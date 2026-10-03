package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.service.api.AvroFileService;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;

/**
 * Optimized for sequential paging (Prev/Next):
 * - keeps a single open DataFileReader session for current file identity + pageSize
 * - caches last N pages (LRU) to make Prev instant and reduce repeated reads
 */
@RequiredArgsConstructor
public class AvroFileServiceImpl implements AvroFileService {

    private static final int DEFAULT_PAGE_CACHE_SIZE = 4;

    private final FilterPredicateFactory predicateFactory;

    private final Object lock = new Object();

    // small LRU cache of pages
    private final LruCache<PageKey, Page> pageCache = new LruCache<>(DEFAULT_PAGE_CACHE_SIZE);

    // open reading session for sequential Next
    private Session session;
    private FileIdentity currentIdentity;

    @Override
    public Page readPage(Path file, int pageIndex, int pageSize) throws IOException {
        Objects.requireNonNull(file, "file");
        if (pageIndex < 0) throw new IllegalArgumentException("pageIndex must be >= 0");
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");

        FileIdentity id = FileIdentity.of(file);
        PageKey key = new PageKey(id, pageIndex, pageSize);

        synchronized (lock) {
            if (!id.equals(currentIdentity)) {
                pageCache.clear();
                closeSessionUnsafe();
                currentIdentity = id;
            }

            Page cached = pageCache.get(key);
            if (cached != null) {
                return cached;
            }

            // Ensure we have a session for this file identity + pageSize
            ensureSession(id, pageSize);

            // Fast path: sequential read
            if (session.nextPageIndex == pageIndex) {
                Page page = readNextPageFromSession(pageIndex, pageSize);
                pageCache.put(key, page);
                return page;
            }

            // Jump path: re-position by reopening and skipping, then keep session open at the end of requested page
            repositionSessionToPage(id, pageIndex, pageSize);
            Page page = readNextPageFromSession(pageIndex, pageSize);
            pageCache.put(key, page);
            return page;
        }
    }

    @Override
    public void invalidate() {
        synchronized (lock) {
            pageCache.clear();
            closeSessionUnsafe();
            currentIdentity = null;
        }
    }

    // -------------------- internals --------------------

    @Override
    public SearchResult search(Path file, List<FilterGroup> groups, int maxResults) throws Exception {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (maxResults <= 0) throw new IllegalArgumentException("maxResults must be > 0");

        var predicate = predicateFactory.compile(groups);

        List<Map<String, Object>> out = new ArrayList<>(Math.min(maxResults, 1024));
        long scanned = 0;
        boolean truncated = false;

        // Search is its own flow; do not reuse paging session
        try (DataFileReader<GenericRecord> reader = open(file)) {
            Schema schema = reader.getSchema();

            GenericRecord rec = null;
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Search cancelled by user");
                }

                rec = reader.next(rec);
                scanned++;

                if (predicate.test(rec)) {
                    // Normalize only matching records to decouple from Avro's reused buffer
                    @SuppressWarnings("unchecked")
                    Map<String, Object> normalized = (Map<String, Object>) com.dkostin.avro_viewer.app.util.AvroNormalizer.normalize(rec, schema);
                    out.add(normalized);

                    if (out.size() >= maxResults) {
                        truncated = true;
                        break;
                    }
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Search cancelled by user");
            }

            return new SearchResult(schema, out, truncated, scanned);
        } catch (java.nio.channels.ClosedByInterruptException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Search cancelled by user");
        }
    }

    private void ensureSession(FileIdentity id, int pageSize) throws IOException {
        if (session == null) {
            session = Session.open(id, pageSize);
            return;
        }
        if (!session.isCompatible(id, pageSize)) {
            closeSessionUnsafe();
            session = Session.open(id, pageSize);
        }
    }

    private void repositionSessionToPage(FileIdentity id, int targetPageIndex, int pageSize) throws IOException {
        closeSessionUnsafe();
        session = Session.open(id, pageSize);

        long startRecord = (long) targetPageIndex * pageSize;
        long skipped = 0;
        try {
            while (skipped < startRecord && session.reader.hasNext()) {
                session.reader.next();
                skipped++;
            }
            session.nextPageIndex = targetPageIndex;
        } catch (Exception e) {
            closeSessionUnsafe();
            throw e;
        }
    }

    private Page readNextPageFromSession(int pageIndex, int pageSize) {
        // invariant: session.nextPageIndex == pageIndex
        List<GenericRecord> out = new ArrayList<>(pageSize);
        int read = 0;
        while (read < pageSize && session.reader.hasNext()) {
            out.add(session.reader.next());
            read++;
        }
        boolean hasNext = session.reader.hasNext();

        session.nextPageIndex = pageIndex + 1;
        session.hasNext = hasNext;

        return new Page(session.schema, out, hasNext);
    }

    private void closeSessionUnsafe() {
        if (session != null) {
            try {
                session.reader.close();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }

    private DataFileReader<GenericRecord> open(Path file) throws IOException {
        SeekableFileInput input = new SeekableFileInput(file.toFile());
        try {
            return new DataFileReader<>(input, new GenericDatumReader<>());
        } catch (IOException e) {
            input.close();
            throw e;
        }
    }

    record FileIdentity(Path path, Object fileKey, long size, FileTime lastModified) {
        static FileIdentity of(Path file) throws IOException {
            BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileIdentity(file.toAbsolutePath().normalize(), a.fileKey(), a.size(), a.lastModifiedTime());
        }
    }

    private record PageKey(FileIdentity identity, int pageIndex, int pageSize) {
    }

    private static final class Session {
        final FileIdentity identity;
        final int pageSize;
        final DataFileReader<GenericRecord> reader;
        final Schema schema;

        int nextPageIndex;
        boolean hasNext;

        private Session(FileIdentity identity,
                        int pageSize,
                        DataFileReader<GenericRecord> reader,
                        Schema schema) {
            this.identity = identity;
            this.pageSize = pageSize;
            this.reader = reader;
            this.schema = schema;
            this.nextPageIndex = 0;
            this.hasNext = true;
        }

        static Session open(FileIdentity id, int pageSize) throws IOException {
            SeekableFileInput input = new SeekableFileInput(id.path().toFile());
            DataFileReader<GenericRecord> r;
            try {
                r = new DataFileReader<>(input, new GenericDatumReader<>());
            } catch (IOException e) {
                input.close();
                throw e;
            }
            return new Session(id, pageSize, r, r.getSchema());
        }

        boolean isCompatible(FileIdentity id, int pageSize) {
            return this.identity.equals(id) && this.pageSize == pageSize;
        }
    }

    private static final class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        LruCache(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = Math.max(1, maxSize);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}

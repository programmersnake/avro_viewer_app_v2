package com.dkostin.avro_viewer.app.data;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.Page;
import com.dkostin.avro_viewer.app.domain.filter.FilterCriterion;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Optimized for sequential paging (Prev/Next):
 * - keeps a single open DataFileReader session for current file+pageSize
 * - caches last N pages (LRU) to make Prev instant and reduce repeated reads
 * <p>
 * Notes:
 * - Not designed for heavy multi-thread concurrent reads. Controller should not call readPage concurrently.
 * - search() intentionally opens its own reader (separate flow).
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

    @Override
    public Page readPage(Path file, int pageIndex, int pageSize) throws IOException {
        Objects.requireNonNull(file, "file");
        if (pageIndex < 0) throw new IllegalArgumentException("pageIndex must be >= 0");
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");

        long lastModified = safeLastModifiedMillis(file);
        PageKey key = new PageKey(file.normalize(), lastModified, pageIndex, pageSize);

        synchronized (lock) {
            Page cached = pageCache.get(key);
            if (cached != null) {
                // Update session to follow cached page if it matches current file/pageSize
                // (optional, but improves "jump then Next" if cache hit)
                ensureSessionAtEndOfPage(file, lastModified, pageIndex, pageSize);
                return cached;
            }

            // Ensure we have a session for this file + pageSize + lastModified
            ensureSession(file, lastModified, pageSize);

            // Fast path: sequential read
            if (session.nextPageIndex == pageIndex) {
                Page page = readNextPageFromSession(pageIndex, pageSize);
                pageCache.put(key, page);
                return page;
            }

            // Jump path: re-position by reopening and skipping, then keep session open at the end of requested page
            repositionSessionToPage(file, lastModified, pageIndex, pageSize);
            Page page = readNextPageFromSession(pageIndex, pageSize); // after reposition, nextPageIndex == pageIndex
            pageCache.put(key, page);
            return page;
        }
    }

    @Override
    public SearchResult search(Path file, List<FilterCriterion> criteria, int maxResults) throws Exception {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (maxResults <= 0) throw new IllegalArgumentException("maxResults must be > 0");

        var predicate = predicateFactory.compile(criteria);

        List<GenericRecord> out = new ArrayList<>(Math.min(maxResults, 1024));
        long scanned = 0;
        boolean truncated = false;

        // Search is its own flow; do not reuse paging session (keeps logic simpler & safe)
        try (DataFileReader<GenericRecord> reader = open(file)) {
            Schema schema = reader.getSchema();

            while (reader.hasNext()) {
                GenericRecord rec = reader.next();
                scanned++;

                if (predicate.test(rec)) {
                    out.add(rec);

                    if (out.size() >= maxResults) {
                        truncated = true;
                        break;
                    }
                }
            }

            return new SearchResult(schema, out, truncated, scanned);
        }
    }

    // -------------------- internals --------------------

    private void ensureSession(Path file, long lastModified, int pageSize) throws IOException {
        if (session == null) {
            session = Session.open(file, lastModified, pageSize);
            return;
        }
        if (!session.isCompatible(file, lastModified, pageSize)) {
            closeSessionUnsafe();
            session = Session.open(file, lastModified, pageSize);
        }
    }

    /**
     * If we returned a cached page, we might want to align session to "pageIndex+1"
     * so that Next is fast (best-effort).
     */
    private void ensureSessionAtEndOfPage(Path file, long lastModified, int pageIndex, int pageSize) {
        if (session == null) {
            return;
        }
        if (!session.isCompatible(file, lastModified, pageSize)) {
            return;
        }

        // If session is already after this page, keep it.
        if (session.nextPageIndex >= pageIndex + 1) {
            return;
        }

        // We won't do expensive reposition here; cached read should stay cheap.
        // Next call will reposition if needed.
    }

    private void repositionSessionToPage(Path file, long lastModified, int targetPageIndex, int pageSize) throws IOException {
        // Reopen reader and skip to startRecord = targetPageIndex * pageSize
        closeSessionUnsafe();
        session = Session.open(file, lastModified, pageSize);

        long startRecord = (long) targetPageIndex * pageSize;
        long skipped = 0;
        while (skipped < startRecord && session.reader.hasNext()) {
            session.reader.next();
            skipped++;
        }
        session.nextPageIndex = targetPageIndex;
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
        return new DataFileReader<>(
                new SeekableFileInput(new File(file.toString())),
                new GenericDatumReader<>()
        );
    }

    private static long safeLastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private record PageKey(Path file, long lastModified, int pageIndex, int pageSize) {
    }

    private static final class Session {
        final Path file;
        final long lastModified;
        final int pageSize;
        final DataFileReader<GenericRecord> reader;
        final Schema schema;

        int nextPageIndex; // page index that can be read next without reopen/skip
        boolean hasNext;

        private Session(Path file,
                        long lastModified,
                        int pageSize,
                        DataFileReader<GenericRecord> reader,
                        Schema schema) {
            this.file = file;
            this.lastModified = lastModified;
            this.pageSize = pageSize;
            this.reader = reader;
            this.schema = schema;
            this.nextPageIndex = 0;
            this.hasNext = true;
        }

        static Session open(Path file, long lastModified, int pageSize) throws IOException {
            DataFileReader<GenericRecord> r = new DataFileReader<>(
                    new SeekableFileInput(file.toFile()),
                    new GenericDatumReader<>()
            );
            return new Session(file.normalize(), lastModified, pageSize, r, r.getSchema());
        }

        boolean isCompatible(Path file, long lastModified, int pageSize) {
            return this.file.equals(file.normalize())
                    && this.lastModified == lastModified
                    && this.pageSize == pageSize;
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

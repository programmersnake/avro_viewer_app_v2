package com.dkostin.avro_viewer.app.service.impl;

import com.dkostin.avro_viewer.app.config.FilterPredicateFactory;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchControl;
import com.dkostin.avro_viewer.app.domain.model.SearchProgress;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.StopReason;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.AvroFileInfo;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.service.api.AvroFileService;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileConstants;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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

    // cached block index for fast seeks and row counts
    private BlockIndex currentBlockIndex;
    private FileIdentity cachedBlockIndexIdentity;

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
                if (!id.equals(cachedBlockIndexIdentity)) {
                    currentBlockIndex = null;
                    cachedBlockIndexIdentity = null;
                }
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
    public AvroFileInfo readFileInfo(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path absPath = file.toAbsolutePath().normalize();
        BasicFileAttributes attrs = Files.readAttributes(absPath, BasicFileAttributes.class);

        SeekableFileInput input = new SeekableFileInput(absPath.toFile());
        try (DataFileReader<GenericRecord> reader = new DataFileReader<>(input, new GenericDatumReader<>())) {
            Schema schema = reader.getSchema();
            String codec = reader.getMetaString(DataFileConstants.CODEC);
            if (codec == null || codec.isBlank()) {
                codec = DataFileConstants.NULL_CODEC;
            }

            Map<String, String> metadata = new LinkedHashMap<>();
            for (String key : reader.getMetaKeys()) {
                byte[] raw = reader.getMeta(key);
                metadata.put(key, formatMetaValue(raw));
            }

            return new AvroFileInfo(
                    absPath,
                    attrs.size(),
                    attrs.lastModifiedTime(),
                    codec,
                    schema,
                    Collections.unmodifiableMap(metadata)
            );
        }
    }

    @Override
    public long countRecords(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        FileIdentity id = FileIdentity.of(file);
        synchronized (lock) {
            if (currentBlockIndex != null && id.equals(cachedBlockIndexIdentity)) {
                return currentBlockIndex.totalRecords();
            }
        }

        BlockIndex index = AvroBlockScanner.scan(file);
        synchronized (lock) {
            cachedBlockIndexIdentity = id;
            currentBlockIndex = index;
            return index.totalRecords();
        }
    }

    @Override
    public OptionalLong knownRecordCount(Path file) {
        if (file == null) {
            return OptionalLong.empty();
        }
        try {
            FileIdentity id = FileIdentity.of(file);
            synchronized (lock) {
                if (currentBlockIndex != null && id.equals(cachedBlockIndexIdentity)) {
                    return OptionalLong.of(currentBlockIndex.totalRecords());
                }
            }
        } catch (Exception ignored) {
        }
        return OptionalLong.empty();
    }

    @Override
    public void invalidate() {
        synchronized (lock) {
            pageCache.clear();
            closeSessionUnsafe();
            currentIdentity = null;
            currentBlockIndex = null;
            cachedBlockIndexIdentity = null;
        }
    }

    // -------------------- internals --------------------

    @Override
    public SearchResult search(Path file, List<FilterGroup> groups, int maxResults, SearchControl control) throws Exception {
        if (file == null) throw new IllegalArgumentException("file is null");
        if (maxResults <= 0) throw new IllegalArgumentException("maxResults must be > 0");
        SearchControl ctrl = control != null ? control : SearchControl.noop();

        var predicate = predicateFactory.compile(groups);

        List<Map<String, Object>> out = new ArrayList<>(Math.min(maxResults, 1024));
        long scanned = 0;
        StopReason stopReason = StopReason.COMPLETED;

        long fileSize = 0;
        try {
            fileSize = Files.size(file);
        } catch (Exception ignored) {
        }

        // Search is its own flow; do not reuse paging session
        try (DataFileReader<GenericRecord> reader = open(file)) {
            Schema schema = reader.getSchema();

            GenericRecord rec = null;
            long lastReported = 0;
            double fraction = 0.0;

            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Search cancelled by user");
                }
                if (ctrl.isStopRequested()) {
                    stopReason = StopReason.USER_STOPPED;
                    break;
                }

                rec = reader.next(rec);
                scanned++;

                if (predicate.test(rec)) {
                    // Normalize only matching records to decouple from Avro's reused buffer
                    @SuppressWarnings("unchecked")
                    Map<String, Object> normalized = (Map<String, Object>) com.dkostin.avro_viewer.app.util.AvroNormalizer.normalize(rec, schema);
                    out.add(normalized);

                    if (out.size() >= maxResults) {
                        stopReason = StopReason.MAX_RESULTS;
                        break;
                    }
                }

                if (scanned - lastReported >= 1000) {
                    if (fileSize > 0) {
                        try {
                            fraction = Math.min(1.0, Math.max(0.0, (double) reader.tell() / fileSize));
                        } catch (Exception ignored) {
                        }
                    }
                    ctrl.reportProgress(new SearchProgress(scanned, out.size(), fraction));
                    lastReported = scanned;
                }
            }

            if (stopReason == StopReason.COMPLETED) {
                fraction = 1.0;
            } else if (fileSize > 0) {
                try {
                    fraction = Math.min(1.0, Math.max(0.0, (double) reader.tell() / fileSize));
                } catch (Exception ignored) {
                }
            }
            ctrl.reportProgress(new SearchProgress(scanned, out.size(), fraction));

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Search cancelled by user");
            }

            return new SearchResult(schema, out, stopReason, scanned, fraction);
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
        try {
            BlockIndex index = (currentBlockIndex != null && id.equals(cachedBlockIndexIdentity))
                    ? currentBlockIndex
                    : null;

            if (index != null && index.totalRecords() > 0 && startRecord < index.totalRecords()) {
                int blockIdx = index.blockContaining(startRecord);
                long blockOffset = index.blockOffsets()[blockIdx];
                long blockStartRecord = index.firstRecordIndex()[blockIdx];

                session.reader.seek(blockOffset);
                long toSkip = startRecord - blockStartRecord;
                for (long i = 0; i < toSkip && session.reader.hasNext(); i++) {
                    session.reader.next();
                }
            } else {
                long skipped = 0;
                while (skipped < startRecord && session.reader.hasNext()) {
                    session.reader.next();
                    skipped++;
                }
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

    static String formatMetaValue(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(raw));
            String str = chars.toString();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                    return "<" + raw.length + " bytes, binary>";
                }
            }
            return str;
        } catch (CharacterCodingException e) {
            return "<" + raw.length + " bytes, binary>";
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

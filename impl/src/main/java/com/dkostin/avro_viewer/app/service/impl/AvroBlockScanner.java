package com.dkostin.avro_viewer.app.service.impl;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

/**
 * Fast, raw block scanner for Avro container files.
 * Traverses block headers directly using {@link FileChannel} without payload decompression,
 * counting total records and mapping block offsets for fast random seeks.
 */
class AvroBlockScanner {

    private static final int SYNC_SIZE = 16;
    private static final int HEADER_READ_AHEAD = 32;

    private AvroBlockScanner() {
    }

    /**
     * Scans the given Avro file to build a {@link BlockIndex}.
     *
     * @param file path to the Avro file
     * @return index containing total record count and block offsets
     * @throws InterruptedIOException if scanning was cancelled by interrupting the current thread
     * @throws IOException            if the file is missing or corrupted
     */
    static BlockIndex scan(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            throw new NoSuchFileException(file.toString());
        }

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Block scanning interrupted");
        }

        long firstBlockOffset;
        try {
            try (SeekableFileInput input = new SeekableFileInput(file.toFile());
                 DataFileReader<GenericRecord> reader = new DataFileReader<>(input, new GenericDatumReader<>())) {
                firstBlockOffset = reader.previousSync();
            }
        } catch (java.nio.channels.ClosedByInterruptException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Block scanning interrupted");
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (firstBlockOffset > fileSize) {
                throw new IOException("Invalid Avro file: header sync position past end of file");
            }
            if (firstBlockOffset < SYNC_SIZE) {
                throw new IOException("Invalid Avro file: header too short");
            }

            // Sync marker is the 16 bytes immediately preceding the first block
            channel.position(firstBlockOffset - SYNC_SIZE);
            ByteBuffer syncBuf = ByteBuffer.allocate(SYNC_SIZE);
            readFully(channel, syncBuf);
            byte[] syncMarker = syncBuf.array();

            LongList blockOffsets = new LongList();
            LongList firstRecordIndices = new LongList();
            long totalRecords = 0;

            channel.position(firstBlockOffset);

            ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_READ_AHEAD);
            ByteBuffer verifySyncBuf = ByteBuffer.allocate(SYNC_SIZE);

            while (channel.position() < fileSize) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Block scanning interrupted");
                }

                long blockOffset = channel.position();
                long remainingBytes = fileSize - blockOffset;
                if (remainingBytes < SYNC_SIZE + 2) {
                    throw new IOException("Truncated block at offset " + blockOffset + ": only " + remainingBytes + " bytes remain");
                }

                headerBuf.clear();
                int toRead = (int) Math.min(HEADER_READ_AHEAD, remainingBytes);
                headerBuf.limit(toRead);
                readFully(channel, headerBuf);
                headerBuf.flip();

                long count;
                long size;
                try {
                    count = readZigzagLong(headerBuf);
                    size = readZigzagLong(headerBuf);
                } catch (EOFException e) {
                    throw new IOException("Truncated block header at offset " + blockOffset, e);
                }

                if (count <= 0) {
                    throw new IOException("Invalid block count " + count + " at offset " + blockOffset);
                }
                if (size < 0) {
                    throw new IOException("Invalid block size " + size + " at offset " + blockOffset);
                }

                int headerBytesConsumed = headerBuf.position();
                long syncOffset = blockOffset + headerBytesConsumed + size;
                long nextBlockOffset = syncOffset + SYNC_SIZE;

                if (nextBlockOffset > fileSize) {
                    throw new IOException("Truncated block payload at offset " + blockOffset + ": expected at least "
                            + (headerBytesConsumed + size + SYNC_SIZE) + " bytes but only " + remainingBytes + " remain");
                }

                // Verify sync marker after block payload
                channel.position(syncOffset);
                verifySyncBuf.clear();
                readFully(channel, verifySyncBuf);
                if (!Arrays.equals(verifySyncBuf.array(), syncMarker)) {
                    throw new IOException("Corrupted block at offset " + blockOffset + ": sync marker mismatch");
                }

                blockOffsets.add(blockOffset);
                firstRecordIndices.add(totalRecords);
                totalRecords += count;

                channel.position(nextBlockOffset);
            }

            return new BlockIndex(totalRecords, blockOffsets.toArray(), firstRecordIndices.toArray());
        } catch (java.nio.channels.ClosedByInterruptException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Block scanning interrupted");
        }
    }

    static long readZigzagLong(ByteBuffer buf) throws IOException {
        long value = 0;
        int shift = 0;
        while (true) {
            if (!buf.hasRemaining()) {
                throw new EOFException("Unexpected EOF while reading varint");
            }
            byte b = buf.get();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            if (shift >= 70) {
                throw new IOException("Corrupted varint: too many bytes");
            }
        }
        return (value >>> 1) ^ -(value & 1);
    }

    private static void readFully(FileChannel channel, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int read = channel.read(buf);
            if (read < 0) {
                throw new EOFException("Unexpected EOF reading from channel");
            }
        }
    }

    private static final class LongList {
        private long[] data = new long[128];
        private int size = 0;

        void add(long val) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = val;
        }

        long[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}

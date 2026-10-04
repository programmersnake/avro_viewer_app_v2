package com.dkostin.avro_viewer.app.service.impl;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable block index mapping record indices to block offsets in an Avro container file.
 */
public record BlockIndex(long totalRecords, long[] blockOffsets, long[] firstRecordIndex) {

    public BlockIndex {
        Objects.requireNonNull(blockOffsets, "blockOffsets");
        Objects.requireNonNull(firstRecordIndex, "firstRecordIndex");
        if (blockOffsets.length != firstRecordIndex.length) {
            throw new IllegalArgumentException("blockOffsets and firstRecordIndex lengths must match");
        }
    }

    /**
     * Binary search to find the block index containing the given record index.
     *
     * @param recordIndex zero-based record index
     * @return zero-based block index
     * @throws IndexOutOfBoundsException if recordIndex < 0 or >= totalRecords
     */
    public int blockContaining(long recordIndex) {
        if (recordIndex < 0 || recordIndex >= totalRecords) {
            throw new IndexOutOfBoundsException("Record index out of range: " + recordIndex + " (total: " + totalRecords + ")");
        }
        int idx = Arrays.binarySearch(firstRecordIndex, recordIndex);
        if (idx >= 0) {
            return idx;
        }
        return -idx - 2;
    }
}

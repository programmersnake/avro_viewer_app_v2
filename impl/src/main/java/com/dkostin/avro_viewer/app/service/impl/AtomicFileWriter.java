package com.dkostin.avro_viewer.app.service.impl;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility to safely write files by writing to a temporary file in the same directory
 * and atomically moving the completed file to the destination.
 * On interruption or error, the temporary file is deleted and the destination remains untouched.
 */
public final class AtomicFileWriter {

    @FunctionalInterface
    public interface Body {
        void writeTo(Path tmp) throws IOException;
    }

    private AtomicFileWriter() {}

    public static void write(Path target, Body body) throws IOException {
        Path abs = target.toAbsolutePath();
        Path parent = abs.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent, "." + abs.getFileName() + ".", ".part");
        try {
            body.writeTo(tmp);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Export cancelled by user");
            }
            try {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

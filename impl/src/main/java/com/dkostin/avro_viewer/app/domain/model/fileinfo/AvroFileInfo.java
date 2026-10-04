package com.dkostin.avro_viewer.app.domain.model.fileinfo;

import com.dkostin.avro_viewer.app.util.PresentationFormatter;
import org.apache.avro.Schema;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

/**
 * Immutable snapshot of Avro file attributes, header metadata, compression codec, and schema.
 */
public record AvroFileInfo(
        Path path,
        long sizeBytes,
        FileTime lastModified,
        String codec,
        Schema schema,
        Map<String, String> metadata
) {
    public AvroFileInfo {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String fileName() {
        return path != null && path.getFileName() != null ? path.getFileName().toString() : "";
    }

    public String formattedSize() {
        return PresentationFormatter.formatFileSize(sizeBytes);
    }
}

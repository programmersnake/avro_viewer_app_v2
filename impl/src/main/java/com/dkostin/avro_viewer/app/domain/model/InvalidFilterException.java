package com.dkostin.avro_viewer.app.domain.model;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when one or more filter paths are not present in the Avro schema.
 */
@Getter
public class InvalidFilterException extends IllegalArgumentException {
    private final List<String> invalidPaths;

    public InvalidFilterException(List<String> invalidPaths) {
        super("Unknown field(s): " + String.join(", ", invalidPaths));
        this.invalidPaths = List.copyOf(invalidPaths);
    }

}

package com.dkostin.avro_viewer.app.service.api;

import java.io.IOException;

/**
 * Factory for creating fresh instances of {@link RecordProvider} to allow multi-pass scans.
 */
@FunctionalInterface
public interface RecordProviderFactory {
    /**
     * Creates a new, independent {@link RecordProvider} instance.
     * @return RecordProvider.
     * @throws IOException if the provider fails to open.
     */
    RecordProvider create() throws IOException;
}

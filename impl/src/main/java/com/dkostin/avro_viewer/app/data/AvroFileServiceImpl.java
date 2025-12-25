package com.dkostin.avro_viewer.app.data;

import com.dkostin.avro_viewer.app.common.Page;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableFileInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AvroFileServiceImpl implements AvroFileService { // todo TODO.md {1} {2}

    @Override
    public Schema readSchema(Path file) throws IOException {
        try (DataFileReader<GenericRecord> r = open(file)) {
            return r.getSchema();
        }
    }

    @Override
    public Page readPage(Path file, int pageIndex, int pageSize) throws IOException {
        long start = (long) pageIndex * pageSize;

        try (DataFileReader<GenericRecord> r = open(file)) {
            Schema schema = r.getSchema();

            // skip to start
            long skipped = 0;
            while (skipped < start && r.hasNext()) {
                r.next();
                skipped++;
            }

            // read page
            List<GenericRecord> out = new ArrayList<>(pageSize);
            int read = 0;
            while (read < pageSize && r.hasNext()) {
                out.add(r.next());
                read++;
            }

            return new Page(schema, out, start + read);
        }
    }

    private DataFileReader<GenericRecord> open(Path file) throws IOException {
        return new DataFileReader<>(
                new SeekableFileInput(new File(file.toString())),
                new GenericDatumReader<>()
        );
    }
}

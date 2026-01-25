package com.dkostin.avro_viewer.app.domain.model;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.List;

public record Page(Schema schema, List<GenericRecord> records, boolean hasNext) {
}

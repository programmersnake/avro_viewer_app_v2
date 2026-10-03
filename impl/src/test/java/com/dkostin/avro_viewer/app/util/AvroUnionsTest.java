package com.dkostin.avro_viewer.app.util;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AvroUnionsTest {

    @Test
    void testResolvePrimitiveUnionBranch() {
        Schema unionSchema = Schema.createUnion(
                Schema.create(Schema.Type.NULL),
                Schema.create(Schema.Type.STRING),
                Schema.create(Schema.Type.LONG)
        );

        Schema resolvedLong = AvroUnions.resolveBranch(unionSchema, 42L);
        assertEquals(Schema.Type.LONG, resolvedLong.getType());

        Schema resolvedString = AvroUnions.resolveBranch(unionSchema, "hello");
        assertEquals(Schema.Type.STRING, resolvedString.getType());

        Schema resolvedNull = AvroUnions.resolveBranch(unionSchema, null);
        assertEquals(Schema.Type.NULL, resolvedNull.getType());
    }

    @Test
    void testResolveMultiRecordUnionBranch() {
        Schema recA = SchemaBuilder.record("RecA").fields().requiredString("aField").endRecord();
        Schema recB = SchemaBuilder.record("RecB").fields().requiredString("bField").endRecord();
        Schema unionSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), recA, recB);

        GenericRecord recBInstance = new GenericData.Record(recB);
        recBInstance.put("bField", "valB");

        Schema resolved = AvroUnions.resolveBranch(unionSchema, recBInstance);
        assertEquals("RecB", resolved.getName());
    }

    @Test
    void testResolveDecimalBytesUnionBranch() {
        Schema decimalBytes = LogicalTypes.decimal(9, 2).addToSchema(Schema.create(Schema.Type.BYTES));
        Schema unionSchema = Schema.createUnion(
                Schema.create(Schema.Type.NULL),
                Schema.create(Schema.Type.STRING),
                decimalBytes
        );

        ByteBuffer buf = ByteBuffer.wrap(new byte[]{1, 2});
        Schema resolved = AvroUnions.resolveBranch(unionSchema, buf);
        assertEquals(Schema.Type.BYTES, resolved.getType());
        assertInstanceOf(LogicalTypes.Decimal.class, resolved.getLogicalType());
    }

    @Test
    void testFallbackForNonAvroValue() {
        Schema recA = SchemaBuilder.record("RecA").fields().requiredString("aField").endRecord();
        Schema recB = SchemaBuilder.record("RecB").fields().requiredString("bField").endRecord();
        Schema unionSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), recA, recB);

        // A plain Map has no Avro schema identity; should fall back to first non-null branch
        Schema resolved = AvroUnions.resolveBranch(unionSchema, Map.of("bField", "valB"));
        assertEquals("RecA", resolved.getName());
    }

    @Test
    void testNonNullBranches() {
        Schema unionSchema = Schema.createUnion(
                Schema.create(Schema.Type.NULL),
                Schema.create(Schema.Type.INT),
                Schema.create(Schema.Type.STRING)
        );

        List<Schema> branches = AvroUnions.nonNullBranches(unionSchema);
        assertEquals(2, branches.size());
        assertEquals(Schema.Type.INT, branches.get(0).getType());
        assertEquals(Schema.Type.STRING, branches.get(1).getType());

        Schema single = Schema.create(Schema.Type.BOOLEAN);
        assertEquals(List.of(single), AvroUnions.nonNullBranches(single));
    }
}

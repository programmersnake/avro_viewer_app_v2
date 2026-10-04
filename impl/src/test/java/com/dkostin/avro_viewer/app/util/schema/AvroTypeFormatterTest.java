package com.dkostin.avro_viewer.app.util.schema;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvroTypeFormatterTest {

    @Test
    void testFormatPrimitivesAndNullable() {
        assertEquals("string", AvroTypeFormatter.format(Schema.create(Schema.Type.STRING)));
        assertEquals("int", AvroTypeFormatter.format(Schema.create(Schema.Type.INT)));

        Schema nullableString = Schema.createUnion(
                Schema.create(Schema.Type.NULL),
                Schema.create(Schema.Type.STRING)
        );
        assertEquals("string?", AvroTypeFormatter.format(nullableString));
    }

    @Test
    void testFormatLogicalTypes() {
        Schema timestampSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        assertEquals("long · timestamp-millis", AvroTypeFormatter.format(timestampSchema));

        Schema decimalSchema = LogicalTypes.decimal(10, 2).addToSchema(Schema.create(Schema.Type.BYTES));
        assertEquals("decimal(10,2)", AvroTypeFormatter.format(decimalSchema));

        Schema nullableDecimal = Schema.createUnion(
                Schema.create(Schema.Type.NULL),
                decimalSchema
        );
        assertEquals("decimal(10,2)?", AvroTypeFormatter.format(nullableDecimal));
    }

    @Test
    void testFormatContainers() {
        Schema itemRec = SchemaBuilder.record("Item").fields().requiredString("sku").endRecord();
        Schema arraySchema = Schema.createArray(itemRec);
        assertEquals("array<Item>", AvroTypeFormatter.format(arraySchema));

        Schema mapSchema = Schema.createMap(Schema.create(Schema.Type.STRING));
        assertEquals("map<string>", AvroTypeFormatter.format(mapSchema));
    }

    @Test
    void testFormatEnumAndFixed() {
        Schema enumSchema = SchemaBuilder.enumeration("Color").symbols("RED", "GREEN", "BLUE");
        assertEquals("enum Color", AvroTypeFormatter.format(enumSchema));

        Schema fixedSchema = Schema.createFixed("Hash", null, null, 16);
        assertEquals("fixed(16)", AvroTypeFormatter.format(fixedSchema));
    }

    @Test
    void testFormatMultiBranchUnion() {
        Schema recA = SchemaBuilder.record("RecA").fields().requiredString("a").endRecord();
        Schema recB = SchemaBuilder.record("RecB").fields().requiredString("b").endRecord();

        Schema unionWithoutNull = Schema.createUnion(recA, recB);
        assertEquals("union<RecA | RecB>", AvroTypeFormatter.format(unionWithoutNull));

        Schema unionWithNull = Schema.createUnion(List.of(
                Schema.create(Schema.Type.NULL),
                recA,
                recB
        ));
        assertEquals("union<RecA | RecB>?", AvroTypeFormatter.format(unionWithNull));
    }
}

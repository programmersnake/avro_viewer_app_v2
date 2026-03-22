package com.dkostin.avro_viewer.app.util;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AvroNormalizerTest {

    @Test
    @SuppressWarnings("unchecked")
    void normalizeGenericRecordWithDecimalBytes() {
        // Decimal Schema (Logical Type)
        Schema decimalSchema = Schema.create(Schema.Type.BYTES);
        LogicalTypes.decimal(10, 4).addToSchema(decimalSchema);

        // Record Schema containing the Decimal
        Schema schema = SchemaBuilder.record("TestRecord")
                .fields()
                .name("salary").type(decimalSchema).noDefault()
                .name("bonus").type(decimalSchema).noDefault()
                .endRecord();

        // Base64 equivalent for testing
        // For value 1234.5678, we need its bytes
        // scale=4 means value = 1234.5678 * 10^4 = 12345678
        // 12345678 in hex is BC614E, which is 3 bytes: [-68, 97, 78]
        byte[] salaryBytes = new java.math.BigInteger("12345678").toByteArray();
        byte[] bonusBytes = new java.math.BigInteger("500000").toByteArray(); // 50.0000

        GenericRecord record = new GenericData.Record(schema);
        record.put("salary", salaryBytes);
        record.put("bonus", java.nio.ByteBuffer.wrap(bonusBytes));

        // Attempt Normalization
        Object normalized = AvroNormalizer.normalize(record, schema);

        // Assertions
        assertInstanceOf(Map.class, normalized, "Normalized GenericRecord should be a Map");
        Map<String, Object> map = (Map<String, Object>) normalized;

        assertEquals(new BigDecimal("1234.5678"), map.get("salary"));
        assertEquals(new BigDecimal("1234.5678"), map.get("salary"));
        assertEquals(new BigDecimal("50.0000"), map.get("bonus"));
    }

    @Test
    void testNormalizeCollectionsAndPrimitives() {
        assertEquals("test", AvroNormalizer.normalize("test", null));
        assertEquals(123, AvroNormalizer.normalize(123, null));

        Map<String, Object> inputMap = Map.of("key", "value");
        Object mapOut = AvroNormalizer.normalize(inputMap, null);
        assertInstanceOf(Map.class, mapOut);
        assertEquals("value", ((Map<?, ?>) mapOut).get("key"));

        java.util.List<String> list = java.util.Arrays.asList("A", "B");
        Object listOut = AvroNormalizer.normalize(list, null);
        assertInstanceOf(java.util.Collection.class, listOut);
        assertEquals(2, ((java.util.Collection<?>) listOut).size());
    }
}

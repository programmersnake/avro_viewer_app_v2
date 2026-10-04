package com.dkostin.avro_viewer.app.util.schema;

import com.dkostin.avro_viewer.app.domain.model.fileinfo.FieldRule;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.NodeKind;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.SchemaNode;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaCatalogTest {

    @Test
    void testNestedRecordsAndContainers() {
        Schema addressSchema = SchemaBuilder.record("Address").fields()
                .requiredString("city")
                .optionalString("zip")
                .endRecord();

        Schema itemSchema = SchemaBuilder.record("Item").fields()
                .requiredString("sku")
                .requiredDouble("price")
                .endRecord();

        Schema root = SchemaBuilder.record("Order").fields()
                .requiredString("orderId")
                .name("address").type(addressSchema).noDefault()
                .name("items").type().array().items(itemSchema).noDefault()
                .name("tags").type().map().values().stringType().noDefault()
                .endRecord();

        SchemaNode tree = SchemaCatalog.tree(root);
        assertNotNull(tree);
        assertEquals(NodeKind.RECORD, tree.kind());
        assertEquals(4, tree.children().size());

        // orderId
        SchemaNode orderIdNode = tree.children().get(0);
        assertEquals("orderId", orderIdNode.name());
        assertEquals("orderId", orderIdNode.path());
        assertEquals(NodeKind.PRIMITIVE, orderIdNode.kind());
        assertFalse(orderIdNode.nullable());

        // address and nested fields
        SchemaNode addressNode = tree.children().get(1);
        assertEquals("address", addressNode.name());
        assertEquals("address", addressNode.path());
        assertEquals(NodeKind.RECORD, addressNode.kind());
        assertEquals(2, addressNode.children().size());

        SchemaNode cityNode = addressNode.children().get(0);
        assertEquals("city", cityNode.name());
        assertEquals("address.city", cityNode.path());
        assertEquals(2, cityNode.depth());

        SchemaNode zipNode = addressNode.children().get(1);
        assertEquals("zip", zipNode.name());
        assertEquals("address.zip", zipNode.path());
        assertTrue(zipNode.nullable());
        assertEquals("string?", zipNode.typeDisplay());

        // items and nested array items
        SchemaNode itemsNode = tree.children().get(2);
        assertEquals("items", itemsNode.name());
        assertEquals("items", itemsNode.path());
        assertEquals(NodeKind.ARRAY, itemsNode.kind());
        assertEquals(2, itemsNode.children().size());

        SchemaNode skuNode = itemsNode.children().get(0);
        assertEquals("sku", skuNode.name());
        assertEquals("items.sku", skuNode.path());

        // tags (map container)
        SchemaNode tagsNode = tree.children().get(3);
        assertEquals("tags", tagsNode.name());
        assertEquals("tags", tagsNode.path());
        assertEquals(NodeKind.MAP, tagsNode.kind());
        assertTrue(tagsNode.children().isEmpty(), "Map should not have static children since keys are dynamic");

        // Flattened paths
        List<SchemaNode> paths = SchemaCatalog.paths(root);
        List<String> pathStrings = paths.stream().map(SchemaNode::path).toList();
        assertTrue(pathStrings.contains("orderId"));
        assertTrue(pathStrings.contains("address"));
        assertTrue(pathStrings.contains("address.city"));
        assertTrue(pathStrings.contains("address.zip"));
        assertTrue(pathStrings.contains("items"));
        assertTrue(pathStrings.contains("items.sku"));
        assertTrue(pathStrings.contains("items.price"));
        assertTrue(pathStrings.contains("tags"));
    }

    @Test
    void testRecursiveSchemaTerminatesGracefully() {
        // Recursive linked-list schema: Node -> { value: int, next: Node? }
        Schema nodeSchema = Schema.createRecord("Node", null, "com.example", false);
        Schema nullableNode = Schema.createUnion(Schema.create(Schema.Type.NULL), nodeSchema);

        List<Schema.Field> fields = List.of(
                new Schema.Field("value", Schema.create(Schema.Type.INT), null, null),
                new Schema.Field("next", nullableNode, null, null)
        );
        nodeSchema.setFields(fields);

        SchemaNode tree = SchemaCatalog.tree(nodeSchema);
        assertNotNull(tree);
        assertEquals(2, tree.children().size());

        SchemaNode nextNode = tree.children().get(1);
        assertEquals("next", nextNode.name());
        assertTrue(nextNode.recursiveRef(), "Ancestor recursion must be detected and marked on nextNode");
        assertTrue(nextNode.children().isEmpty(), "Recursive ref must not expand infinitely");
    }

    @Test
    void testFieldRulesExtraction() {
        Schema decimalType = LogicalTypes.decimal(12, 4).addToSchema(Schema.create(Schema.Type.BYTES));
        Schema enumType = SchemaBuilder.enumeration("Status").symbols("PENDING", "ACTIVE", "COMPLETED");

        Schema.Field decimalField = new Schema.Field("amount", decimalType, "Transaction amount", null);
        decimalField.addAlias("tx_amount");

        Schema root = SchemaBuilder.record("Transaction").fields()
                .name("status").type(enumType).noDefault()
                .name("amount").doc("Transaction amount").type(decimalType).noDefault()
                .name("note").type().nullable().stringType().stringDefault("N/A")
                .endRecord();

        SchemaNode tree = SchemaCatalog.tree(root);
        assertNotNull(tree);

        // Status field rules
        SchemaNode statusNode = tree.children().get(0);
        List<FieldRule> statusRules = statusNode.rules();
        assertTrue(statusRules.stream().anyMatch(r -> r.label().equals("Enum symbols") && r.value().contains("PENDING")));

        // Amount field rules
        SchemaNode amountNode = tree.children().get(1);
        List<FieldRule> amountRules = amountNode.rules();
        assertTrue(amountRules.stream().anyMatch(r -> r.label().equals("Decimal precision") && r.value().equals("12")));
        assertTrue(amountRules.stream().anyMatch(r -> r.label().equals("Decimal scale") && r.value().equals("4")));
        assertTrue(amountRules.stream().anyMatch(r -> r.label().equals("Documentation") && r.value().equals("Transaction amount")));

        // Note field rules
        SchemaNode noteNode = tree.children().get(2);
        List<FieldRule> noteRules = noteNode.rules();
        assertTrue(noteRules.stream().anyMatch(r -> r.label().equals("Required") && r.value().contains("nullable")));
        assertTrue(noteRules.stream().anyMatch(r -> r.label().equals("Default value") && r.value().equals("N/A")));
    }
}

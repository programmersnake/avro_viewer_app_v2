package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterPredicateFactoryTest {

    private FilterPredicateFactory factory;
    private Schema schema;
    private GenericRecord record;

    @BeforeEach
    void setUp() {
        factory = new FilterPredicateFactory();

        // 1. Build a purely abstract, domain-independent complex schema
        Schema alphaSchema = SchemaBuilder.record("Alpha")
                .fields()
                .requiredDouble("metricA")
                .requiredDouble("metricB")
                .endRecord();

        Schema betaSchema = SchemaBuilder.record("Beta")
                .fields()
                .name("betaDetails").type().record("BetaDetails")
                    .fields().requiredDouble("valDouble").requiredBoolean("flag").endRecord().noDefault()
                .endRecord();

        Schema gammaSchema = SchemaBuilder.record("Gamma")
                .fields()
                .name("alpha").type(alphaSchema).noDefault()
                .name("beta").type(betaSchema).noDefault()
                .endRecord();

        Schema mapSchema = SchemaBuilder.map().values(gammaSchema);

        // Logical Decimal Schema
        Schema decimalSchema = LogicalTypes.decimal(18, 9).addToSchema(
                SchemaBuilder.builder().bytesType()
        );

        Schema nestedContainerSchema = SchemaBuilder.record("NestedContainer")
                .fields()
                .name("dataMap").type(mapSchema).noDefault()
                .name("decimalField").type(decimalSchema).noDefault()
                .requiredLong("timestamp")
                .endRecord();

        Schema listItemSchema = SchemaBuilder.record("ListItem")
                .fields()
                .requiredDouble("value")
                .requiredString("name")
                .endRecord();

        Schema detailsContainerSchema = SchemaBuilder.record("DetailsContainer")
                .fields()
                .name("recordsList").type().array().items(listItemSchema).noDefault()
                .endRecord();

        Schema parentContainerSchema = SchemaBuilder.record("ParentContainer")
                .fields()
                .name("nestedContainer").type(nestedContainerSchema).noDefault()
                .name("detailsContainer").type(detailsContainerSchema).noDefault()
                .endRecord();

        schema = SchemaBuilder.record("RootObject")
                .fields()
                .requiredString("id")
                .name("items").type().array().items().stringType().noDefault()
                .name("subContainer").type(parentContainerSchema).noDefault()
                .requiredString("region")
                .requiredString("category")
                .endRecord();

        // 2. Populate record
        record = new GenericData.Record(schema);
        record.put("id", "9206c8cc-bd06-4cbd-8d44-d56dad47e898");
        record.put("region", "43");
        record.put("category", "1300");

        // items list
        GenericData.Array<String> itemsList = new GenericData.Array<>(
                SchemaBuilder.array().items().stringType(),
                List.of("DE000A0N4RM0", "DE000A2AACC6")
        );
        record.put("items", itemsList);

        // alpha
        GenericRecord alpha = new GenericData.Record(alphaSchema);
        alpha.put("metricA", 0.47432068758609836);
        alpha.put("metricB", 0.47808718268324696);

        // betaDetails
        GenericRecord betaDetails = new GenericData.Record(betaSchema.getField("betaDetails").schema());
        betaDetails.put("valDouble", 0.7688172043010753);
        betaDetails.put("flag", true);

        GenericRecord beta = new GenericData.Record(betaSchema);
        beta.put("betaDetails", betaDetails);

        // gamma
        GenericRecord gamma = new GenericData.Record(gammaSchema);
        gamma.put("alpha", alpha);
        gamma.put("beta", beta);

        // dataMap
        Map<String, GenericRecord> mapData = Map.of("keyABC", gamma);

        // NestedContainer with a logical Decimal representation
        GenericRecord nestedContainer = new GenericData.Record(nestedContainerSchema);
        nestedContainer.put("dataMap", mapData);
        nestedContainer.put("timestamp", 1765839843630L);

        // logical Decimal value: 0.474320687 (scale 9, BigInt unscaled = 474320687)
        byte[] unscaledBytes = new BigDecimal("0.474320687").unscaledValue().toByteArray();
        nestedContainer.put("decimalField", ByteBuffer.wrap(unscaledBytes));

        // recordsList
        GenericRecord listEntry1 = new GenericData.Record(listItemSchema);
        listEntry1.put("value", -0.0791);
        listEntry1.put("name", "itemX");

        GenericRecord listEntry2 = new GenericData.Record(listItemSchema);
        listEntry2.put("value", 0.0035);
        listEntry2.put("name", "itemY");

        GenericData.Array<GenericRecord> recordsList = new GenericData.Array<>(
                detailsContainerSchema.getField("recordsList").schema(),
                List.of(listEntry1, listEntry2)
        );

        GenericRecord detailsContainer = new GenericData.Record(detailsContainerSchema);
        detailsContainer.put("recordsList", recordsList);

        // ParentContainer
        GenericRecord parentContainer = new GenericData.Record(parentContainerSchema);
        parentContainer.put("nestedContainer", nestedContainer);
        parentContainer.put("detailsContainer", detailsContainer);

        record.put("subContainer", parentContainer);
    }

    @Test
    void testRootLevelFieldMatching() {
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ofField("id"),
                MatchOperation.EQUALS,
                "9206c8cc-bd06-4cbd-8d44-d56dad47e898"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));

        FilterCriterion mismatch = new FilterCriterion(
                FilterOption.ofField("id"),
                MatchOperation.EQUALS,
                "other-id"
        );
        compiled = factory.compile(List.of(mismatch));
        assertFalse(compiled.test(record));
    }

    @Test
    void testWildcardMatching() {
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ALL_FIELDS,
                MatchOperation.CONTAINS,
                "DE000A2AACC6"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));
    }

    @Test
    void testDotNotationPathWithRecordAndMap() {
        // Query nested metricA
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.dataMap.keyABC.alpha.metricA"),
                MatchOperation.EQUALS,
                "0.47432068758609836"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));

        // Query nested metricB
        FilterCriterion criterion2 = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.dataMap.keyABC.alpha.metricB"),
                MatchOperation.EQUALS,
                "0.47808718268324696"
        );
        compiled = factory.compile(List.of(criterion2));
        assertTrue(compiled.test(record));

        // Mismatch check
        FilterCriterion mismatch = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.dataMap.keyABC.alpha.metricA"),
                MatchOperation.EQUALS,
                "0.999"
        );
        compiled = factory.compile(List.of(mismatch));
        assertFalse(compiled.test(record));
    }

    @Test
    void testDotNotationPathWithListAndIndex() {
        // Query: subContainer.detailsContainer.recordsList.1.value EQUALS 0.0035 (2nd item value)
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ofField("subContainer.detailsContainer.recordsList.1.value"),
                MatchOperation.EQUALS,
                "0.0035"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));

        // Test with 1st item (index 0)
        FilterCriterion criterionIndex0 = new FilterCriterion(
                FilterOption.ofField("subContainer.detailsContainer.recordsList.0.value"),
                MatchOperation.EQUALS,
                "-0.0791"
        );
        compiled = factory.compile(List.of(criterionIndex0));
        assertTrue(compiled.test(record));

        // Index out of bounds should not match and return false safely
        FilterCriterion mismatchIndex = new FilterCriterion(
                FilterOption.ofField("subContainer.detailsContainer.recordsList.99.value"),
                MatchOperation.EQUALS,
                "0.0035"
        );
        compiled = factory.compile(List.of(mismatchIndex));
        assertFalse(compiled.test(record));
    }

    @Test
    void testDotNotationPathArrayMapping() {
        // Query: subContainer.detailsContainer.recordsList.name CONTAINS itemY (searches all list elements)
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ofField("subContainer.detailsContainer.recordsList.name"),
                MatchOperation.CONTAINS,
                "itemY"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));

        FilterCriterion mismatch = new FilterCriterion(
                FilterOption.ofField("subContainer.detailsContainer.recordsList.name"),
                MatchOperation.CONTAINS,
                "itemZ"
        );
        compiled = factory.compile(List.of(mismatch));
        assertFalse(compiled.test(record));
    }

    @Test
    void testLogicalDecimalMatching() {
        // Test targeted decimal query: subContainer.nestedContainer.decimalField EQUALS 0.474320687
        FilterCriterion criterion = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.decimalField"),
                MatchOperation.EQUALS,
                "0.474320687"
        );
        Predicate<GenericRecord> compiled = factory.compile(List.of(criterion));
        assertTrue(compiled.test(record));

        // Test wildcard matching logical decimal
        FilterCriterion wildcard = new FilterCriterion(
                FilterOption.ALL_FIELDS,
                MatchOperation.EQUALS,
                "0.474320687"
        );
        Predicate<GenericRecord> compiledWildcard = factory.compile(List.of(wildcard));
        assertTrue(compiledWildcard.test(record));
    }

    @Test
    void testCornerCaseNullNodesAndPaths() {
        // Test query on a path that does not exist in schema (should return false safely, no exception)
        FilterCriterion criterionNonExistent = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.dataMap.keyABC.alpha.nonExistentField"),
                MatchOperation.EQUALS,
                "0.474320687"
        );
        Predicate<GenericRecord> compiledNonExistent = factory.compile(List.of(criterionNonExistent));
        assertFalse(compiledNonExistent.test(record));

        // Test querying null values (IS_NULL/NOT_NULL ops) on a missing path
        FilterCriterion isNullCriterion = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.dataMap.keyABC.alpha.nonExistentField"),
                MatchOperation.IS_NULL,
                null
        );
        Predicate<GenericRecord> compiledNull = factory.compile(List.of(isNullCriterion));
        assertTrue(compiledNull.test(record));

        // Test query on an existing field whose value is null (timestamp is long, let's add a nullable field)
        // Set subContainer to null and query deep nested values (should return false or true for IS_NULL)
        record.put("subContainer", null);

        FilterCriterion nullNestedQuery = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.decimalField"),
                MatchOperation.EQUALS,
                "0.474320687"
        );
        Predicate<GenericRecord> compiledNullNested = factory.compile(List.of(nullNestedQuery));
        assertFalse(compiledNullNested.test(record));

        FilterCriterion nullNestedIsNullQuery = new FilterCriterion(
                FilterOption.ofField("subContainer.nestedContainer.decimalField"),
                MatchOperation.IS_NULL,
                null
        );
        Predicate<GenericRecord> compiledNullNestedIsNull = factory.compile(List.of(nullNestedIsNullQuery));
        assertTrue(compiledNullNestedIsNull.test(record));
    }
}

package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.domain.model.InvalidFilterException;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilterPathValidatorTest {

    private Schema rootSchema;

    @BeforeEach
    void setUp() {
        Schema addressSchema = SchemaBuilder.record("Address").fields()
                .requiredString("city")
                .requiredString("street")
                .endRecord();

        Schema itemSchema = SchemaBuilder.record("Item").fields()
                .requiredString("itemName")
                .requiredDouble("price")
                .endRecord();

        Schema typeA = SchemaBuilder.record("TypeA").fields()
                .requiredString("aField")
                .endRecord();

        Schema typeB = SchemaBuilder.record("TypeB").fields()
                .requiredString("bField")
                .endRecord();

        rootSchema = SchemaBuilder.record("Root").fields()
                .requiredString("id")
                .name("address").type().optional().type(addressSchema)
                .name("tags").type().map().values().stringType().noDefault()
                .name("items").type().array().items(itemSchema).noDefault()
                .name("polymorphic").type().unionOf().nullType().and().type(typeA).and().type(typeB).endUnion().nullDefault()
                .endRecord();
    }

    @Test
    void testValidPaths() {
        List<FilterCriterion> criteria = List.of(
                new FilterCriterion(FilterOption.ALL_FIELDS, MatchOperation.CONTAINS, "test"),
                new FilterCriterion(FilterOption.ofField("id"), MatchOperation.EQUALS, "123"),
                new FilterCriterion(FilterOption.ofField("address.city"), MatchOperation.EQUALS, "New York"),
                new FilterCriterion(FilterOption.ofField("tags.anyKey"), MatchOperation.EQUALS, "prod"),
                new FilterCriterion(FilterOption.ofField("items.itemName"), MatchOperation.CONTAINS, "book"),
                new FilterCriterion(FilterOption.ofField("items.0.price"), MatchOperation.EQUALS, "10"),
                new FilterCriterion(FilterOption.ofField("polymorphic.bField"), MatchOperation.EQUALS, "valueB")
        );

        assertDoesNotThrow(() -> FilterPathValidator.validate(rootSchema, List.of(new FilterGroup(criteria))));
    }

    @Test
    void testInvalidPathsThrowException() {
        List<FilterCriterion> criteria = List.of(
                new FilterCriterion(FilterOption.ofField("id.invalidSub"), MatchOperation.EQUALS, "val"),
                new FilterCriterion(FilterOption.ofField("address.nonExistent"), MatchOperation.EQUALS, "val"),
                new FilterCriterion(FilterOption.ofField("wrongRootField"), MatchOperation.EQUALS, "val")
        );

        InvalidFilterException ex = assertThrows(InvalidFilterException.class, () ->
                FilterPathValidator.validate(rootSchema, List.of(new FilterGroup(criteria))));

        List<String> invalid = ex.getInvalidPaths();
        assertEquals(3, invalid.size());
        assertTrue(invalid.contains("id.invalidSub"));
        assertTrue(invalid.contains("address.nonExistent"));
        assertTrue(invalid.contains("wrongRootField"));
    }
}

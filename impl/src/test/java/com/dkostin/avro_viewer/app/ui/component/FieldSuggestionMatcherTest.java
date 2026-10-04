package com.dkostin.avro_viewer.app.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldSuggestionMatcherTest {

    @Test
    void testBlankQueryMatchesEverything() {
        assertTrue(FieldSuggestionMatcher.matches("", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches("  ", "id", false));
        assertTrue(FieldSuggestionMatcher.matches(null, "user.name", false));
        assertTrue(FieldSuggestionMatcher.matches("", "*", true));
    }

    @Test
    void testWildcardMatching() {
        assertTrue(FieldSuggestionMatcher.matches("*", "*", true));
        assertTrue(FieldSuggestionMatcher.matches("all", "*", true));
        assertTrue(FieldSuggestionMatcher.matches("field", "*", true));
        assertFalse(FieldSuggestionMatcher.matches("city", "*", true));
    }

    @Test
    void testDirectSubstringMatch() {
        assertTrue(FieldSuggestionMatcher.matches("addr", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches("address", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches(".ci", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches("city", "address.city", false));
    }

    @Test
    void testLeafSegmentMatch() {
        assertTrue(FieldSuggestionMatcher.matches("lat", "location.geo.lat", false));
        assertTrue(FieldSuggestionMatcher.matches("sku", "order.items.sku", false));
    }

    @Test
    void testCaseInsensitiveMatching() {
        assertTrue(FieldSuggestionMatcher.matches("CITY", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches("City", "address.city", false));
        assertTrue(FieldSuggestionMatcher.matches("address", "Address.City", false));
    }

    @Test
    void testNonMatchingQuery() {
        assertFalse(FieldSuggestionMatcher.matches("zip", "address.city", false));
        assertFalse(FieldSuggestionMatcher.matches("abc", "id", false));
        assertFalse(FieldSuggestionMatcher.matches("name", null, false));
    }
}

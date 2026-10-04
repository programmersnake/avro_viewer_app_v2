package com.dkostin.avro_viewer.app.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PresentationFormatterTest {

    @Test
    void testFormatBigDecimal() {
        assertNull(PresentationFormatter.formatBigDecimal(null));
        assertEquals("50", PresentationFormatter.formatBigDecimal(new BigDecimal("50.000")));
        assertEquals("0.5", PresentationFormatter.formatBigDecimal(new BigDecimal("0.50")));
        assertEquals("100", PresentationFormatter.formatBigDecimal(new BigDecimal("1E+2")));
    }

    @Test
    void testFormatValue() {
        assertEquals("", PresentationFormatter.formatValue(null));
        assertEquals("ACTIVE", PresentationFormatter.formatValue(Status.ACTIVE));
        assertEquals("hello", PresentationFormatter.formatValue(new StringBuilder("hello")));
        assertEquals("50", PresentationFormatter.formatValue(new BigDecimal("50.000")));
        assertEquals("true", PresentationFormatter.formatValue(true));
        assertEquals("123", PresentationFormatter.formatValue(123));
    }

    @Test
    void testFormatFileSize() {
        assertEquals("0 B", PresentationFormatter.formatFileSize(-5));
        assertEquals("0 B", PresentationFormatter.formatFileSize(0));
        assertEquals("512 B", PresentationFormatter.formatFileSize(512));
        assertEquals("1.0 KB (1,024 bytes)", PresentationFormatter.formatFileSize(1024));
        assertEquals("14.8 MB (15,518,720 bytes)", PresentationFormatter.formatFileSize(15_518_720));
        assertEquals("2.0 GB (2,147,483,648 bytes)", PresentationFormatter.formatFileSize(2L * 1024 * 1024 * 1024));
    }

    @Test
    void testFormatCount() {
        assertEquals("Unknown", PresentationFormatter.formatCount(-1));
        assertEquals("0", PresentationFormatter.formatCount(0));
        assertEquals("1,234", PresentationFormatter.formatCount(1234));
        assertEquals("1,234,567", PresentationFormatter.formatCount(1_234_567));
    }

    @Test
    void testFormatDateTime() {
        assertEquals("—", PresentationFormatter.formatDateTime(null));
        java.time.Instant instant = java.time.Instant.parse("2026-10-03T12:00:00Z");
        String formatted = PresentationFormatter.formatDateTime(instant);
        // Should contain year and month/day
        org.junit.jupiter.api.Assertions.assertTrue(formatted.startsWith("2026-10-03"));
    }

    enum Status {ACTIVE, INACTIVE}
}

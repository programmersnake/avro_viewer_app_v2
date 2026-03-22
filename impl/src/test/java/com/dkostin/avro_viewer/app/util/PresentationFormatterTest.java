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

    enum Status {ACTIVE, INACTIVE}
}

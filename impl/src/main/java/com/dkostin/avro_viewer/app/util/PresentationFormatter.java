package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public final class PresentationFormatter {

    public static String formatBigDecimal(BigDecimal bd) {
        if (bd == null) return null;
        return bd.stripTrailingZeros().toPlainString();
    }

    public static String formatValue(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal bd -> formatBigDecimal(bd);
            case CharSequence cs -> cs.toString();
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
    }
}

package com.dkostin.avro_viewer.app.util;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

@UtilityClass
public final class PresentationFormatter {

    public static String formatBigDecimal(BigDecimal bd) {
        if (bd == null) return null;
        return bd.stripTrailingZeros().toPlainString();
    }

    public static String formatValue(Object value) {
        return switch (value) {
            case null -> "";
            case Map<?, ?> map -> formatMap(map);
            case Collection<?> coll -> formatCollection(coll);
            case BigDecimal bd -> formatBigDecimal(bd);
            case CharSequence cs -> cs.toString();
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
    }

    private static String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append("=").append(formatValue(e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String formatCollection(Collection<?> coll) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : coll) {
            if (!first) sb.append(", ");
            sb.append(formatValue(item));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}


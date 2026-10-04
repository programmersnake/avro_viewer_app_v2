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

    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        exp = Math.min(exp, units.length - 1);
        double value = bytes / Math.pow(1024, exp);
        String formattedBytes = java.text.NumberFormat.getInstance(java.util.Locale.US).format(bytes);
        return String.format(java.util.Locale.ROOT, "%.1f %s (%s bytes)", value, units[exp], formattedBytes);
    }

    public static String formatCount(long count) {
        if (count < 0) {
            return "Unknown";
        }
        return java.text.NumberFormat.getInstance(java.util.Locale.US).format(count);
    }

    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault());

    public static String formatDateTime(java.time.Instant instant) {
        if (instant == null) return "—";
        return DATE_TIME_FORMATTER.format(instant);
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


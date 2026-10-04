package com.dkostin.avro_viewer.app.domain.model.filter;

/**
 * Represents a selectable field option in the filter system.
 * Separates the internal semantic classification (wildcard vs specific field)
 * from its display representation, providing type safety at the UI boundary.
 *
 * @param fieldName the schema field name, or {@code "*"} for the wildcard
 * @param wildcard  {@code true} if this option represents "all fields" recursive search
 */
public record FilterOption(String fieldName, boolean wildcard, String typeDisplay, int depth) {

    /** The wildcard option that searches all fields recursively via DFS. */
    public static final FilterOption ALL_FIELDS = new FilterOption("*", true, "", 0);

    public FilterOption(String fieldName, boolean wildcard) {
        this(fieldName, wildcard, "", 0);
    }

    /** Creates a FilterOption for a specific schema field. */
    public static FilterOption ofField(String name) {
        return new FilterOption(name, false, "", 0);
    }

    /** Creates a FilterOption with typeDisplay and hierarchy depth. */
    public static FilterOption ofField(String name, String typeDisplay, int depth) {
        return new FilterOption(name, false, typeDisplay != null ? typeDisplay : "", depth);
    }

    @Override
    public String toString() {
        return wildcard ? "* (All Fields)" : fieldName;
    }
}

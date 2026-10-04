package com.dkostin.avro_viewer.app.ui.component;

/**
 * Pure matcher for field autocomplete suggestions.
 * Matches by substring across the full dot-path as well as the leaf segment.
 * Kept free of JavaFX dependencies so it can be thoroughly tested via standard JUnit tests.
 */
public final class FieldSuggestionMatcher {

    private FieldSuggestionMatcher() {
    }

    /**
     * Determines whether the candidate field option matches the user query.
     *
     * @param query       the user input query
     * @param candidatePath the candidate path (e.g. "address.city")
     * @param isWildcard  true if this candidate is the "* (All Fields)" option
     * @return true if the candidate matches
     */
    public static boolean matches(String query, String candidatePath, boolean isWildcard) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String q = query.trim().toLowerCase();

        if (isWildcard) {
            return q.equals("*") || "all fields".contains(q) || "* (all fields)".contains(q);
        }

        if (candidatePath == null || candidatePath.isBlank()) {
            return false;
        }

        String path = candidatePath.trim().toLowerCase();

        // Direct substring of full path
        if (path.contains(q)) {
            return true;
        }

        // Substring of leaf segment (after last dot)
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < path.length() - 1) {
            String leaf = path.substring(lastDot + 1);
            if (leaf.contains(q)) {
                return true;
            }
        }

        return false;
    }
}

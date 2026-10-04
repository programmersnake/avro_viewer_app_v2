package com.dkostin.avro_viewer.app.domain.model.fileinfo;

import java.util.List;

/**
 * Hierarchical tree node representing an Avro schema field or nested structural component.
 */
public record SchemaNode(
        String name,
        String path,
        int depth,
        NodeKind kind,
        String typeDisplay,
        boolean nullable,
        String defaultValue,
        String doc,
        List<FieldRule> rules,
        List<SchemaNode> children,
        boolean recursiveRef,
        String unionBranch
) {
    public SchemaNode {
        rules = rules == null ? List.of() : List.copyOf(rules);
        children = children == null ? List.of() : List.copyOf(children);
    }
}

package com.dkostin.avro_viewer.app.ui.component;

public record JsonTreeNode(String key, Object value, NodeType type) {
    public enum NodeType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    @Override
    public String toString() {
        return key + (value != null ? ": " + value : "");
    }
}

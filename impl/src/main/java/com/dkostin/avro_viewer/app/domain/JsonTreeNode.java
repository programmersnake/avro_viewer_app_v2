package com.dkostin.avro_viewer.app.domain;

public record JsonTreeNode(String key, Object value, NodeType type) {
    public enum NodeType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }
}

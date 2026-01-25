package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.data.JsonSerializer;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.component.ErrorAlert.showError;

@RequiredArgsConstructor
public class JsonRowViewerWindow {

    private Stage jsonStage;
    private TreeView<JsonTreeNode> jsonTreeView;
    private String rawJsonCache;

    public void openJsonWindow(Map<String, Object> row, Scene ownerScene) {
        try {
            this.rawJsonCache = JsonSerializer.toJsonSafe(row);

            if (jsonStage == null) {
                initStage(ownerScene);
            }

            TreeItem<JsonTreeNode> root = buildTree("root", row);
            root.setExpanded(true);
            jsonTreeView.setRoot(root);

            jsonStage.show();
            jsonStage.toFront();

        } catch (Exception ex) {
            showError("JSON structure view failed", ex);
        }
    }

    private void initStage(Scene ownerScene) {
        jsonTreeView = new TreeView<>();
        jsonTreeView.setShowRoot(false);
        jsonTreeView.getStyleClass().add("table-view");

        jsonTreeView.setCellFactory(_ -> new TreeCell<>() {
            @Override
            protected void updateItem(JsonTreeNode item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                boolean isContainer = (item.type() == JsonTreeNode.NodeType.OBJECT || item.type() == JsonTreeNode.NodeType.ARRAY);

                // 1. Key
                Text keyText = new Text(item.key());
                if (isContainer) {
                    keyText.getStyleClass().add("json-key-container");
                } else {
                    keyText.getStyleClass().add("json-key");
                }

                // 2. Value
                TextFlow flow;
                switch (item.type()) {
                    case OBJECT -> {
                        Text icon = new Text(" { } ");
                        icon.getStyleClass().add("json-icon-object");

                        int size = getTreeItem().getChildren().size();
                        Text meta = new Text("(" + size + " fields)");
                        meta.getStyleClass().add("json-meta");

                        flow = new TextFlow(keyText, icon, meta);
                    }
                    case ARRAY -> {
                        Text icon = new Text(" [ ] ");
                        icon.getStyleClass().add("json-icon-array");

                        int size = getTreeItem().getChildren().size();
                        Text meta = new Text(size + " items");
                        meta.getStyleClass().add("json-meta");

                        flow = new TextFlow(keyText, icon, meta);
                    }
                    default -> {
                        Text separator = new Text(" : ");
                        separator.getStyleClass().add("json-separator");

                        Text valueText = createValueText(item);
                        flow = new TextFlow(keyText, separator, valueText);
                    }
                }

                setGraphic(flow);
            }
        });

        var root = getBorderPane();

        jsonStage = new Stage();
        jsonStage.setTitle("Record Inspector");
        jsonStage.initOwner(ownerScene.getWindow());
        jsonStage.initModality(javafx.stage.Modality.NONE);

        Scene s = new Scene(root, 700, 800);
        if (ownerScene.getStylesheets() != null) {
            s.getStylesheets().setAll(ownerScene.getStylesheets());
        }
        jsonStage.setScene(s);
    }

    private Text createValueText(JsonTreeNode item) {
        Text t = new Text();
        switch (item.type()) {
            case STRING -> {
                t.setText("\"" + item.value() + "\"");
                t.getStyleClass().add("json-string");
            }
            case NUMBER -> {
                t.setText(String.valueOf(item.value()));
                t.getStyleClass().add("json-number");
            }
            case BOOLEAN -> {
                t.setText(String.valueOf(item.value()));
                t.getStyleClass().add("json-boolean");
            }
            case NULL -> {
                t.setText("null");
                t.getStyleClass().add("json-null");
            }
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private TreeItem<JsonTreeNode> buildTree(String key, Object data) {
        JsonTreeNode.NodeType type;

        if (data == null) type = JsonTreeNode.NodeType.NULL;
        else if (data instanceof Map) type = JsonTreeNode.NodeType.OBJECT;
        else if (data instanceof GenericRecord) type = JsonTreeNode.NodeType.OBJECT;
        else if (data instanceof List) type = JsonTreeNode.NodeType.ARRAY;
        else if (data instanceof Number) type = JsonTreeNode.NodeType.NUMBER;
        else if (data instanceof Boolean) type = JsonTreeNode.NodeType.BOOLEAN;
        else type = JsonTreeNode.NodeType.STRING;

        JsonTreeNode node = new JsonTreeNode(key, data, type);
        TreeItem<JsonTreeNode> item = new TreeItem<>(node);

        if (data instanceof Map<?, ?> map) {
            map.forEach((k, v) -> item.getChildren().add(buildTree(String.valueOf(k), v)));
        }
        else if (data instanceof GenericRecord record) {
            for (Schema.Field field : record.getSchema().getFields()) {
                Object value = record.get(field.pos());
                item.getChildren().add(buildTree(field.name(), value));
            }
        }
        else if (data instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                item.getChildren().add(buildTree("[" + i + "]", list.get(i)));
            }
        }
        return item;
    }

    private BorderPane getBorderPane() {
        Button copyBtn = new Button("Copy JSON");
        copyBtn.getStyleClass().add("btn");

        copyBtn.setOnAction(_ -> {
            var content = new ClipboardContent();
            content.putString(this.rawJsonCache);
            Clipboard.getSystemClipboard().setContent(content);
        });

        // --- EXPAND BUTTON ---
        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.getStyleClass().add("btn");
        expandAllBtn.setOnAction(_ -> expandAll(jsonTreeView.getRoot()));

        // --- COLLAPSE BUTTON ---
        Button collapseBtn = new Button("Collapse All");
        collapseBtn.getStyleClass().add("btn");
        collapseBtn.setOnAction(_ -> {
            if (jsonTreeView.getRoot() != null) {
                collapseAll(jsonTreeView.getRoot());
                jsonTreeView.getRoot().setExpanded(true);
            }
        });

        ToolBar toolBar = new ToolBar(copyBtn, new Separator(), expandAllBtn, collapseBtn);
        toolBar.getStyleClass().add("topbar");

        var root = new BorderPane(jsonTreeView);
        root.setTop(toolBar);
        root.getStyleClass().add("surface");

        return root;
    }

    private void expandAll(TreeItem<?> item) {
        if(item != null && !item.isLeaf()){
            item.setExpanded(true);
            for(TreeItem<?> child : item.getChildren()){
                expandAll(child);
            }
        }
    }

    private void collapseAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            for (TreeItem<?> child : item.getChildren()) {
                collapseAll(child);
            }
        }
    }

    public void syncStyles(javafx.collections.ObservableList<String> styles) {
        if (jsonStage != null && jsonStage.getScene() != null) {
            jsonStage.getScene().getStylesheets().setAll(styles);
        }
    }
}
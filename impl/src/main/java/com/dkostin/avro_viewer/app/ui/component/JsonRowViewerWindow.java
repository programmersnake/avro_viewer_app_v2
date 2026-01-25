package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.data.JsonSerializer;
import com.dkostin.avro_viewer.app.domain.JsonTreeNode;
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
            // Cache JSON for Copy Button
            this.rawJsonCache = JsonSerializer.toJsonSafe(row);

            if (jsonStage == null) {
                initStage(ownerScene);
            }

            // Build Tree
            TreeItem<JsonTreeNode> root = buildTree("root", row);
            root.setExpanded(true); // Expand first level
            jsonTreeView.setRoot(root);

            jsonStage.show();
            jsonStage.toFront();

        } catch (Exception ex) {
            showError("JSON structure view failed", ex);
        }
    }

    private void initStage(Scene ownerScene) {
        jsonTreeView = new TreeView<>();
        jsonTreeView.setShowRoot(false); // Hide technical root level

        // --- VIEW: Magic of view ---
        jsonTreeView.setCellFactory(_ -> new TreeCell<>() {
            @Override
            protected void updateItem(JsonTreeNode item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                // 1. KEY (name of field)
                Text keyText = new Text(item.key());
                boolean isContainer = (item.type() == JsonTreeNode.NodeType.OBJECT || item.type() == JsonTreeNode.NodeType.ARRAY);

                // If it is folder (Object/Array) - BOLD
                if (isContainer) {
                    keyText.setStyle("-fx-font-weight: bold; -fx-fill: #2c3e50; -fx-font-size: 13px;");
                } else {
                    keyText.setStyle("-fx-font-weight: normal; -fx-fill: #8e44ad;");
                }

                // 2. Value and Icons
                TextFlow flow;

                switch (item.type()) {
                    case OBJECT -> {
                        // For Map/Record add icons {}
                        Text icon = new Text(" { } ");
                        icon.setStyle("-fx-fill: #e67e22; -fx-font-weight: bold;");

                        // Metadata: how a lot of fields inside
                        int size = getTreeItem().getChildren().size();
                        Text meta = new Text("(" + size + " fields)");
                        meta.setStyle("-fx-fill: #95a5a6; -fx-font-size: 10px;");

                        flow = new TextFlow(keyText, icon, meta);
                    }
                    case ARRAY -> {
                        // For List add icons []
                        Text icon = new Text(" [ ] ");
                        icon.setStyle("-fx-fill: #2980b9; -fx-font-weight: bold;");

                        int size = getTreeItem().getChildren().size();
                        Text meta = new Text(size + " items");
                        meta.setStyle("-fx-fill: #95a5a6; -fx-font-size: 10px;");

                        flow = new TextFlow(keyText, icon, meta);
                    }
                    default -> {
                        // For primitives
                        Text separator = new Text(" : ");
                        separator.setStyle("-fx-fill: gray;");

                        Text valueText = formatPrimitiveValue(item);
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
        jsonStage.initModality(javafx.stage.Modality.NONE); // Enable clicking on main window

        Scene s = new Scene(root, 700, 800);
        if (ownerScene.getStylesheets() != null) {
            s.getStylesheets().setAll(ownerScene.getStylesheets());
        }
        jsonStage.setScene(s);
    }

    private Text formatPrimitiveValue(JsonTreeNode item) {
        Text t = new Text();
        switch (item.type()) {
            case STRING -> {
                t.setText("\"" + item.value() + "\"");
                t.setStyle("-fx-fill: #27ae60;");
            }
            case NUMBER -> {
                t.setText(String.valueOf(item.value()));
                t.setStyle("-fx-fill: #2980b9;");
            }
            case BOOLEAN -> {
                t.setText(String.valueOf(item.value()));
                t.setStyle("-fx-fill: #d35400; -fx-font-weight: bold;");
            }
            case NULL -> {
                t.setText("null");
                t.setStyle("-fx-fill: #bdc3c7; -fx-font-style: italic;");
            }
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private TreeItem<JsonTreeNode> buildTree(String key, Object data) {
        TreeItem<JsonTreeNode> item = getIdentifiedType(key, data);

        // 2. Recursion (Taking children)
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

    private static TreeItem<JsonTreeNode> getIdentifiedType(String key, Object data) {
        JsonTreeNode.NodeType type = switch (data) {
            case null -> JsonTreeNode.NodeType.NULL;
            case Map _, GenericRecord _ -> JsonTreeNode.NodeType.OBJECT;
            case List _ -> JsonTreeNode.NodeType.ARRAY;
            case Number _ -> JsonTreeNode.NodeType.NUMBER;
            case Boolean _ -> JsonTreeNode.NodeType.BOOLEAN;
            default -> JsonTreeNode.NodeType.STRING;
        };

        JsonTreeNode node = new JsonTreeNode(key, data, type);
        return new TreeItem<>(node);
    }

    private BorderPane getBorderPane() {
        Button copyBtn = new Button("Copy JSON");
        copyBtn.setOnAction(_ -> {
            var content = new ClipboardContent();
            content.putString(this.rawJsonCache);
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.setOnAction(_ -> expandAll(jsonTreeView.getRoot()));

        ToolBar toolBar = new ToolBar(copyBtn, expandAllBtn);

        var root = new BorderPane(jsonTreeView);
        root.setTop(toolBar);
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

    public void syncStyles(javafx.collections.ObservableList<String> styles) {
        if (jsonStage != null && jsonStage.getScene() != null) {
            jsonStage.getScene().getStylesheets().setAll(styles);
        }
    }
}
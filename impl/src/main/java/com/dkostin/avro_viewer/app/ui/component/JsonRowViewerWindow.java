package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.util.JsonSerializer;
import javafx.animation.PauseTransition;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.List;
import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.component.ErrorAlert.showError;

@RequiredArgsConstructor
public class JsonRowViewerWindow {

    // --- CSS Constants ---
    private static final String CSS_JSON_KEY = "json-key";
    private static final String CSS_JSON_KEY_CONTAINER = "json-key-container";
    private static final String CSS_JSON_ICON_OBJECT = "json-icon-object";
    private static final String CSS_JSON_ICON_ARRAY = "json-icon-array";
    private static final String CSS_JSON_META = "json-meta";
    private static final String CSS_JSON_SEPARATOR = "json-separator";
    private static final String CSS_JSON_STRING = "json-string";
    private static final String CSS_JSON_NUMBER = "json-number";
    private static final String CSS_JSON_BOOLEAN = "json-boolean";
    private static final String CSS_JSON_NULL = "json-null";
    private static final String CSS_SURFACE = "surface";
    private static final String CSS_BTN = "btn";
    private static final String CSS_TOPBAR = "topbar";

    private Stage jsonStage;
    private TreeView<JsonTreeNode> jsonTreeView;
    private String rawJsonCache;
    private Button copyBtn; // Reference needed for feedback animation

    // --- Public API ---

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

    public void syncStyles(ObservableList<String> styles) {
        if (jsonStage != null && jsonStage.getScene() != null) {
            jsonStage.getScene().getStylesheets().setAll(styles);
        }
    }

    // --- Initialization Logic ---

    private void initStage(Scene ownerScene) {
        // 1. Create Components
        createTreeView();
        ToolBar toolBar = createToolBar();
        BorderPane rootLayout = createLayout(toolBar);

        // 2. Setup Stage & Scene
        jsonStage = new Stage();
        jsonStage.setTitle("Record Inspector");
        jsonStage.initOwner(ownerScene.getWindow());
        jsonStage.initModality(javafx.stage.Modality.NONE);

        createScene(rootLayout, ownerScene);
    }

    private void createTreeView() {
        jsonTreeView = new TreeView<>();
        jsonTreeView.setShowRoot(false);
        jsonTreeView.getStyleClass().add("table-view");
        jsonTreeView.setCellFactory(_ -> new JsonTreeCell());

        // Accessibility
        jsonTreeView.setAccessibleText("JSON Structure Tree View");
    }

    private ToolBar createToolBar() {
        copyBtn = new Button("Copy JSON");
        copyBtn.getStyleClass().add(CSS_BTN);
        copyBtn.setAccessibleText("Copy full JSON to clipboard");
        copyBtn.setOnAction(_ -> handleCopyAction());

        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.getStyleClass().add(CSS_BTN);
        expandAllBtn.setAccessibleText("Expand all nodes");
        expandAllBtn.setOnAction(_ -> expandAll(jsonTreeView.getRoot()));

        Button collapseBtn = new Button("Collapse All");
        collapseBtn.getStyleClass().add(CSS_BTN);
        collapseBtn.setAccessibleText("Collapse all nodes");
        collapseBtn.setOnAction(_ -> handleCollapseAction());

        ToolBar toolBar = new ToolBar(copyBtn, new Separator(), expandAllBtn, collapseBtn);
        toolBar.getStyleClass().add(CSS_TOPBAR);
        return toolBar;
    }

    private BorderPane createLayout(ToolBar toolBar) {
        var root = new BorderPane(jsonTreeView);
        root.setTop(toolBar);
        root.getStyleClass().add(CSS_SURFACE);
        return root;
    }

    private void createScene(BorderPane root, Scene ownerScene) {
        Scene scene = new Scene(root, 700, 800);

        if (ownerScene.getStylesheets() != null) {
            scene.getStylesheets().setAll(ownerScene.getStylesheets());
        }

        // Keyboard Shortcuts
        setupAccelerators(scene);

        jsonStage.setScene(scene);
    }

    private void setupAccelerators(Scene scene) {
        // CTRL+C -> Copy
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN),
                this::handleCopyAction
        );

        // CTRL + PLUS -> Expand All
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN), // Equals is often '+' key
                () -> expandAll(jsonTreeView.getRoot())
        );

        // CTRL + MINUS -> Collapse All
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN),
                this::handleCollapseAction
        );
    }

    // --- Actions & Event Handlers ---

    private void handleCopyAction() {
        if (this.rawJsonCache == null) return;

        var content = new ClipboardContent();
        content.putString(this.rawJsonCache);
        Clipboard.getSystemClipboard().setContent(content);

        // UX Feedback: Change button text temporarily
        String originalText = copyBtn.getText();
        copyBtn.setText("Copied!");
        copyBtn.setDisable(true); // Prevent spamming

        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(_ -> {
            copyBtn.setText(originalText);
            copyBtn.setDisable(false);
        });
        pause.play();
    }

    private void handleCollapseAction() {
        if (jsonTreeView.getRoot() != null) {
            collapseAll(jsonTreeView.getRoot());
            // Keep root expanded because setShowRoot is false
            jsonTreeView.getRoot().setExpanded(true);
        }
    }

    private void expandAll(TreeItem<?> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
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

    // --- Data Logic (Tree Building) ---

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
        } else if (data instanceof GenericRecord record) {
            for (Schema.Field field : record.getSchema().getFields()) {
                Object value = record.get(field.pos());
                item.getChildren().add(buildTree(field.name(), value));
            }
        } else if (data instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                item.getChildren().add(buildTree("[" + i + "]", list.get(i)));
            }
        }
        return item;
    }

    // --- Inner Classes & Records ---

    public record JsonTreeNode(String key, Object value, NodeType type) {
        public enum NodeType {OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL}

        @Override
        public String toString() {
            return key + (value != null ? ": " + value : "");
        }
    }

    /**
     * Named TreeCell implementation to clean up the init code.
     */
    private static class JsonTreeCell extends TreeCell<JsonTreeNode> {

        @Override
        protected void updateItem(JsonTreeNode item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            boolean isContainer = (item.type() == JsonTreeNode.NodeType.OBJECT
                    || item.type() == JsonTreeNode.NodeType.ARRAY);

            // 1. Build Key Text
            Text keyText = new Text(item.key());
            keyText.getStyleClass().add(isContainer ? CSS_JSON_KEY_CONTAINER : CSS_JSON_KEY);

            // 2. Build Value Flow
            TextFlow flow;
            switch (item.type()) {
                case OBJECT -> {
                    Text icon = new Text(" { } ");
                    icon.getStyleClass().add(CSS_JSON_ICON_OBJECT);

                    int size = getTreeItem().getChildren().size();
                    Text meta = new Text("(" + size + " fields)");
                    meta.getStyleClass().add(CSS_JSON_META);

                    flow = new TextFlow(keyText, icon, meta);
                }
                case ARRAY -> {
                    Text icon = new Text(" [ ] ");
                    icon.getStyleClass().add(CSS_JSON_ICON_ARRAY);

                    int size = getTreeItem().getChildren().size();
                    Text meta = new Text(size + " items");
                    meta.getStyleClass().add(CSS_JSON_META);

                    flow = new TextFlow(keyText, icon, meta);
                }
                default -> {
                    Text separator = new Text(" : ");
                    separator.getStyleClass().add(CSS_JSON_SEPARATOR);

                    Text valueText = createValueText(item);
                    flow = new TextFlow(keyText, separator, valueText);
                }
            }

            setGraphic(flow);
        }

        private Text createValueText(JsonTreeNode item) {
            Text t = new Text();
            switch (item.type()) {
                case STRING -> {
                    t.setText("\"" + item.value() + "\"");
                    t.getStyleClass().add(CSS_JSON_STRING);
                }
                case NUMBER -> {
                    t.setText(String.valueOf(item.value()));
                    t.getStyleClass().add(CSS_JSON_NUMBER);
                }
                case BOOLEAN -> {
                    t.setText(String.valueOf(item.value()));
                    t.getStyleClass().add(CSS_JSON_BOOLEAN);
                }
                case NULL -> {
                    t.setText("null");
                    t.getStyleClass().add(CSS_JSON_NULL);
                }
            }
            return t;
        }
    }
}
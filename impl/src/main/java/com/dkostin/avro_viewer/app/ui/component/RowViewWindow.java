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

import java.util.List;
import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.component.ErrorAlert.showError;

@RequiredArgsConstructor
public class RowViewWindow {

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
    private Map<String, Object> currentRowCache;
    private String currentFilterQuery = "";
    private Button copyBtn; // Reference needed for feedback animation
    private TextField filterField;

    // --- Public API ---

    private static TreeItem<JsonTreeNode> getJsonTreeNodeTreeItem(String key, Object data) {
        JsonTreeNode.NodeType type = switch (data) {
            case null -> JsonTreeNode.NodeType.NULL;
            case Map<?, ?> _ -> JsonTreeNode.NodeType.OBJECT;
            case List<?> _ -> JsonTreeNode.NodeType.ARRAY;
            case Number _ -> JsonTreeNode.NodeType.NUMBER;
            case Boolean _ -> JsonTreeNode.NodeType.BOOLEAN;
            default -> JsonTreeNode.NodeType.STRING;
        };

        JsonTreeNode node = new JsonTreeNode(key, data, type);
        return new TreeItem<>(node);
    }

    public void openJsonWindow(Map<String, Object> row, Scene ownerScene) {
        openJsonWindow(row, ownerScene, null);
    }

    public void openJsonWindow(Map<String, Object> row, Scene ownerScene, String highlightKey) {
        try {
            this.currentRowCache = row;
            this.rawJsonCache = JsonSerializer.toJsonSafe(row);

            if (jsonStage == null) {
                initStage(ownerScene);
            }

            if (filterField != null) {
                filterField.clear();
            }
            this.currentFilterQuery = "";

            TreeItem<JsonTreeNode> root = buildTree("root", row);
            root.setExpanded(true);
            jsonTreeView.setRoot(root);

            if (highlightKey != null) {
                TreeItem<JsonTreeNode> targetItem = null;
                for (TreeItem<JsonTreeNode> child : root.getChildren()) {
                    if (child.getValue() != null && highlightKey.equals(child.getValue().key())) {
                        targetItem = child;
                        break;
                    }
                }

                if (targetItem != null) {
                    targetItem.setExpanded(true);
                    jsonTreeView.getSelectionModel().select(targetItem);
                    final TreeItem<JsonTreeNode> finalTarget = targetItem;
                    javafx.application.Platform.runLater(() -> {
                        int index = jsonTreeView.getRow(finalTarget);
                        if (index >= 0) {
                            jsonTreeView.scrollTo(index);
                        }
                    });
                }
            }

            jsonStage.show();
            jsonStage.toFront();

        } catch (Exception ex) {
            showError("JSON structure view failed", ex);
        }
    }

    private void applyFilter(String query) {
        if (currentRowCache == null) return;
        this.currentFilterQuery = query == null ? "" : query.trim().toLowerCase();

        TreeItem<JsonTreeNode> root = buildTree("root", currentRowCache);
        if (!currentFilterQuery.isEmpty()) {
            TreeItem<JsonTreeNode> filteredRoot = filterNode(root, currentFilterQuery);
            if (filteredRoot == null) {
                filteredRoot = new TreeItem<>(new JsonTreeNode("No matches", null, JsonTreeNode.NodeType.STRING));
            }
            filteredRoot.setExpanded(true);
            jsonTreeView.setRoot(filteredRoot);
        } else {
            root.setExpanded(true);
            jsonTreeView.setRoot(root);
        }
    }

    private TreeItem<JsonTreeNode> filterNode(TreeItem<JsonTreeNode> node, String query) {
        JsonTreeNode value = node.getValue();
        boolean matchesSelf = false;
        if (value != null) {
            String key = value.key() != null ? value.key().toLowerCase() : "";
            String valStr = value.value() != null ? String.valueOf(value.value()).toLowerCase() : "";
            matchesSelf = key.contains(query) || valStr.contains(query);
        }

        List<TreeItem<JsonTreeNode>> matchingChildren = new java.util.ArrayList<>();
        for (TreeItem<JsonTreeNode> child : node.getChildren()) {
            TreeItem<JsonTreeNode> filteredChild = filterNode(child, query);
            if (filteredChild != null) {
                matchingChildren.add(filteredChild);
            }
        }

        if (matchesSelf || !matchingChildren.isEmpty()) {
            TreeItem<JsonTreeNode> copy = new TreeItem<>(value);
            copy.getChildren().addAll(matchingChildren);
            copy.setExpanded(true);
            return copy;
        }

        return null;
    }

    // --- Initialization Logic ---

    public void syncStyles(ObservableList<String> styles) {
        if (jsonStage != null && jsonStage.getScene() != null) {
            jsonStage.getScene().getStylesheets().setAll(styles);
        }
    }

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
        copyBtn.setOnAction(_ -> handleCopyFullJson());

        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.getStyleClass().add(CSS_BTN);
        expandAllBtn.setAccessibleText("Expand all nodes");
        expandAllBtn.setOnAction(_ -> expandAll(jsonTreeView.getRoot()));

        Button collapseBtn = new Button("Collapse All");
        collapseBtn.getStyleClass().add(CSS_BTN);
        collapseBtn.setAccessibleText("Collapse all nodes");
        collapseBtn.setOnAction(_ -> handleCollapseAction());

        filterField = new TextField();
        filterField.setPromptText("Filter...");
        filterField.setPrefWidth(140);
        filterField.textProperty().addListener((_, _, newVal) -> applyFilter(newVal));

        ToolBar toolBar = new ToolBar(
                copyBtn, 
                new Separator(), 
                expandAllBtn, 
                collapseBtn, 
                new Separator(), 
                filterField
        );
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

    // --- Actions & Event Handlers ---

    private void setupAccelerators(Scene scene) {
        // CTRL+C -> Smart copy: selected node value or full JSON fallback
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN),
                this::handleSmartCopy
        );

        // CTRL + PLUS -> Expand All
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN),
                () -> expandAll(jsonTreeView.getRoot())
        );

        // CTRL + MINUS -> Collapse All
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN),
                this::handleCollapseAction
        );
    }

    /**
     * Ctrl+C handler: copies the selected node's default value (raw value for primitives,
     * JSON fragment for containers). Falls back to full JSON if nothing is selected.
     */
    private void handleSmartCopy() {
        TreeItem<JsonTreeNode> selected = jsonTreeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null) {
            JsonTreeNode node = selected.getValue();
            String text = node.isContainer()
                    ? JsonSerializer.toJsonSafe(node.value())
                    : rawValueString(node);
            copyToClipboard(text);
            showCopyFeedbackOnCell();
        } else {
            // fallback: copy full JSON
            handleCopyFullJson();
        }
    }

    /**
     * Toolbar "Copy JSON" button: always copies the entire record.
     */
    private void handleCopyFullJson() {
        if (this.rawJsonCache == null) return;

        copyToClipboard(this.rawJsonCache);

        // UX Feedback: Change button text temporarily
        String originalText = copyBtn.getText();
        copyBtn.setText("Copied!");
        copyBtn.setDisable(true);

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

    // --- Clipboard Helpers ---

    private static void copyToClipboard(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Extracts the raw, unformatted value string from a primitive node.
     * This is the domain value, NOT the UI-formatted text (no quotes, no formatting).
     */
    private static String rawValueString(JsonTreeNode node) {
        return switch (node.type()) {
            case NULL -> "null";
            case NUMBER -> com.dkostin.avro_viewer.app.util.PresentationFormatter.formatValue(node.value());
            default -> String.valueOf(node.value());
        };
    }

    /**
     * Shows a temporary Tooltip on the currently selected tree cell as copy feedback.
     */
    private void showCopyFeedbackOnCell() {
        int selectedIndex = jsonTreeView.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) return;

        // Lookup the visible cell for the selected index
        jsonTreeView.lookupAll(".tree-cell").stream()
                .filter(n -> n instanceof JsonTreeCell cell && cell.getIndex() == selectedIndex)
                .findFirst()
                .ifPresent(n -> {
                    Tooltip tip = new Tooltip("Copied!");
                    tip.setAutoHide(true);

                    var bounds = n.localToScreen(n.getBoundsInLocal());
                    if (bounds != null) {
                        tip.show(n, bounds.getMaxX() + 4, bounds.getMinY());
                        PauseTransition hide = new PauseTransition(Duration.seconds(1.2));
                        hide.setOnFinished(_ -> tip.hide());
                        hide.play();
                    }
                });
    }

    // --- Data Logic (Tree Building) ---

    @SuppressWarnings("unchecked")
    private TreeItem<JsonTreeNode> buildTree(String key, Object data) {
        TreeItem<JsonTreeNode> item = getJsonTreeNodeTreeItem(key, data);

        if (data instanceof Map<?, ?> map) {
            map.forEach((k, v) -> item.getChildren().add(buildTree(String.valueOf(k), v)));
        } else if (data instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                item.getChildren().add(buildTree("[" + i + "]", list.get(i)));
            }
        }
        return item;
    }

    // --- Inner Classes & Records ---

    public record JsonTreeNode(String key, Object value, NodeType type) {

        public boolean isContainer() {
            return type == NodeType.OBJECT || type == NodeType.ARRAY;
        }

        @Override
        public String toString() {
            return key + (value != null ? ": " + value : "");
        }

        public enum NodeType {OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL}
    }

    /**
     * TreeCell with right-click context menu for granular copy operations.
     * Reuses persistent child nodes to avoid garbage collection churn and layout jitters.
     */
    private class JsonTreeCell extends TreeCell<JsonTreeNode> {

        private final ContextMenu primitiveMenu = new ContextMenu();
        private final ContextMenu containerMenu = new ContextMenu();

        // Persistent sub-nodes to eliminate layout engine recalculation overhead
        private final Text keyText = new Text();
        private final Text separatorText = new Text(" : ");
        private final Text valueText = new Text();
        private final Text iconText = new Text();
        private final Text metaText = new Text();
        private final TextFlow flowContainer = new TextFlow();

        // Tracking fields to skip redundant node rebuilds (preventing color blinking/flashing on selection)
        private JsonTreeNode lastItem;
        private String lastFilterQuery = "";

        JsonTreeCell() {
            // Apply CSS styles to static components
            separatorText.getStyleClass().add(CSS_JSON_SEPARATOR);

            // --- Primitive menu: Copy Key, Copy Value ---
            MenuItem copyKey = new MenuItem("Copy Key");
            copyKey.setOnAction(_ -> {
                if (getItem() != null) {
                    copyToClipboard(getItem().key());
                    showCopyFeedbackOnCell();
                }
            });
            MenuItem copyValue = new MenuItem("Copy Value");
            copyValue.setOnAction(_ -> {
                if (getItem() != null) {
                    copyToClipboard(rawValueString(getItem()));
                    showCopyFeedbackOnCell();
                }
            });
            primitiveMenu.getItems().addAll(copyKey, copyValue);

            // --- Container menu: Copy Key, Copy JSON Fragment ---
            MenuItem copyKeyC = new MenuItem("Copy Key");
            copyKeyC.setOnAction(_ -> {
                if (getItem() != null) {
                    copyToClipboard(getItem().key());
                    showCopyFeedbackOnCell();
                }
            });
            MenuItem copyFragment = new MenuItem("Copy JSON Fragment");
            copyFragment.setOnAction(_ -> {
                if (getItem() != null) {
                    copyToClipboard(JsonSerializer.toJsonSafe(getItem().value()));
                    showCopyFeedbackOnCell();
                }
            });
            containerMenu.getItems().addAll(copyKeyC, copyFragment);
        }

        @Override
        protected void updateItem(JsonTreeNode item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setContextMenu(null);
                lastItem = null;
                lastFilterQuery = "";
                return;
            }

            boolean isContainer = item.isContainer();

            // Context menu: set based on node type
            setContextMenu(isContainer ? containerMenu : primitiveMenu);

            // If the item and filter query are unchanged, early exit (keeps graphic intact and prevents blink)
            if (item.equals(lastItem) && currentFilterQuery.equals(lastFilterQuery)) {
                return;
            }

            lastItem = item;
            lastFilterQuery = currentFilterQuery;

            // 1. Configure Key Text
            keyText.getStyleClass().clear();
            keyText.getStyleClass().add(isContainer ? CSS_JSON_KEY_CONTAINER : CSS_JSON_KEY);
            if (!currentFilterQuery.isEmpty() && item.key().toLowerCase().contains(currentFilterQuery)) {
                keyText.getStyleClass().add("json-highlight-match");
            }
            keyText.setText(item.key());

            // 2. Build Value Flow
            flowContainer.getChildren().clear();
            switch (item.type()) {
                case OBJECT -> {
                    iconText.getStyleClass().clear();
                    iconText.getStyleClass().add(CSS_JSON_ICON_OBJECT);
                    iconText.setText(" { } ");

                    int size = getTreeItem().getChildren().size();
                    metaText.getStyleClass().clear();
                    metaText.getStyleClass().add(CSS_JSON_META);
                    metaText.setText("(" + size + " fields)");

                    flowContainer.getChildren().addAll(keyText, iconText, metaText);
                }
                case ARRAY -> {
                    iconText.getStyleClass().clear();
                    iconText.getStyleClass().add(CSS_JSON_ICON_ARRAY);
                    iconText.setText(" [ ] ");

                    int size = getTreeItem().getChildren().size();
                    metaText.getStyleClass().clear();
                    metaText.getStyleClass().add(CSS_JSON_META);
                    metaText.setText(size + " items");

                    flowContainer.getChildren().addAll(keyText, iconText, metaText);
                }
                default -> {
                    updateValueText(item);
                    flowContainer.getChildren().addAll(keyText, separatorText, valueText);
                }
            }

            setGraphic(flowContainer);
        }

        private void updateValueText(JsonTreeNode item) {
            valueText.getStyleClass().clear();
            String valStr = "";
            switch (item.type()) {
                case STRING -> {
                    valStr = String.valueOf(item.value());
                    valueText.setText("\"" + valStr + "\"");
                    valueText.getStyleClass().add(CSS_JSON_STRING);
                }
                case NUMBER -> {
                    valStr = com.dkostin.avro_viewer.app.util.PresentationFormatter.formatValue(item.value());
                    valueText.setText(valStr);
                    valueText.getStyleClass().add(CSS_JSON_NUMBER);
                }
                case BOOLEAN -> {
                    valStr = String.valueOf(item.value());
                    valueText.setText(valStr);
                    valueText.getStyleClass().add(CSS_JSON_BOOLEAN);
                }
                case NULL -> {
                    valStr = "null";
                    valueText.setText(valStr);
                    valueText.getStyleClass().add(CSS_JSON_NULL);
                }
                case OBJECT, ARRAY -> { /* handled in updateItem */ }
            }
            if (!currentFilterQuery.isEmpty() && valStr.toLowerCase().contains(currentFilterQuery)) {
                valueText.getStyleClass().add("json-highlight-match");
            }
        }
    }
}
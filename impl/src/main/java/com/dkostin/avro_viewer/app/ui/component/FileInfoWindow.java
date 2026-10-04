package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.domain.model.fileinfo.AvroFileInfo;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.FieldRule;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.SchemaNode;
import com.dkostin.avro_viewer.app.service.impl.AtomicFileWriter;
import com.dkostin.avro_viewer.app.util.PresentationFormatter;
import com.dkostin.avro_viewer.app.util.schema.SchemaCatalog;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Dedicated, non-modal window displaying comprehensive information about the open Avro file.
 * Includes persistent overview card, segmented navigation (Fields, Schema, Metadata),
 * rule inspection, schema export, and direct integration with filter generation.
 */
public class FileInfoWindow {

    private Stage stage;
    private AvroFileInfo currentInfo;
    private Consumer<String> onFilterByPath;
    private Task<Long> countingTask;

    // Overview controls
    private Label fileNameLabel;
    private Label filePathLabel;
    private Label sizeLabel;
    private Label rowsLabel;
    private ProgressIndicator rowsSpinner;
    private Label codecLabel;
    private Label modifiedLabel;
    private Label schemaNameLabel;
    private Label schemaStatsLabel;

    // Segmented toggle
    private ToggleGroup sectionToggleGroup;
    private ToggleButton fieldsBtn;
    private ToggleButton schemaBtn;
    private ToggleButton metadataBtn;
    private String lastSelectedSection = "fields";

    // View containers
    private BorderPane contentPane;
    private Node fieldsView;
    private Node schemaView;
    private Node metadataView;

    // Fields view controls
    private TextField fieldsFilterField;
    private TreeTableView<SchemaNode> fieldsTreeTable;
    private VBox rulesDetailPanel;
    private Label rulePathLabel;
    private Label ruleTypeLabel;
    private Label ruleRequiredLabel;
    private Label ruleDefaultLabel;
    private Label ruleDocLabel;
    private VBox rulesListContainer;
    private Button filterByThisBtn;
    private Button copyFieldPathBtn;

    // Schema view controls
    private TextArea schemaTextArea;
    private TextField schemaSearchField;
    private Button copySchemaBtn;
    private Button saveSchemaBtn;
    private int lastSchemaSearchIndex = 0;

    // Metadata view controls
    private TableView<Map.Entry<String, String>> metadataTable;
    private CheckBox showSystemKeysCheckBox;

    public void show(Scene ownerScene,
                     AvroFileInfo info,
                     Supplier<OptionalLong> knownCountSupplier,
                     Callable<Long> countSupplier,
                     Consumer<String> onFilterByPath) {
        this.currentInfo = Objects.requireNonNull(info, "info");
        this.onFilterByPath = onFilterByPath;

        if (stage == null) {
            initStage(ownerScene);
        }

        refresh(info, knownCountSupplier, countSupplier);

        stage.show();
        stage.toFront();
    }

    public void refresh(AvroFileInfo info,
                        Supplier<OptionalLong> knownCountSupplier,
                        Callable<Long> countSupplier) {
        this.currentInfo = Objects.requireNonNull(info, "info");
        if (stage == null) return;

        updateOverview(info);
        updateFieldsView(info);
        updateSchemaView(info);
        updateMetadataView(info);
        triggerRowCount(knownCountSupplier, countSupplier);
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public void syncStyles(ObservableList<String> stylesheets) {
        if (stage != null && stage.getScene() != null && stylesheets != null) {
            stage.getScene().getStylesheets().setAll(stylesheets);
        }
    }

    // ---------------- UI Initialization ----------------

    private void initStage(Scene ownerScene) {
        stage = new Stage();
        stage.setTitle("File Info");
        stage.initOwner(ownerScene.getWindow());
        stage.initModality(Modality.NONE);

        BorderPane rootLayout = new BorderPane();
        rootLayout.getStyleClass().add("surface");

        // Top: Overview card + segmented toggle
        VBox topContainer = new VBox(10);
        topContainer.setPadding(new Insets(12, 14, 8, 14));
        Node overviewCard = createOverviewCard();
        Node segmentedBar = createSegmentedBar();
        topContainer.getChildren().addAll(overviewCard, segmentedBar);
        rootLayout.setTop(topContainer);

        // Center: Dynamic content pane based on selected tab
        contentPane = new BorderPane();
        contentPane.setPadding(new Insets(0, 14, 14, 14));
        rootLayout.setCenter(contentPane);

        createFieldsView();
        createSchemaView();
        createMetadataView();

        selectSection(lastSelectedSection);

        Scene scene = new Scene(rootLayout, 860, 680);
        if (ownerScene.getStylesheets() != null) {
            scene.getStylesheets().setAll(ownerScene.getStylesheets());
        }

        // Accelerators & Close handlers
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        stage.setOnCloseRequest(_ -> cancelCountingTask());
        stage.setScene(scene);
    }

    private Node createOverviewCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));

        // Top line: File name + Copy Path button
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        fileNameLabel = new Label();
        fileNameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");
        fileNameLabel.getStyleClass().add("h1");

        Button copyPathBtn = new Button("Copy path");
        copyPathBtn.getStyleClass().add("btn");
        copyPathBtn.setOnAction(_ -> {
            if (currentInfo != null) {
                copyToClipboard(currentInfo.path().toString(), copyPathBtn, "Copied!");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBox.getChildren().addAll(fileNameLabel, spacer, copyPathBtn);

        // Path label
        filePathLabel = new Label();
        filePathLabel.getStyleClass().add("muted");
        filePathLabel.setStyle("-fx-font-size: 11px;");

        // Grid for file details
        GridPane grid = new GridPane();
        grid.getStyleClass().add("summary-grid");
        grid.setHgap(16);
        grid.setVgap(4);
        grid.setPadding(new Insets(4, 0, 0, 0));

        // Row 0: Size, Rows
        Label sizeTitle = createMutedLabel("Size:");
        sizeLabel = new Label();
        sizeLabel.setStyle("-fx-font-weight: 600;");

        Label rowsTitle = createMutedLabel("Rows:");
        HBox rowsBox = new HBox(6);
        rowsBox.setAlignment(Pos.CENTER_LEFT);
        rowsLabel = new Label();
        rowsLabel.setStyle("-fx-font-weight: 600;");
        rowsSpinner = new ProgressIndicator();
        rowsSpinner.setPrefSize(14, 14);
        rowsSpinner.setVisible(false);
        rowsBox.getChildren().addAll(rowsLabel, rowsSpinner);

        grid.add(sizeTitle, 0, 0);
        grid.add(sizeLabel, 1, 0);
        grid.add(rowsTitle, 2, 0);
        grid.add(rowsBox, 3, 0);

        // Row 1: Codec, Modified
        Label codecTitle = createMutedLabel("Codec:");
        codecLabel = new Label();

        Label modTitle = createMutedLabel("Modified:");
        modifiedLabel = new Label();

        grid.add(codecTitle, 0, 1);
        grid.add(codecLabel, 1, 1);
        grid.add(modTitle, 2, 1);
        grid.add(modifiedLabel, 3, 1);

        // Row 2: Schema name, stats
        Label schemaTitle = createMutedLabel("Schema:");
        schemaNameLabel = new Label();
        schemaNameLabel.setStyle("-fx-font-weight: 600;");

        schemaStatsLabel = new Label();
        schemaStatsLabel.getStyleClass().add("muted");

        grid.add(schemaTitle, 0, 2);
        grid.add(schemaNameLabel, 1, 2);
        grid.add(schemaStatsLabel, 2, 2, 2, 1);

        card.getChildren().addAll(headerBox, filePathLabel, grid);
        return card;
    }

    private Node createSegmentedBar() {
        HBox bar = new HBox(4);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("segmented");

        sectionToggleGroup = new ToggleGroup();

        fieldsBtn = new ToggleButton("Fields");
        fieldsBtn.setToggleGroup(sectionToggleGroup);
        fieldsBtn.setOnAction(_ -> selectSection("fields"));

        schemaBtn = new ToggleButton("Schema");
        schemaBtn.setToggleGroup(sectionToggleGroup);
        schemaBtn.setOnAction(_ -> selectSection("schema"));

        metadataBtn = new ToggleButton("Metadata");
        metadataBtn.setToggleGroup(sectionToggleGroup);
        metadataBtn.setOnAction(_ -> selectSection("metadata"));

        bar.getChildren().addAll(fieldsBtn, schemaBtn, metadataBtn);
        return bar;
    }

    private void selectSection(String section) {
        this.lastSelectedSection = section;
        switch (section) {
            case "schema" -> {
                schemaBtn.setSelected(true);
                contentPane.setCenter(schemaView);
            }
            case "metadata" -> {
                metadataBtn.setSelected(true);
                contentPane.setCenter(metadataView);
            }
            default -> {
                fieldsBtn.setSelected(true);
                contentPane.setCenter(fieldsView);
            }
        }
    }

    // ---------------- Section 1: Fields Tree & Rules ----------------

    private void createFieldsView() {
        // Left: Filter input + TreeTableView
        VBox leftBox = new VBox(8);
        fieldsFilterField = new TextField();
        fieldsFilterField.setPromptText("🔎 Filter fields…");
        fieldsFilterField.textProperty().addListener((_, _, newVal) -> filterFields(newVal));

        fieldsTreeTable = new TreeTableView<>();
        fieldsTreeTable.setShowRoot(false);
        fieldsTreeTable.getStyleClass().add("tree-table-view");
        VBox.setVgrow(fieldsTreeTable, Priority.ALWAYS);

        TreeTableColumn<SchemaNode, String> nameCol = new TreeTableColumn<>("Name");
        nameCol.setPrefWidth(220);
        nameCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().name()));

        TreeTableColumn<SchemaNode, String> typeCol = new TreeTableColumn<>("Type");
        typeCol.setPrefWidth(160);
        typeCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().typeDisplay()));

        TreeTableColumn<SchemaNode, String> reqCol = new TreeTableColumn<>("Required");
        reqCol.setPrefWidth(85);
        reqCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().nullable() ? "No" : "Yes"));

        TreeTableColumn<SchemaNode, String> defCol = new TreeTableColumn<>("Default");
        defCol.setPrefWidth(90);
        defCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(
                param.getValue().getValue().defaultValue() != null ? param.getValue().getValue().defaultValue() : "—"));

        fieldsTreeTable.getColumns().addAll(List.of(nameCol, typeCol, reqCol, defCol));

        fieldsTreeTable.getSelectionModel().selectedItemProperty().addListener((_, _, newSelection) -> {
            if (newSelection != null && newSelection.getValue() != null) {
                displayNodeDetails(newSelection.getValue());
            } else {
                clearNodeDetails();
            }
        });

        leftBox.getChildren().addAll(fieldsFilterField, fieldsTreeTable);

        // Right: Rules Details Panel
        rulesDetailPanel = new VBox(10);
        rulesDetailPanel.setPrefWidth(280);
        rulesDetailPanel.setMinWidth(240);
        rulesDetailPanel.getStyleClass().add("card");
        rulesDetailPanel.setPadding(new Insets(12));

        Label detailsTitle = new Label("Field Rules & Details");
        detailsTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");

        rulePathLabel = new Label("Select a field");
        rulePathLabel.setStyle("-fx-font-weight: 600;");
        rulePathLabel.setWrapText(true);

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(8);
        detailsGrid.setVgap(4);

        ruleTypeLabel = new Label("—");
        ruleTypeLabel.setWrapText(true);
        ruleRequiredLabel = new Label("—");
        ruleDefaultLabel = new Label("—");
        ruleDocLabel = new Label("—");
        ruleDocLabel.setWrapText(true);

        detailsGrid.add(createMutedLabel("Type:"), 0, 0);
        detailsGrid.add(ruleTypeLabel, 1, 0);
        detailsGrid.add(createMutedLabel("Required:"), 0, 1);
        detailsGrid.add(ruleRequiredLabel, 1, 1);
        detailsGrid.add(createMutedLabel("Default:"), 0, 2);
        detailsGrid.add(ruleDefaultLabel, 1, 2);
        detailsGrid.add(createMutedLabel("Doc:"), 0, 3);
        detailsGrid.add(ruleDocLabel, 1, 3);

        Label rulesHeader = new Label("Rules");
        rulesHeader.setStyle("-fx-font-weight: 600; -fx-font-size: 11px;");
        rulesHeader.getStyleClass().add("muted");

        rulesListContainer = new VBox(4);
        VBox.setVgrow(rulesListContainer, Priority.ALWAYS);

        // Action buttons
        HBox actionsBox = new HBox(8);
        actionsBox.setAlignment(Pos.CENTER_LEFT);

        copyFieldPathBtn = new Button("Copy path");
        copyFieldPathBtn.getStyleClass().add("btn");
        copyFieldPathBtn.setDisable(true);
        copyFieldPathBtn.setOnAction(_ -> {
            TreeItem<SchemaNode> sel = fieldsTreeTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue() != null) {
                copyToClipboard(sel.getValue().path(), copyFieldPathBtn, "Copied!");
            }
        });

        filterByThisBtn = new Button("Filter by this ▸");
        filterByThisBtn.getStyleClass().addAll("btn", "btn-primary");
        filterByThisBtn.setDisable(true);
        filterByThisBtn.setOnAction(_ -> {
            TreeItem<SchemaNode> sel = fieldsTreeTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue() != null && onFilterByPath != null) {
                onFilterByPath.accept(sel.getValue().path());
            }
        });

        actionsBox.getChildren().addAll(copyFieldPathBtn, filterByThisBtn);

        rulesDetailPanel.getChildren().addAll(detailsTitle, rulePathLabel, detailsGrid, new Separator(), rulesHeader, rulesListContainer, actionsBox);

        SplitPane splitPane = new SplitPane(leftBox, rulesDetailPanel);
        splitPane.setDividerPositions(0.65);
        fieldsView = splitPane;
    }

    private void updateFieldsView(AvroFileInfo info) {
        SchemaNode rootNode = SchemaCatalog.tree(info.schema());
        TreeItem<SchemaNode> rootItem = buildTreeItem(rootNode);
        fieldsTreeTable.setRoot(rootItem);
        fieldsFilterField.clear();
        clearNodeDetails();
    }

    private TreeItem<SchemaNode> buildTreeItem(SchemaNode node) {
        TreeItem<SchemaNode> item = new TreeItem<>(node);
        item.setExpanded(node.depth() <= 1);
        for (SchemaNode child : node.children()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private void filterFields(String query) {
        if (currentInfo == null) return;
        SchemaNode rootNode = SchemaCatalog.tree(currentInfo.schema());
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            fieldsTreeTable.setRoot(buildTreeItem(rootNode));
            return;
        }

        TreeItem<SchemaNode> filteredRoot = filterNodeRecursive(rootNode, q);
        if (filteredRoot != null) {
            filteredRoot.setExpanded(true);
            fieldsTreeTable.setRoot(filteredRoot);
        } else {
            fieldsTreeTable.setRoot(new TreeItem<>(new SchemaNode("No matches", "", 0, null, "", false, null, null, List.of(), List.of(), false, null)));
        }
    }

    private TreeItem<SchemaNode> filterNodeRecursive(SchemaNode node, String query) {
        boolean matchesSelf = node.name().toLowerCase().contains(query)
                || node.path().toLowerCase().contains(query)
                || node.typeDisplay().toLowerCase().contains(query);

        List<TreeItem<SchemaNode>> matchingChildren = new ArrayList<>();
        for (SchemaNode child : node.children()) {
            TreeItem<SchemaNode> filteredChild = filterNodeRecursive(child, query);
            if (filteredChild != null) {
                matchingChildren.add(filteredChild);
            }
        }

        if (matchesSelf || !matchingChildren.isEmpty()) {
            TreeItem<SchemaNode> item = new TreeItem<>(node);
            item.getChildren().addAll(matchingChildren);
            item.setExpanded(true);
            return item;
        }
        return null;
    }

    private void displayNodeDetails(SchemaNode node) {
        rulePathLabel.setText(node.path().isEmpty() ? node.name() : node.path());
        ruleTypeLabel.setText(node.typeDisplay());
        ruleRequiredLabel.setText(node.nullable() ? "No (nullable)" : "Yes (required)");
        ruleDefaultLabel.setText(node.defaultValue() != null ? node.defaultValue() : "null");
        ruleDocLabel.setText(node.doc() != null && !node.doc().isBlank() ? node.doc() : "—");

        rulesListContainer.getChildren().clear();
        if (node.rules().isEmpty()) {
            Label noRulesLabel = new Label("No specific constraints");
            noRulesLabel.getStyleClass().add("muted");
            rulesListContainer.getChildren().add(noRulesLabel);
        } else {
            for (FieldRule rule : node.rules()) {
                HBox ruleRow = new HBox(6);
                ruleRow.setAlignment(Pos.CENTER_LEFT);
                Label labelText = new Label(rule.label() + ":");
                labelText.setStyle("-fx-font-weight: 600; -fx-font-size: 11px;");
                labelText.getStyleClass().add("muted");

                Label valText = new Label(rule.value());
                valText.setStyle("-fx-font-size: 11px;");
                valText.setWrapText(true);

                ruleRow.getChildren().addAll(labelText, valText);
                rulesListContainer.getChildren().add(ruleRow);
            }
        }

        copyFieldPathBtn.setDisable(node.path().isEmpty());
        filterByThisBtn.setDisable(node.path().isEmpty());
    }

    private void clearNodeDetails() {
        rulePathLabel.setText("Select a field");
        ruleTypeLabel.setText("—");
        ruleRequiredLabel.setText("—");
        ruleDefaultLabel.setText("—");
        ruleDocLabel.setText("—");
        rulesListContainer.getChildren().clear();
        copyFieldPathBtn.setDisable(true);
        filterByThisBtn.setDisable(true);
    }

    // ---------------- Section 2: Schema JSON ----------------

    private void createSchemaView() {
        BorderPane pane = new BorderPane();

        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("topbar");

        schemaSearchField = new TextField();
        schemaSearchField.setPromptText("🔎 Find in schema…");
        schemaSearchField.setPrefWidth(180);
        schemaSearchField.setOnAction(_ -> searchInSchema());

        Button findNextBtn = new Button("Find next");
        findNextBtn.getStyleClass().add("btn");
        findNextBtn.setOnAction(_ -> searchInSchema());

        copySchemaBtn = new Button("Copy schema");
        copySchemaBtn.getStyleClass().add("btn");
        copySchemaBtn.setOnAction(_ -> {
            if (currentInfo != null) {
                copyToClipboard(currentInfo.schema().toString(true), copySchemaBtn, "Copied!");
            }
        });

        saveSchemaBtn = new Button("Save .avsc…");
        saveSchemaBtn.getStyleClass().add("btn");
        saveSchemaBtn.setOnAction(_ -> handleSaveSchema());

        toolBar.getItems().addAll(schemaSearchField, findNextBtn, new Separator(), copySchemaBtn, saveSchemaBtn);
        pane.setTop(toolBar);

        schemaTextArea = new TextArea();
        schemaTextArea.setEditable(false);
        schemaTextArea.setWrapText(false);
        schemaTextArea.getStyleClass().addAll("code-area", "text-field");

        pane.setCenter(schemaTextArea);
        schemaView = pane;
    }

    private void updateSchemaView(AvroFileInfo info) {
        schemaTextArea.setText(info.schema().toString(true));
        schemaSearchField.clear();
        lastSchemaSearchIndex = 0;
    }

    private void searchInSchema() {
        String query = schemaSearchField.getText();
        if (query == null || query.isBlank()) return;

        String text = schemaTextArea.getText();
        int found = text.toLowerCase().indexOf(query.toLowerCase(), lastSchemaSearchIndex);
        if (found < 0) {
            // wrap around
            found = text.toLowerCase().indexOf(query.toLowerCase(), 0);
        }

        if (found >= 0) {
            schemaTextArea.selectRange(found, found + query.length());
            lastSchemaSearchIndex = found + query.length();
        }
    }

    private void handleSaveSchema() {
        if (currentInfo == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Avro Schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Avro Schema (*.avsc)", "*.avsc"));
        String defaultName = currentInfo.fileName().replace(".avro", "") + ".avsc";
        chooser.setInitialFileName(defaultName);

        File target = chooser.showSaveDialog(stage);
        if (target != null) {
            try {
                String json = currentInfo.schema().toString(true);
                AtomicFileWriter.write(target.toPath(), tmp -> Files.writeString(tmp, json));
            } catch (Exception e) {
                ErrorAlert.showError("Failed to save schema file", e);
            }
        }
    }

    // ---------------- Section 3: Metadata Table ----------------

    private void createMetadataView() {
        BorderPane pane = new BorderPane();

        ToolBar toolBar = new ToolBar();
        toolBar.getStyleClass().add("topbar");

        showSystemKeysCheckBox = new CheckBox("Show system keys (avro.*)");
        showSystemKeysCheckBox.setSelected(false);
        showSystemKeysCheckBox.setOnAction(_ -> updateMetadataTableContent());

        toolBar.getItems().add(showSystemKeysCheckBox);
        pane.setTop(toolBar);

        metadataTable = new TableView<>();
        metadataTable.getStyleClass().add("table-view");

        TableColumn<Map.Entry<String, String>, String> keyCol = new TableColumn<>("Key");
        keyCol.setPrefWidth(220);
        keyCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getKey()));

        TableColumn<Map.Entry<String, String>, String> valCol = new TableColumn<>("Value");
        valCol.setPrefWidth(480);
        valCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));

        metadataTable.getColumns().addAll(List.of(keyCol, valCol));

        // Context menu to copy value
        ContextMenu menu = new ContextMenu();
        MenuItem copyKeyItem = new MenuItem("Copy Key");
        copyKeyItem.setOnAction(_ -> {
            Map.Entry<String, String> sel = metadataTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.getKey(), null, null);
        });
        MenuItem copyValItem = new MenuItem("Copy Value");
        copyValItem.setOnAction(_ -> {
            Map.Entry<String, String> sel = metadataTable.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.getValue(), null, null);
        });
        menu.getItems().addAll(copyKeyItem, copyValItem);
        metadataTable.setContextMenu(menu);

        pane.setCenter(metadataTable);
        metadataView = pane;
    }

    private void updateMetadataView(AvroFileInfo info) {
        updateMetadataTableContent();
    }

    private void updateMetadataTableContent() {
        if (currentInfo == null) return;
        boolean showSystem = showSystemKeysCheckBox.isSelected();

        List<Map.Entry<String, String>> items = new ArrayList<>();
        for (Map.Entry<String, String> e : currentInfo.metadata().entrySet()) {
            if (!showSystem && e.getKey().startsWith("avro.")) {
                continue;
            }
            items.add(e);
        }
        metadataTable.setItems(FXCollections.observableArrayList(items));
    }

    // ---------------- Overview & Async Row Counting ----------------

    private void updateOverview(AvroFileInfo info) {
        fileNameLabel.setText(info.fileName());
        filePathLabel.setText(info.path().toString());
        sizeLabel.setText(info.formattedSize());
        codecLabel.setText(info.codec());
        modifiedLabel.setText(PresentationFormatter.formatDateTime(info.lastModified().toInstant()));

        String sName = info.schema().getFullName();
        if (sName == null || sName.isBlank()) {
            sName = info.schema().getName();
        }
        schemaNameLabel.setText(sName != null ? sName : "Anonymous");

        var paths = SchemaCatalog.paths(info.schema());
        var tree = SchemaCatalog.tree(info.schema());
        int topLevel = tree.children().size();
        int total = paths.size();
        int maxDepth = paths.stream().mapToInt(SchemaNode::depth).max().orElse(1);

        schemaStatsLabel.setText(topLevel + " top-level fields · " + total + " total · max depth " + maxDepth);
    }

    private void triggerRowCount(Supplier<OptionalLong> knownCountSupplier, Callable<Long> countSupplier) {
        cancelCountingTask();

        OptionalLong known = knownCountSupplier.get();
        if (known.isPresent()) {
            rowsLabel.setText(PresentationFormatter.formatCount(known.getAsLong()));
            rowsSpinner.setVisible(false);
            return;
        }

        rowsLabel.setText("");
        rowsSpinner.setVisible(true);

        countingTask = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return countSupplier.call();
            }
        };

        countingTask.setOnSucceeded(_ -> {
            long count = countingTask.getValue();
            rowsLabel.setText(PresentationFormatter.formatCount(count));
            rowsSpinner.setVisible(false);
        });

        countingTask.setOnFailed(_ -> {
            rowsLabel.setText("Unknown");
            rowsSpinner.setVisible(false);
        });

        Thread t = new Thread(countingTask, "file-info-row-counter");
        t.setDaemon(true);
        t.start();
    }

    private void cancelCountingTask() {
        if (countingTask != null && !countingTask.isDone()) {
            countingTask.cancel(true);
            countingTask = null;
        }
    }

    // ---------------- Utilities ----------------

    private Label createMutedLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }

    private void copyToClipboard(String text, Button triggerBtn, String feedbackText) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        if (triggerBtn != null && feedbackText != null) {
            String original = triggerBtn.getText();
            triggerBtn.setText(feedbackText);
            triggerBtn.setDisable(true);
            PauseTransition pause = new PauseTransition(Duration.seconds(1.2));
            pause.setOnFinished(_ -> {
                triggerBtn.setText(original);
                triggerBtn.setDisable(false);
            });
            pause.play();
        }
    }
}

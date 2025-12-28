package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.common.Page;
import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.ExportService;
import com.dkostin.avro_viewer.app.data.SearchResult;
import com.dkostin.avro_viewer.app.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.filter.FilterRowModel;
import com.dkostin.avro_viewer.app.filter.MatchOperation;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.ErrorAlert.showError;
import static com.dkostin.avro_viewer.app.ui.Theme.DARK;
import static com.dkostin.avro_viewer.app.ui.Theme.LIGHT;

public class MainController {

    @FXML
    private TextField maxResultsField;
    @FXML
    private ToggleButton themeToggle;

    @FXML
    private ComboBox<Integer> pageSizeCombo;

    @FXML
    private VBox filtersBox;
    @FXML
    private Label resultsLabel;

    @FXML
    private Button prevBtn;
    @FXML
    private Button nextBtn;

    @FXML
    private Label pageLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private TableView<Map<String, Object>> table;

    private final List<FilterRowModel> filterRows = new ArrayList<>();
    private final List<FilterRowView> filterRowViews = new ArrayList<>();

    private Scene scene;

    private final AvroFileService avroFileService;
    private final ExportService exportService;
    private final ViewerState state;

    private final JsonRowViewerWindow jsonRowViewerWindow;

    public MainController(AppContext ctx) {
        avroFileService = ctx.avroFileService();
        exportService = ctx.exportService();
        state = ctx.viewerState();
        jsonRowViewerWindow = ctx.jsonWindow();
    }

    public void initTheme(Scene scene) {
        this.scene = scene;
        // dark by default
        themeToggle.setSelected(true);
        themeToggle.setTooltip(new Tooltip("Toggle Light/Dark theme"));
        jsonRowViewerWindow.syncStyles(scene.getStylesheets());
    }

    @FXML
    private void initialize() {
        initPageSize();
        initTableActions();

        state.setPageSize(pageSizeCombo.getValue());

        updatePagingButtons();
        resetFiltersUi();

        maxResultsField.textProperty().addListener((_, _, newV) -> {
            if (newV != null && !newV.matches("\\d*")) {
                maxResultsField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });
    }

    private int getMaxResultsOrDefault() {
        String s = maxResultsField.getText();
        if (s == null || s.isBlank()) return 500;
        int v = Integer.parseInt(s);
        return Math.max(1, v);
    }

    private void resetFiltersUi() {
        filtersBox.getChildren().clear();
        filterRows.clear();
        filterRowViews.clear();

        addFilterRow(); // always at least one
        resultsLabel.setText("Active: (none)");
    }

    @FXML
    public void onAddFilter(ActionEvent e) {
        addFilterRow();
    }

    private void addFilterRow() {
        FilterRowModel model = new FilterRowModel();
        filterRows.add(model);

        FilterRowView view = createFilterRow(model);
        filterRowViews.add(view);
        filtersBox.getChildren().add(view.root());
    }

//    @FXML
//    public void onRemoveFilter(ActionEvent e) {
//        Button btn = (Button) e.getSource();
//        // parent = HBox row
//        var row = btn.getParent();
//        filtersBox.getChildren().remove(row);
//
//        // should be more than 1 row
//        if (filtersBox.getChildren().isEmpty()) {
//            addFilterRow();
//        }
//    }

    private void removeFilterRow(FilterRowView view) {
        filtersBox.getChildren().remove(view.root());
        filterRowViews.remove(view);
        filterRows.remove(view.model());

        if (filterRowViews.isEmpty()) {
            addFilterRow();
        }
    }

    private FilterRowView createFilterRow(FilterRowModel model) {
        ComboBox<String> fieldCombo = new ComboBox<>();
        fieldCombo.setPromptText("Field");
        fieldCombo.setPrefWidth(220);
        fieldCombo.setItems(FXCollections.observableArrayList(getAvailableFields()));

        ComboBox<MatchOperation> opCombo = new ComboBox<>();
        opCombo.setPromptText("Condition");
        opCombo.setPrefWidth(180);
        opCombo.setItems(FXCollections.observableArrayList(MatchOperation.values()));
        opCombo.setValue(MatchOperation.CONTAINS);

        TextField valueField = new TextField();
        valueField.setPromptText("Value (use 'null')");
        HBox.setHgrow(valueField, Priority.ALWAYS);

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().addAll("btn", "btn-icon");

        HBox row = new HBox(10, fieldCombo, opCombo, valueField, removeBtn);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- UI -> model
        fieldCombo.valueProperty().addListener((_, _, v) -> model.setField(v));
        opCombo.valueProperty().addListener((_, _, v) -> model.setOp(v));
        valueField.textProperty().addListener((_, _, v) -> model.setValue(v));

        // --- model defaults -> UI
        opCombo.setValue(model.getOp());

        // IS_NULL/NOT_NULL => value disabled
        opCombo.valueProperty().addListener((_, _, newV) -> {
            boolean needsNoValue = (newV == MatchOperation.IS_NULL || newV == MatchOperation.NOT_NULL);
            valueField.setDisable(needsNoValue);
            if (needsNoValue) valueField.clear();
        });

        FilterRowView view = new FilterRowView(row, fieldCombo, opCombo, valueField, removeBtn, model);

        removeBtn.setOnAction(_ -> removeFilterRow(view));

        return view;
    }

    private List<FilterCriterion> collectCriteria() {
        List<FilterCriterion> out = new ArrayList<>();

        for (FilterRowModel r : filterRows) {
            String field = r.getField();
            MatchOperation op = r.getOp();
            String value = r.getValue();

            if (field == null || field.isBlank() || op == null) {
                continue;
            }

            if (op == MatchOperation.IS_NULL || op == MatchOperation.NOT_NULL) {
                out.add(new FilterCriterion(field, op, null));
                continue;
            }

            if (value == null || value.isBlank()) {
                continue;
            }

            String trimmed = value.trim();
            String normalizedValue = trimmed.equalsIgnoreCase("null") ? null : trimmed;

            out.add(new FilterCriterion(field, op, normalizedValue));
        }

        return out;
    }

    private void initPageSize() {
        pageSizeCombo.setItems(FXCollections.observableArrayList(25, 50, 100, 200, 500));
        pageSizeCombo.setValue(50);
        pageSizeCombo.setOnAction(_ -> onPageSizeChanged());
    }

    private void initTableActions() {
        table.setRowFactory(_ -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    jsonRowViewerWindow.openJsonWindow(row.getItem(), table.getScene());
                }
            });
            return row;
        });

        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Map<String, Object> item = table.getSelectionModel().getSelectedItem();
                if (item != null) {
                    jsonRowViewerWindow.openJsonWindow(item, table.getScene());
                }
                e.consume();
            }
        });
    }

    @FXML
    private void onThemeToggle() {
        if (scene == null) {
            return;
        }

        scene.getStylesheets().clear();
        var selectedTheme = themeToggle.isSelected() ? DARK : LIGHT;
        scene.getStylesheets().add(getClass().getResource(selectedTheme.getCssPath()).toExternalForm());
        jsonRowViewerWindow.syncStyles(scene.getStylesheets());
    }

    // --- Actions (placeholders) ---
    @FXML
    private void onOpenFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open .avro file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Avro files (*.avro)", "*.avro"));

        File file = fc.showOpenDialog(table.getScene().getWindow());
        if (file == null) {
            return;
        }

        var prevFile = state.getFile();
        try {
            state.openFile(file.toPath());
            reloadCurrentPage();
        } catch (Exception ex) {
            state.openFile(prevFile);
            showError("Error to open file with path: " + file.toPath(), ex);
        }

    }

    @FXML
    private void onExportJson() {
        if (table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export to JSON");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        fc.setInitialFileName("export-page-" + (state.getPageIndex() + 1) + ".json");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        try {
            exportService.exportTableToJson(out.toPath(), table.getItems());
            statusLabel.setText("Exported JSON: " + out.getName());
        } catch (Exception ex) {
            showError("Export JSON failed", ex);
        }
    }

    @FXML
    private void onExportCsv() {
        if (table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export to CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        fc.setInitialFileName("export-page-" + (state.getPageIndex() + 1) + ".csv");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        try {
            // columns order from current schema (stable)
            exportService.exportTableToCsv(out.toPath(), table.getItems(), state.getSchema());
            statusLabel.setText("Exported CSV: " + out.getName());
        } catch (Exception ex) {
            showError("Export CSV failed", ex);
        }
    }

    @FXML
    private void onPrevPage() {
        if (state.getFile() == null) {
            return;
        }
        state.prevPage();
        reloadCurrentPage();
    }

    @FXML
    private void onNextPage() {
        if (state.getFile() == null) {
            return;
        }
        if (!state.isHasNext()) {
            return;
        }

        state.nextPage();
        reloadCurrentPage();
    }

    private void reloadCurrentPage() {
        if (state.getFile() == null) {
            return;
        }

        try {
            Page page = avroFileService.readPage(state.getFile(), state.getPageIndex(), state.getPageSize());

            if (state.getSchema() == null || !state.getSchema().equals(page.schema())) {
                state.setSchema(page.schema());
                refreshFieldCombos();
                rebuildColumns(page.schema());
            }

            table.setItems(toItems(page.records(), page.schema()));

            state.setHasNext(page.hasNext());
            pageLabel.setText("Page " + (state.getPageIndex() + 1));
            statusLabel.setText("Loaded " + page.records().size() + " records from " + state.getFile().getFileName());

            updatePagingButtons();
        } catch (Exception ex) {
            showError("Load page failed", ex);
        }
    }

    private void refreshFieldCombos() {
        var fields = FXCollections.observableArrayList(getAvailableFields());

        for (FilterRowView view : filterRowViews) {
            var combo = view.fieldCombo();
            var prev = combo.getValue();

            combo.setItems(fields);

            if (prev != null && fields.contains(prev)) {
                combo.setValue(prev);
            } else {
                combo.setValue(null);
                view.model().setField(null);
            }
        }
    }

    private void rebuildColumns(Schema schema) {
        table.getColumns().clear();

        for (Schema.Field f : schema.getFields()) {
            String name = f.name();

            TableColumn<Map<String, Object>, String> col = new TableColumn<>(name);
            col.setCellValueFactory(cell -> {
                Object v = cell.getValue().get(name);
                return new ReadOnlyStringWrapper(v == null ? "" : String.valueOf(v));
            });

            // For demo -> todo reimplement
            col.setPrefWidth(Math.max(120, name.length() * 12.0));
            table.getColumns().add(col);
        }
    }

    private Map<String, Object> recordToMap(GenericRecord r, Schema schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Schema.Field f : schema.getFields()) {
            m.put(f.name(), r.get(f.name()));
        }
        return m;
    }

    private void updatePagingButtons() {
        boolean noFile = state.getFile() == null;
        boolean search = state.isSearchMode();

        prevBtn.setDisable(noFile || search || state.getPageIndex() == 0);
        nextBtn.setDisable(noFile || search || !state.isHasNext());
    }

    private void onPageSizeChanged() {
        Integer newSize = pageSizeCombo.getValue();
        if (newSize == null) {
            return;
        }

        state.setPageSize(newSize);

        reloadCurrentPage();
    }

    private ObservableList<Map<String, Object>> toItems(List<GenericRecord> records, Schema schema) {
        var items = FXCollections.<Map<String, Object>>observableArrayList();
        for (GenericRecord r : records) {
            items.add(recordToMap(r, schema));
        }
        return items;
    }

    @FXML
    public void onApplyFilters(ActionEvent e) {
        if (state.getFile() == null) {
            statusLabel.setText("Open an .avro file first");
            return;
        }

        var criteria = collectCriteria();
        int max = getMaxResultsOrDefault();

        // UI -> SEARCH mode: off paging
        state.setSearch(criteria, max);
        updatePagingButtons();

        statusLabel.setText("Searching...");
        resultsLabel.setText("Searching...");

        var task = new Task<SearchResult>() {
            @Override
            protected SearchResult call() throws Exception {
                return avroFileService.search(state.getFile(), criteria, max);
            }
        };

        task.setOnSucceeded(_ -> {
            SearchResult r = task.getValue();

            // schema + columns
            if (state.getSchema() == null || !state.getSchema().equals(r.schema())) {
                state.setSchema(r.schema());
                refreshFieldCombos();
                rebuildColumns(r.schema());
            }

            table.setItems(toItems(r.records(), r.schema()));

            String tail = r.truncated() ? " (stopped by maxResults)" : "";
            resultsLabel.setText("Results: " + r.records().size() + tail);
            statusLabel.setText("Scanned: " + r.scanned() + ", matched: " + r.records().size() + tail);

            // paging disabled in search
            updatePagingButtons();
        });

        task.setOnFailed(_ -> {
            showError("Search failed", new Exception(task.getException()));
            statusLabel.setText("Search failed");
        });

        // run in separate executor
        new Thread(task, "avro-search").start();
    }

    @FXML
    public void onClearFilters(ActionEvent e) {
        resetFiltersUi();
        state.clearSearch();
        resultsLabel.setText("Active: (none)");
        reloadCurrentPage();
    }

    private List<String> getAvailableFields() {
        if (state.getSchema() == null) return List.of();
        return state.getSchema().getFields().stream()
                .map(Schema.Field::name)
                .toList();
    }
}


package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.common.Page;
import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.ExportService;
import com.dkostin.avro_viewer.app.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.filter.MatchOperation;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
        addFilterRow();              // 1 start row
        resultsLabel.setText("Active: (none)");
    }

    @FXML
    public void onAddFilter(ActionEvent e) {
        addFilterRow();
    }

    private void addFilterRow() {
        HBox row = createFilterRow();
        filtersBox.getChildren().add(row);
    }

    private HBox createFilterRow() {
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
        removeBtn.getStyleClass().addAll("btn", "btn-icon"); // додаси CSS
        removeBtn.setOnAction(this::onRemoveFilter);

        rowSetUserData(removeBtn, fieldCombo, opCombo, valueField);

        // IS_NULL/NOT_NULL => value disabled
        opCombo.valueProperty().addListener((_, _, newV) -> {
            boolean needsNoValue = (newV == MatchOperation.IS_NULL || newV == MatchOperation.NOT_NULL);
            valueField.setDisable(needsNoValue);
            if (needsNoValue) valueField.clear();
        });

        HBox row = new HBox(10, fieldCombo, opCombo, valueField, removeBtn);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private List<FilterCriterion> collectCriteria() {
        List<FilterCriterion> out = new java.util.ArrayList<>();

        for (var node : filtersBox.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            var children = row.getChildren();
            if (children.size() < 4) continue;

            Button removeBtn = (Button) children.getLast();
            FilterRowRefs refs = (FilterRowRefs) removeBtn.getUserData();

            String field = refs.fieldCombo().getValue();
            MatchOperation op = refs.opCombo().getValue();
            String value = refs.valueField().getText();

            if (field == null || op == null) continue;

            if (op == MatchOperation.IS_NULL || op == MatchOperation.NOT_NULL) {
                out.add(new FilterCriterion(field, op, null));
                continue;
            }

            if (value == null || value.isBlank()) {
                continue;
            }

            out.add(new FilterCriterion(field, op, value.trim()));
        }

        return out;
    }

    private void rowSetUserData(
            Button removeBtn,
            ComboBox<String> fieldCombo,
            ComboBox<MatchOperation> opCombo,
            TextField valueField
    ) {
        removeBtn.setUserData(new FilterRowRefs(fieldCombo, opCombo, valueField));
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

        for (var node : filtersBox.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            var children = row.getChildren();
            if (children.isEmpty()) continue;

            ComboBox<String> fieldCombo = (ComboBox<String>) children.get(0);
            var prev = fieldCombo.getValue();
            fieldCombo.setItems(fields);
            // якщо попереднє поле ще існує — залишаємо
            if (prev != null && fields.contains(prev)) fieldCombo.setValue(prev);
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
        prevBtn.setDisable(noFile || state.getPageIndex() == 0);
        nextBtn.setDisable(noFile || !state.isHasNext());
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
        state.resetToFirstPage();

        // TODO: state.setCriteria(criteria) коли додаси в ViewerState
        resultsLabel.setText(criteria.isEmpty()
                ? "No active filters"
                : ("Active filters: " + criteria.size() + " (AND)"));

        // поки просто reload (пізніше передаси criteria у service)
        reloadCurrentPage();
    }

    @FXML
    public void onClearFilters(ActionEvent e) {
        resetFiltersUi();
        state.resetToFirstPage();
        resultsLabel.setText("No active filters");
        reloadCurrentPage(); // якщо file==null — твій метод і так повертає
    }

    private List<String> getAvailableFields() {
        if (state.getSchema() == null) return List.of();
        return state.getSchema().getFields().stream()
                .map(Schema.Field::name)
                .toList();
    }

    @FXML
    public void onRemoveFilter(ActionEvent e) {
        Button btn = (Button) e.getSource();
        // parent = HBox row
        var row = btn.getParent();
        filtersBox.getChildren().remove(row);

        // не даємо зробити 0 рядків
        if (filtersBox.getChildren().isEmpty()) {
            addFilterRow();
        }
    }
}


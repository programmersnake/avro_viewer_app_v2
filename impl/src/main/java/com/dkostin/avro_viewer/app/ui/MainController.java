package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.common.Page;
import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.AvroFileServiceImpl;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.Theme.DARK;
import static com.dkostin.avro_viewer.app.ui.Theme.LIGHT;

public class MainController {

    @FXML
    private ToggleButton themeToggle;

    @FXML
    private ComboBox<Integer> pageSizeCombo;
    @FXML
    private Label pageSizeValue;
    @FXML
    private ComboBox<String> operatorCombo;
    @FXML
    private TextField searchField;
    @FXML
    private TextField maxResultsField;

    @FXML
    private Button prevBtn;
    @FXML
    private Button nextBtn;

    @FXML
    private Label activeFiltersLabel;
    @FXML
    private Label pageLabel;
    @FXML
    private Label statusLabel;

    @FXML
    private TableView<Map<String, Object>> table;

    private Scene scene;

    private final ViewerState state = new ViewerState();

    private final AvroFileService avroFileService = new AvroFileServiceImpl();

    public void initTheme(Scene scene) {
        this.scene = scene;
        // dark by default
        themeToggle.setSelected(true);
        themeToggle.setTooltip(new Tooltip("Toggle Light/Dark theme"));
    }

    @FXML
    private void initialize() {
        // filters controls
        pageSizeCombo.setItems(FXCollections.observableArrayList(25, 50, 100, 200, 500));
        pageSizeCombo.setValue(50);
        pageSizeValue.setText(String.valueOf(pageSizeCombo.getValue()));
        pageSizeCombo.setOnAction(_ -> onPageSizeChanged());

        state.setPageSize(pageSizeCombo.getValue());

        operatorCombo.setItems(FXCollections.observableArrayList("(all)", "AND", "OR"));
        operatorCombo.setValue("(all)");

        maxResultsField.setText("500");

        updatePagingButtons();
    }

    @FXML
    private void onThemeToggle() {
        if (scene == null) return;

        scene.getStylesheets().clear();
        var selectedTheme = themeToggle.isSelected() ? DARK : LIGHT;
        scene.getStylesheets().add(getClass().getResource(selectedTheme.getCssPath()).toExternalForm());
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

        state.openFile(file.toPath());
        reloadCurrentPage();
    }

    @FXML
    private void onExportJson() {
        statusLabel.setText("Export JSON clicked");
    }

    @FXML
    private void onExportCsv() {
        statusLabel.setText("Export CSV clicked");
    }

    @FXML
    private void onAddFilter() {
        activeFiltersLabel.setText("Active: (add filter clicked)");
    }

    @FXML
    private void onApplyFilters() {
        activeFiltersLabel.setText("Active: (apply filters clicked)");
    }

    @FXML
    private void onClearFilters() {
        activeFiltersLabel.setText("Active: (none)");
    }

    @FXML
    private void onRunSearch() {
        statusLabel.setText("Searching: '" + searchField.getText() + "', max=" + maxResultsField.getText());
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

    private void showError(String title, Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(ex.getClass().getSimpleName());
        a.setContentText(ex.getMessage());
        a.showAndWait();
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
        pageSizeValue.setText(String.valueOf(newSize));

        reloadCurrentPage();
    }

    private ObservableList<Map<String, Object>> toItems(List<GenericRecord> records, Schema schema) {
        var items = FXCollections.<Map<String, Object>>observableArrayList();
        for (GenericRecord r : records) {
            items.add(recordToMap(r, schema));
        }
        return items;
    }


}


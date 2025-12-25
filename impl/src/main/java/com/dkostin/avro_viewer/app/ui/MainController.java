package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.common.Page;
import com.dkostin.avro_viewer.app.data.AvroFileService;
import com.dkostin.avro_viewer.app.data.AvroFileServiceImpl;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    private Path currentFile;
    private Schema currentSchema;

    private final AtomicInteger pageIndex = new AtomicInteger();
    private final AtomicInteger pageSize = new AtomicInteger(50); // можеш підвʼязати до pageSizeCombo

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

        operatorCombo.setItems(FXCollections.observableArrayList("(all)", "AND", "OR"));
        operatorCombo.setValue("(all)");

        maxResultsField.setText("500");

        disablePrevBtnState();
        disableNextBtnState();
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

        File f = fc.showOpenDialog(table.getScene().getWindow());
        if (f == null) return;

        currentFile = f.toPath();
        pageIndex.set(0);

        try {
            loadPage(0);
            statusLabel.setText("Opened: " + f.getName());
        } catch (Exception ex) {
            statusLabel.setText("Failed to open: " + f.getName());
            showError("Open Avro failed", ex);
        }

        statusLabel.setText("Open file clicked");
        disablePrevBtnState();
        enableNextBtnState();
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
        if (currentFile == null) {
            return;
        }
        if (pageIndex.get() <= 1) {
            disablePrevBtnState();
        }

        pageIndex.decrementAndGet();
        try {
            enableNextBtnState();
            loadPage(pageIndex.get());
        } catch (Exception ex) {
            pageIndex.set(Math.max(0, pageIndex.incrementAndGet()));
            showError("Prev page failed", ex);
        }
    }

    @FXML
    private void onNextPage() {
        if (currentFile == null) return;
        pageIndex.incrementAndGet();

        enablePrevBtnState();

        try {
            boolean hasData = loadPage(pageIndex.get());

            if (!hasData) {
                disableNextBtnState();
            }
        } catch (Exception ex) {
            pageIndex.set(Math.max(0, pageIndex.decrementAndGet()));
            showError("Next page failed", ex);
        }
    }

    private boolean loadPage(int idx) throws Exception {
        Page page = avroFileService.readPage(currentFile, idx, pageSize.get());

        if (!page.schema().equals(currentSchema)) {
            currentSchema = page.schema();
            rebuildColumns(currentSchema);
        }

        var items = FXCollections.<Map<String, Object>>observableArrayList();
        for (GenericRecord r : page.records()) {
            items.add(recordToMap(r, currentSchema));
        }

        table.setItems(items);
        pageLabel.setText("Page " + (idx + 1));
        statusLabel.setText("Loaded " + items.size() + " records from " + currentFile.getFileName());

        return !items.isEmpty();
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

    private void enablePrevBtnState() {
        if (prevBtn.isDisabled()) {
            prevBtn.setDisable(false);
        }
    }

    private void enableNextBtnState() {
        if (nextBtn.isDisabled()) {
            nextBtn.setDisable(false);
        }
    }

    private void disablePrevBtnState() {
        if (!prevBtn.isDisabled()) {
            prevBtn.setDisable(true);
        }
    }

    private void disableNextBtnState() {
        if (!nextBtn.isDisabled()) {
            nextBtn.setDisable(true);
        }
    }

    private void onPageSizeChanged() {
        Integer newSize = pageSizeCombo.getValue();

        pageSize.set(newSize);
        pageSizeValue.setText(String.valueOf(newSize));

        // reset paging state
        pageIndex.set(0);
        // update states for prevBtn/nextBtn
        disablePrevBtnState();
        enableNextBtnState();

        // if file not opened yet - just update ui and that is all
        if (currentFile == null) {
            pageLabel.setText("Page 1"); // todo -> it potentially should be in another place (Solid responsibility)
            statusLabel.setText("Page size set to " + newSize);
            return;
        }

        // reread from 1 page
        try {
            boolean hasData = loadPage(0);
            // if hasData=false — means file is empty or filter "ate" everything
        } catch (Exception ex) {
            showError("Reload failed after page size change", ex);
        }
    }

}


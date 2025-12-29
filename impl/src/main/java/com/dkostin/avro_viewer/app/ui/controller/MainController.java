package com.dkostin.avro_viewer.app.ui.controller;

import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.data.SearchResult;
import com.dkostin.avro_viewer.app.domain.Page;
import com.dkostin.avro_viewer.app.domain.Theme;
import com.dkostin.avro_viewer.app.domain.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.logic.ViewerService;
import com.dkostin.avro_viewer.app.ui.component.ErrorAlert;
import com.dkostin.avro_viewer.app.ui.component.FiltersUi;
import com.dkostin.avro_viewer.app.ui.component.JsonRowViewerWindow;
import com.dkostin.avro_viewer.app.ui.component.TableViewPresenter;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.NumberStringConverter;
import org.apache.avro.Schema;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * MainController:
 * - UI orchestration only
 * - Filters UI delegating to FiltersUi
 * - Table setup delegating to TableViewPresenter
 * - Data/State operations delegating to ViewerService
 */
public class MainController {

    // ---- FXML ----
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

    // ---- Dependencies / Components ----
    private final ViewerService viewerService;
    private final JsonRowViewerWindow jsonRowViewerWindow;

    private FiltersUi filtersUi;
    private TableViewPresenter tablePresenter;

    // ---- Runtime state ----
    private Scene scene;
    private Task<?> activeSearchTask;

    public MainController(AppContext ctx) {
        this.viewerService = ctx.viewerService();
        this.jsonRowViewerWindow = ctx.jsonWindow();
    }

    /**
     * Called from Main.start(...) after FXMLLoader.load(), once Scene exists.
     */
    public void initTheme(Scene scene) {
        this.scene = scene;

        // default: dark
        themeToggle.setSelected(true);
        themeToggle.setTooltip(new Tooltip("Toggle Light/Dark theme"));

        applyTheme(themeToggle.isSelected());
    }

    @FXML
    private void initialize() {
        // Components
        this.filtersUi = new FiltersUi(filtersBox);
        this.tablePresenter = new TableViewPresenter(table, jsonRowViewerWindow);

        // Initial UI
        initPageSizeCombo();
        filtersUi.clearFilters();

        bindMaxResultsField();
        resultsLabel.setText("Active: (none)");
        pageLabel.setText("Page 1");
        statusLabel.setText("");

        updatePagingButtons();
    }

    // ---------------------------
    // Bindings / UI init
    // ---------------------------

    private void initPageSizeCombo() {
        pageSizeCombo.getItems().setAll(25, 50, 100, 200, 500);

        // prefer state value if present
        pageSizeCombo.setValue(viewerService.getPageSize());

        // keep service in sync
        pageSizeCombo.setOnAction(_ -> onPageSizeChanged());
    }

    private void bindMaxResultsField() {
        // Set initial value (service/state should provide a sane default, e.g., 500)
        int initial = viewerService.maxResultsProperty().get();
        if (initial <= 0) {
            viewerService.maxResultsProperty().set(500);
        }

        // Only digits in textfield (keeps UX predictable)
        maxResultsField.textProperty().addListener((_, _, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                maxResultsField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });

        // Bidirectional binding (NumberStringConverter handles parse/format)
        // NOTE: because we also sanitize digits above, parsing stays safe.
        maxResultsField.textProperty().bindBidirectional(
                viewerService.maxResultsProperty(),
                new NumberStringConverter()
        );
    }

    // ---------------------------
    // Theme
    // ---------------------------

    @FXML
    private void onThemeToggle() {
        if (scene == null) return;
        applyTheme(themeToggle.isSelected());
    }

    private void applyTheme(boolean darkSelected) {
        if (scene == null) return;

        scene.getStylesheets().clear();
        Theme t = darkSelected ? Theme.DARK : Theme.LIGHT;
        var css = getClass().getResource(t.getCssPath());
        if (css != null) {
            scene.getStylesheets().setAll(
                    getClass().getResource(Theme.BASE.getCssPath()).toExternalForm(),
                    css.toExternalForm()
            );
        }

        // propagate to JSON window
        jsonRowViewerWindow.syncStyles(scene.getStylesheets());
    }

    // ---------------------------
    // File
    // ---------------------------

    @FXML
    private void onOpenFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open .avro file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Avro files (*.avro)", "*.avro"));

        File file = fc.showOpenDialog(table.getScene().getWindow());
        if (file == null) return;

        cancelActiveSearchIfRunning();

        try {
            Page page = viewerService.openFile(file.toPath());

            // schema -> filters & table
            Schema schema = page.schema();
            filtersUi.updateFieldOptions(schema);
            tablePresenter.updateTableData(page.records(), schema);

            // labels
            resultsLabel.setText("Active: (none)");
            pageLabel.setText("Page 1");
            statusLabel.setText("Opened: " + file.getName() + " (" + page.records().size() + " records)");

            // page size combo must reflect service/state
            pageSizeCombo.setValue(viewerService.getPageSize());

        } catch (Exception ex) {
            ErrorAlert.showError("Error opening file: " + file.getPath(), ex);
            statusLabel.setText("Failed to open file");
        } finally {
            updatePagingButtons();
        }
    }

    // ---------------------------
    // Filters
    // ---------------------------

    @FXML
    private void onAddFilter(ActionEvent e) {
        filtersUi.addFilterRow();
    }

    @FXML
    private void onApplyFilters(ActionEvent e) {
        if (!viewerService.isFileOpen()) {
            statusLabel.setText("Open an .avro file first");
            return;
        }

        cancelActiveSearchIfRunning();

        List<FilterCriterion> criteria = filtersUi.getFilterCriteria();
        int max = safeMaxResults();

        // UX
        statusLabel.setText("Searching...");
        resultsLabel.setText("Searching...");

        Task<SearchResult> task = new Task<>() {
            @Override
            protected SearchResult call() throws Exception {
                return viewerService.search(criteria, max);
            }
        };

        activeSearchTask = task;

        task.setOnSucceeded(_ -> {
            // prevent stale task updating UI after newer one started
            if (activeSearchTask != task) return;

            SearchResult result = task.getValue();
            tablePresenter.updateTableData(result.records(), result.schema());

            String tail = result.truncated() ? " (stopped by maxResults)" : "";
            resultsLabel.setText("Results: " + result.records().size() + tail);
            statusLabel.setText("Scanned: " + result.scanned() + ", matched: " + result.records().size() + tail);

            pageLabel.setText("Search"); // optional, makes mode obvious
            updatePagingButtons();
        });

        task.setOnFailed(evt -> {
            if (activeSearchTask != task) return;

            Throwable err = task.getException();
            ErrorAlert.showError("Search failed", err);
            statusLabel.setText("Search failed");
            resultsLabel.setText("Search failed");
            updatePagingButtons();
        });

        Thread t = new Thread(task, "avro-search");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onClearFilters(ActionEvent e) {
        cancelActiveSearchIfRunning();

        filtersUi.clearFilters();
        resultsLabel.setText("Active: (none)");

        if (!viewerService.isFileOpen()) {
            statusLabel.setText("");
            pageLabel.setText("Page 1");
            updatePagingButtons();
            return;
        }

        try {
            Page page = viewerService.clearSearch();
            if (page != null) {
                tablePresenter.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            } else {
                statusLabel.setText("");
            }
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to reload after clearing filters", ex);
            statusLabel.setText("Failed to reload");
        } finally {
            updatePagingButtons();
        }
    }

    // ---------------------------
    // Paging
    // ---------------------------

    @FXML
    private void onPrevPage() {
        if (!viewerService.isFileOpen()) return;
        if (viewerService.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        try {
            Page page = viewerService.prevPage();
            if (page != null) {
                tablePresenter.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (viewerService.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (viewerService.getPageIndex() + 1) + ")");
            }
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to load previous page", ex);
            statusLabel.setText("Failed to load previous page");
        } finally {
            updatePagingButtons();
        }
    }

    @FXML
    private void onNextPage() {
        if (!viewerService.isFileOpen()) return;
        if (viewerService.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        try {
            Page page = viewerService.nextPage();
            if (page != null) {
                tablePresenter.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (viewerService.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (viewerService.getPageIndex() + 1) + ")");
            }
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to load next page", ex);
            statusLabel.setText("Failed to load next page");
        } finally {
            updatePagingButtons();
        }
    }

    private void onPageSizeChanged() {
        if (!viewerService.isFileOpen()) {
            // still update service default for next open (optional)
            Integer ps = pageSizeCombo.getValue();
            if (ps != null) {
                viewerService.setPageSize(ps);
            }
            return;
        }

        cancelActiveSearchIfRunning();

        Integer newSize = pageSizeCombo.getValue();
        if (newSize == null) return;

        try {
            Page page = viewerService.changePageSize(newSize);
            if (page != null) {
                tablePresenter.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            }
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to change page size", ex);
            statusLabel.setText("Failed to change page size");
        } finally {
            updatePagingButtons();
        }
    }

    // ---------------------------
    // Export
    // ---------------------------

    @FXML
    private void onExportJson() {
        if (table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Export to JSON");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        fc.setInitialFileName("export-" + exportNameSuffix() + ".json");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        try {
            viewerService.exportToJson(out.toPath(), table.getItems());
            statusLabel.setText("Exported JSON: " + out.getName());
        } catch (Exception ex) {
            ErrorAlert.showError("Export JSON failed", ex);
            statusLabel.setText("Export JSON failed");
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
        fc.setInitialFileName("export-" + exportNameSuffix() + ".csv");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        try {
            viewerService.exportToCsv(out.toPath(), table.getItems());
            statusLabel.setText("Exported CSV: " + out.getName());
        } catch (Exception ex) {
            ErrorAlert.showError("Export CSV failed", ex);
            statusLabel.setText("Export CSV failed");
        }
    }

    private String exportNameSuffix() {
        if (viewerService.isSearchMode()) {
            return "search";
        }
        return "page-" + (viewerService.getPageIndex() + 1);
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private void updatePagingButtons() {
        boolean noFile = !viewerService.isFileOpen();
        boolean searchMode = viewerService.isSearchMode();

        prevBtn.setDisable(noFile || searchMode || viewerService.getPageIndex() == 0);
        nextBtn.setDisable(noFile || searchMode || !viewerService.hasNextPage());
    }

    private int safeMaxResults() {
        // viewerService.maxResultsProperty() is bound, but users can delete text => empty => parse issues.
        // We normalize hard here.
        String s = maxResultsField.getText();
        if (s == null || s.isBlank()) {
            viewerService.maxResultsProperty().set(500);
            return 500;
        }
        try {
            int v = Integer.parseInt(s);
            v = Math.max(1, v);
            viewerService.maxResultsProperty().set(v);
            return v;
        } catch (NumberFormatException ex) {
            viewerService.maxResultsProperty().set(500);
            return 500;
        }
    }

    private void cancelActiveSearchIfRunning() {
        Task<?> task = activeSearchTask;
        if (task != null && task.isRunning()) {
            task.cancel(true);
        }
        activeSearchTask = null;
    }

    private static String safeSchemaName(Schema schema) {
        if (schema == null) {
            return "schema";
        }
        String name = schema.getName();
        return (name == null || name.isBlank()) ? "schema" : name;
    }
}

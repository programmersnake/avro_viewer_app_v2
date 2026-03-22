package com.dkostin.avro_viewer.app.ui.main;

import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.domain.model.Page;
import com.dkostin.avro_viewer.app.domain.model.SearchResult;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.service.api.ExportFacade;
import com.dkostin.avro_viewer.app.service.api.FileLoader;
import com.dkostin.avro_viewer.app.service.api.PageNavigator;
import com.dkostin.avro_viewer.app.service.api.SearchFacade;
import com.dkostin.avro_viewer.app.ui.Theme;
import com.dkostin.avro_viewer.app.ui.component.ErrorAlert;
import com.dkostin.avro_viewer.app.ui.component.FiltersUi;
import com.dkostin.avro_viewer.app.ui.component.RowViewWindow;
import com.dkostin.avro_viewer.app.ui.component.TableViewWindow;
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
 * - Data/State operations delegating to segregated service interfaces
 */
public class MainController {

    // ---- Dependencies (segregated interfaces) ----
    private final FileLoader fileLoader;
    private final PageNavigator pageNavigator;
    private final SearchFacade searchFacade;
    private final ExportFacade exportFacade;
    private final RowViewWindow rowViewWindow;

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
    private FiltersUi filtersUi;
    private TableViewWindow tableViewWindow;

    // ---- Runtime state ----
    private Scene scene;
    private Task<?> activeSearchTask;

    public MainController(AppContext ctx) {
        this.fileLoader = ctx.fileLoader();
        this.pageNavigator = ctx.pageNavigator();
        this.searchFacade = ctx.searchFacade();
        this.exportFacade = ctx.exportFacade();
        this.rowViewWindow = ctx.jsonWindow();
    }

    private static String safeSchemaName(Schema schema) {
        if (schema == null) {
            return "schema";
        }
        String name = schema.getName();
        return (name == null || name.isBlank()) ? "schema" : name;
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

    // ---------------------------
    // Bindings / UI init
    // ---------------------------

    @FXML
    private void initialize() {
        // Components
        this.filtersUi = new FiltersUi(filtersBox);
        this.tableViewWindow = new TableViewWindow(table, rowViewWindow);

        // Initial UI
        initPageSizeCombo();
        filtersUi.clearFilters();

        bindMaxResultsField();
        resultsLabel.setText("Active: (none)");
        pageLabel.setText("Page 1");
        statusLabel.setText("");

        updatePagingButtons();
    }

    private void initPageSizeCombo() {
        pageSizeCombo.getItems().setAll(25, 50, 100, 200, 500);

        // prefer state value if present
        pageSizeCombo.setValue(pageNavigator.getPageSize());

        // keep service in sync
        pageSizeCombo.setOnAction(_ -> onPageSizeChanged());
    }

    // ---------------------------
    // Theme
    // ---------------------------

    private void bindMaxResultsField() {
        // Set initial value (service/state should provide a sane default, e.g., 500)
        int initial = searchFacade.maxResultsProperty().get();
        if (initial <= 0) {
            searchFacade.maxResultsProperty().set(500);
        }

        // Only digits in textfield (keeps UX predictable)
        maxResultsField.textProperty().addListener((_, _, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                maxResultsField.setText(newV.replaceAll("[^\\d]", ""));
            }
        });

        // Bidirectional binding (NumberStringConverter handles parse/format)
        maxResultsField.textProperty().bindBidirectional(
                searchFacade.maxResultsProperty(),
                new NumberStringConverter()
        );
    }

    @FXML
    private void onThemeToggle() {
        if (scene == null) return;
        applyTheme(themeToggle.isSelected());
    }

    // ---------------------------
    // File
    // ---------------------------

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
        rowViewWindow.syncStyles(scene.getStylesheets());
    }

    // ---------------------------
    // Filters
    // ---------------------------

    @FXML
    private void onOpenFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open .avro file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Avro files (*.avro)", "*.avro"));

        File file = fc.showOpenDialog(table.getScene().getWindow());
        if (file == null) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Error opening file: " + file.getPath(), () -> {
            Page page = fileLoader.openFile(file.toPath());

            // schema -> filters & table
            Schema schema = page.schema();
            filtersUi.updateFieldOptions(schema);
            tableViewWindow.updateTableData(page.records(), schema);

            // labels
            resultsLabel.setText("Active: (none)");
            pageLabel.setText("Page 1");
            statusLabel.setText("Opened: " + file.getName() + " (" + page.records().size() + " records)");

            // page size combo must reflect service/state
            pageSizeCombo.setValue(pageNavigator.getPageSize());
        });
    }

    @FXML
    private void onAddFilter(ActionEvent e) {
        filtersUi.addFilterRow();
    }

    @FXML
    private void onApplyFilters(ActionEvent e) {
        if (!fileLoader.isFileOpen()) {
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
                return searchFacade.search(criteria, max);
            }
        };

        activeSearchTask = task;

        task.setOnSucceeded(_ -> {
            // prevent stale task updating UI after newer one started
            if (activeSearchTask != task) return;

            SearchResult result = task.getValue();
            tableViewWindow.updateSearchData(result.records(), result.schema());

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

    // ---------------------------
    // Paging
    // ---------------------------

    @FXML
    private void onClearFilters(ActionEvent e) {
        cancelActiveSearchIfRunning();

        filtersUi.clearFilters();
        resultsLabel.setText("Active: (none)");

        if (!fileLoader.isFileOpen()) {
            statusLabel.setText("");
            pageLabel.setText("Page 1");
            updatePagingButtons();
            return;
        }

        executeWithUiUpdate("Failed to reload after clearing filters", () -> {
            Page page = searchFacade.clearSearch();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            } else {
                statusLabel.setText("");
            }
        });
    }

    @FXML
    private void onPrevPage() {
        if (!fileLoader.isFileOpen()) return;
        if (searchFacade.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Failed to load previous page", () -> {
            Page page = pageNavigator.prevPage();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (pageNavigator.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (pageNavigator.getPageIndex() + 1) + ")");
            }
        });
    }

    @FXML
    private void onNextPage() {
        if (!fileLoader.isFileOpen()) return;
        if (searchFacade.isSearchMode()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Failed to load next page", () -> {
            Page page = pageNavigator.nextPage();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page " + (pageNavigator.getPageIndex() + 1));
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (pageNavigator.getPageIndex() + 1) + ")");
            }
        });
    }

    // ---------------------------
    // Export
    // ---------------------------

    private void onPageSizeChanged() {
        if (!fileLoader.isFileOpen()) {
            // still update service default for next open (optional)
            Integer ps = pageSizeCombo.getValue();
            if (ps != null) {
                pageNavigator.setPageSize(ps);
            }
            return;
        }

        cancelActiveSearchIfRunning();

        Integer newSize = pageSizeCombo.getValue();
        if (newSize == null) return;

        executeWithUiUpdate("Failed to change page size", () -> {
            Page page = pageNavigator.changePageSize(newSize);
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                pageLabel.setText("Page 1");
                statusLabel.setText("Loaded " + page.records().size() + " records from " + safeSchemaName(page.schema()));
            }
        });
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
        fc.setInitialFileName("export-" + exportNameSuffix() + ".json");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        executeWithUiUpdate("Export JSON failed", () -> {
            exportFacade.exportToJson(out.toPath(), table.getItems());
            statusLabel.setText("Exported JSON: " + out.getName());
        });
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

        executeWithUiUpdate("Export CSV failed", () -> {
            exportFacade.exportToCsv(out.toPath(), table.getItems());
            statusLabel.setText("Exported CSV: " + out.getName());
        });
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    /**
     * Template method that wraps common UI update boilerplate: try/catch, ErrorAlert, updatePagingButtons.
     */
    private void executeWithUiUpdate(String errorContext, UiAction action) {
        try {
            action.run();
        } catch (Exception ex) {
            ErrorAlert.showError(errorContext, ex);
            statusLabel.setText(errorContext);
        } finally {
            updatePagingButtons();
        }
    }

    private String exportNameSuffix() {
        if (searchFacade.isSearchMode()) {
            return "search";
        }
        return "page-" + (pageNavigator.getPageIndex() + 1);
    }

    private void updatePagingButtons() {
        boolean noFile = !fileLoader.isFileOpen();
        boolean searchMode = searchFacade.isSearchMode();

        prevBtn.setDisable(noFile || searchMode || pageNavigator.getPageIndex() == 0);
        nextBtn.setDisable(noFile || searchMode || !pageNavigator.hasNextPage());
    }

    private int safeMaxResults() {
        String s = maxResultsField.getText();
        if (s == null || s.isBlank()) {
            searchFacade.maxResultsProperty().set(500);
            return 500;
        }
        try {
            int v = Integer.parseInt(s);
            v = Math.max(1, v);
            searchFacade.maxResultsProperty().set(v);
            return v;
        } catch (NumberFormatException ex) {
            searchFacade.maxResultsProperty().set(500);
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

    /**
     * Functional interface for UI actions that may throw checked exceptions.
     */
    @FunctionalInterface
    private interface UiAction {
        void run() throws Exception;
    }
}


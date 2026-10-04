package com.dkostin.avro_viewer.app.ui.main;

import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.domain.model.*;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.AvroFileInfo;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterGroup;
import com.dkostin.avro_viewer.app.service.api.ExportFacade;
import com.dkostin.avro_viewer.app.service.api.FileLoader;
import com.dkostin.avro_viewer.app.service.api.PageNavigator;
import com.dkostin.avro_viewer.app.service.api.SearchFacade;
import com.dkostin.avro_viewer.app.ui.Theme;
import com.dkostin.avro_viewer.app.ui.component.*;
import com.dkostin.avro_viewer.app.util.PresentationFormatter;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.NumberStringConverter;
import org.apache.avro.Schema;

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MainController:
 * - UI orchestration only
 * - Filters UI delegating to FiltersUi
 * - Table setup delegating to TableViewWindow
 * - Data/State operations delegating to segregated service interfaces
 */
public class MainController {

    /**
     * Hard sanity maximum for search results. Prevents OOM from unbounded
     * materialization of deep HashMaps when users enter excessively large values.
     */
    private static final int MAX_RESULTS_LIMIT = 100_000;

    // ---- Dependencies (segregated interfaces) ----
    private final FileLoader fileLoader;
    private final PageNavigator pageNavigator;
    private final SearchFacade searchFacade;
    private final ExportFacade exportFacade;
    private final RowViewWindow rowViewWindow;

    // ---- FXML ----
    @FXML
    private MenuItem reloadMenuItem;
    @FXML
    private Button fileInfoBtn;
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
    @FXML
    private Button applyBtn;
    @FXML
    private ProgressBar searchProgressBar;
    private FiltersUi filtersUi;
    private TableViewWindow tableViewWindow;

    // ---- Controller-local UI state ----
    private final IntegerProperty maxResultsProperty = new SimpleIntegerProperty();
    private int lastSearchResultCount = -1;
    private Scene scene;
    private Task<?> activeSearchTask;
    private volatile SearchControl activeSearchControl;
    private ExportPreviewDialog exportPreviewDialog;
    private final FileInfoWindow fileInfoWindow = new FileInfoWindow();

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

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN),
                this::onFileInfo
        );

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

        updateControls();
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
        maxResultsProperty.set(searchFacade.getDefaultMaxResults());

        // Only digits in textfield + clamp to MAX_RESULTS_LIMIT
        maxResultsField.textProperty().addListener((_, _, newV) -> {
            if (newV == null) return;
            if (!newV.matches("\\d*")) {
                maxResultsField.setText(newV.replaceAll("[^\\d]", ""));
                return;
            }
            // Clamp excessively large values immediately on input
            if (!newV.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(newV);
                    if (parsed > MAX_RESULTS_LIMIT) {
                        maxResultsField.setText(String.valueOf(MAX_RESULTS_LIMIT));
                        statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
                    }
                } catch (NumberFormatException ignored) {
                    // Oversized numeric literal — clamp defensively
                    maxResultsField.setText(String.valueOf(MAX_RESULTS_LIMIT));
                    statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
                }
            }
        });

        // Bidirectional binding (NumberStringConverter handles parse/format)
        maxResultsField.textProperty().bindBidirectional(
                maxResultsProperty,
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
        fileInfoWindow.syncStyles(scene.getStylesheets());
    }

    // ---------------------------
    // File
    // ---------------------------

    @FXML
    private void onFileInfo() {
        if (!fileLoader.isFileOpen()) return;
        try {
            AvroFileInfo info = fileLoader.getFileInfo();
            Path current = fileLoader.getCurrentFile();
            fileInfoWindow.show(
                    table.getScene(),
                    info,
                    () -> pageNavigator.totalRecords(),
                    () -> fileLoader.countRecords(current),
                    path -> filtersUi.addFilterFor(path)
            );
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to display file info", ex);
        }
    }

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
            lastSearchResultCount = -1;
            resultsLabel.setText("Active: (none)");
            updatePageLabel();
            statusLabel.setText("Opened: " + file.getName() + " (" + page.records().size() + " records)");

            // page size combo must reflect service/state
            pageSizeCombo.setValue(pageNavigator.getPageSize());

            triggerBackgroundRecordCount();
        });
    }

    @FXML
    private void onReloadFile() {
        if (!fileLoader.isFileOpen()) return;

        cancelActiveSearchIfRunning();

        executeWithUiUpdate("Error reloading file", () -> {
            Page page = fileLoader.reloadFile();
            Schema schema = page.schema();
            filtersUi.updateFieldOptions(schema);
            tableViewWindow.updateTableData(page.records(), schema);

            lastSearchResultCount = -1;
            resultsLabel.setText("Active: (none)");
            updatePageLabel();
            Path current = fileLoader.getCurrentFile();
            String name = current != null ? current.getFileName().toString() : "file";
            statusLabel.setText("Reloaded: " + name + " (" + page.records().size() + " records)");

            triggerBackgroundRecordCount();
        });
    }

    @FXML
    private void onAddGroup(ActionEvent ignored) {
        filtersUi.addGroup();
    }

    @FXML
    private void onApplyFilters(ActionEvent ignored) {
        if (!fileLoader.isFileOpen()) {
            statusLabel.setText("Open an .avro file first");
            return;
        }

        if (activeSearchTask != null && activeSearchTask.isRunning()) {
            // User requested stop
            if (activeSearchControl != null) {
                activeSearchControl.stopRequested().set(true);
            }
            if (applyBtn != null) {
                applyBtn.setDisable(true);
            }
            unbindSearchProperties();
            statusLabel.setText("Stopping search...");
            return;
        }

        cancelActiveSearchIfRunning();

        List<FilterGroup> groups = filtersUi.getFilterGroups();
        int max = safeMaxResults();

        SearchRequest request;
        try {
            request = searchFacade.prepareSearch(groups, max);
        } catch (InvalidFilterException ex) {
            ErrorAlert.showError("Invalid filter criteria", ex);
            statusLabel.setText("Unknown field(s): " + String.join(", ", ex.getInvalidPaths()));
            return;
        } catch (Exception ex) {
            ErrorAlert.showError("Failed to prepare search", ex);
            statusLabel.setText("Search preparation failed");
            return;
        }

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        Task<SearchResult> task = new Task<>() {
            @Override
            protected SearchResult call() throws Exception {
                SearchControl searchControl = new SearchControl(stopRequested, progress -> {
                    if (progress.fraction() > 0) {
                        updateProgress(progress.fraction(), 1.0);
                    } else {
                        updateProgress(-1.0, 1.0);
                    }
                    String pct = progress.fraction() > 0
                            ? String.format(Locale.ROOT, " · %.0f%%", progress.fraction() * 100)
                            : "";
                    String msg = String.format(Locale.ROOT, "Scanned %s · matched %s%s",
                            PresentationFormatter.formatCount(progress.scanned()),
                            PresentationFormatter.formatCount(progress.matched()),
                            pct);
                    updateMessage(msg);
                });
                activeSearchControl = searchControl;
                return searchFacade.executeSearch(request, searchControl);
            }
        };

        activeSearchControl = new SearchControl(stopRequested, p -> {});
        activeSearchTask = task;

        if (applyBtn != null) {
            applyBtn.setText("■ Stop");
            applyBtn.getStyleClass().remove("btn-primary");
            if (!applyBtn.getStyleClass().contains("btn-danger")) {
                applyBtn.getStyleClass().add("btn-danger");
            }
            applyBtn.setDisable(false);
        }
        if (searchProgressBar != null) {
            searchProgressBar.setVisible(true);
            searchProgressBar.setManaged(true);
            searchProgressBar.progressProperty().bind(task.progressProperty());
        }
        statusLabel.textProperty().bind(task.messageProperty());
        resultsLabel.setText("Searching...");
        updateControls();

        task.setOnSucceeded(_ -> {
            unbindSearchProperties();
            resetSearchButtonAndProgress();

            if (activeSearchTask != task) {
                return;
            }
            activeSearchTask = null;

            SearchResult result = task.getValue();
            boolean committed = searchFacade.commitSearch(request, result);
            if (!committed) {
                // Stale task result (e.g. user opened a new file while search was running)
                renderCommittedState();
                return;
            }

            tableViewWindow.updateSearchData(result.records(), result.schema());

            lastSearchResultCount = result.records().size();
            String tail;
            if (result.stopReason() == StopReason.USER_STOPPED) {
                int pct = (int) Math.round(result.fractionScanned() * 100);
                tail = " (stopped by user" + (pct > 0 ? " at " + pct + "%" : "") + ")";
                statusLabel.setText("Stopped by user" + (pct > 0 ? " at " + pct + "%" : "") +
                        " — " + PresentationFormatter.formatCount(result.records().size()) + " matches (scanned " +
                        PresentationFormatter.formatCount(result.scanned()) + ")");
            } else if (result.stopReason() == StopReason.MAX_RESULTS) {
                tail = " (stopped by maxResults)";
                statusLabel.setText("Scanned: " + PresentationFormatter.formatCount(result.scanned()) +
                        ", matched: " + PresentationFormatter.formatCount(result.records().size()) + tail);
            } else {
                tail = "";
                statusLabel.setText("Scanned: " + PresentationFormatter.formatCount(result.scanned()) +
                        ", matched: " + PresentationFormatter.formatCount(result.records().size()));
            }

            resultsLabel.setText("Results: " + PresentationFormatter.formatCount(result.records().size()) + tail);
            pageLabel.setText("Search");
            updateControls();
        });

        task.setOnFailed(_ -> {
            unbindSearchProperties();
            resetSearchButtonAndProgress();

            if (activeSearchTask != task) {
                return;
            }
            activeSearchTask = null;

            Throwable err = task.getException();
            if (!(err instanceof InterruptedIOException) && !(err instanceof InterruptedException)) {
                ErrorAlert.showError("Search failed", err);
            }
            renderCommittedState();
        });

        task.setOnCancelled(_ -> {
            unbindSearchProperties();
            resetSearchButtonAndProgress();

            if (activeSearchTask != task) {
                return;
            }
            activeSearchTask = null;

            renderCommittedState();
        });

        Thread t = new Thread(task, "avro-search");
        t.setDaemon(true);
        t.start();
    }

    private void renderCommittedState() {
        if (!fileLoader.isFileOpen()) {
            resultsLabel.setText("Active: (none)");
            updatePageLabel();
            statusLabel.setText("");
        } else if (searchFacade.isSearchMode()) {
            updatePageLabel();
            resultsLabel.setText(lastSearchResultCount >= 0 ? "Results: " + lastSearchResultCount : "Search mode");
            statusLabel.setText("Search mode active");
        } else {
            updatePageLabel();
            resultsLabel.setText("Active: (none)");
            Path current = fileLoader.getCurrentFile();
            String name = current != null ? current.getFileName().toString() : "file";
            int count = table.getItems() != null ? table.getItems().size() : 0;
            statusLabel.setText("Loaded " + count + " records from " + name);
        }
        updateControls();
    }

    // ---------------------------
    // Paging
    // ---------------------------

    @FXML
    private void onClearFilters(ActionEvent ignored) {
        cancelActiveSearchIfRunning();

        filtersUi.clearFilters();
        lastSearchResultCount = -1;
        resultsLabel.setText("Active: (none)");
        maxResultsProperty.set(searchFacade.getDefaultMaxResults());

        if (!fileLoader.isFileOpen()) {
            statusLabel.setText("");
            updatePageLabel();
            updateControls();
            return;
        }

        executeWithUiUpdate("Failed to reload after clearing filters", () -> {
            Page page = searchFacade.clearSearch();
            if (page != null) {
                tableViewWindow.updateTableData(page.records(), page.schema());
                updatePageLabel();
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
                updatePageLabel();
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
                updatePageLabel();
                statusLabel.setText("Loaded " + page.records().size() + " records (page " + (pageNavigator.getPageIndex() + 1) + ")");
            }
        });
    }

    // ---------------------------
    // Export
    // ---------------------------

    private void onPageSizeChanged() {
        if (!fileLoader.isFileOpen()) {
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
        String suffix = searchFacade.isSearchMode() ? "search-results" : "page-" + (pageNavigator.getPageIndex() + 1);
        fc.setInitialFileName("export-" + suffix + ".json");

        File out = fc.showSaveDialog(table.getScene().getWindow());
        if (out == null) return;

        List<Map<String, Object>> rows = new ArrayList<>(table.getItems());
        executeWithUiUpdate("Export JSON failed", () -> {
            exportFacade.exportToJson(out.toPath(), rows);
            String scopeDesc = searchFacade.isSearchMode() ? "search results" : "page " + (pageNavigator.getPageIndex() + 1);
            statusLabel.setText("Exported " + rows.size() + " rows (" + scopeDesc + ") to " + out.getName());
        });
    }

    @FXML
    private void onExportCsv() {
        if (!fileLoader.isFileOpen() || table.getItems() == null || table.getItems().isEmpty()) {
            statusLabel.setText("Nothing to export");
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>(table.getItems());
        ExportSnapshot snapshot = exportFacade.captureExportSnapshot(rows);
        getOrCreateExportPreviewDialog().show(table.getScene(), snapshot);
    }

    private ExportPreviewDialog getOrCreateExportPreviewDialog() {
        if (exportPreviewDialog == null) {
            this.exportPreviewDialog = new ExportPreviewDialog(exportFacade);
        }
        return exportPreviewDialog;
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private void executeWithUiUpdate(String errorContext, UiAction action) {
        try {
            action.run();
        } catch (Exception ex) {
            ErrorAlert.showError(errorContext, ex);
            statusLabel.setText(errorContext);
        } finally {
            updateControls();
        }
    }

    private void updateControls() {
        boolean noFile = !fileLoader.isFileOpen();
        boolean searching = activeSearchTask != null && activeSearchTask.isRunning();

        if (reloadMenuItem != null) {
            reloadMenuItem.setDisable(noFile);
        }
        if (fileInfoBtn != null) {
            fileInfoBtn.setDisable(noFile);
        }

        if (searching) {
            if (applyBtn != null) {
                applyBtn.setDisable(false);
            }
            if (pageSizeCombo != null) pageSizeCombo.setDisable(true);
            if (maxResultsField != null) maxResultsField.setDisable(true);
            prevBtn.setDisable(true);
            nextBtn.setDisable(true);
            return;
        }

        if (applyBtn != null) {
            applyBtn.setDisable(noFile);
        }
        if (pageSizeCombo != null) {
            pageSizeCombo.setDisable(noFile);
        }
        if (maxResultsField != null) {
            maxResultsField.setDisable(noFile);
        }

        boolean searchMode = searchFacade.isSearchMode();

        prevBtn.setDisable(noFile || searchMode || pageNavigator.getPageIndex() == 0);
        boolean hasNext = pageNavigator.hasNextPage();
        if (pageNavigator.totalPages().isPresent() && pageNavigator.getPageIndex() + 1 >= pageNavigator.totalPages().getAsInt()) {
            hasNext = false;
        }
        nextBtn.setDisable(noFile || searchMode || !hasNext);
    }

    private void updatePageLabel() {
        if (!fileLoader.isFileOpen()) {
            pageLabel.setText("Page 1");
            return;
        }
        if (searchFacade.isSearchMode()) {
            pageLabel.setText("Search");
            return;
        }
        int current = pageNavigator.getPageIndex() + 1;
        OptionalInt total = pageNavigator.totalPages();
        if (total.isPresent()) {
            pageLabel.setText("Page " + current + " of " + PresentationFormatter.formatCount(total.getAsInt()));
        } else {
            pageLabel.setText("Page " + current);
        }
    }

    private void triggerBackgroundRecordCount() {
        if (!fileLoader.isFileOpen()) return;
        Path current = fileLoader.getCurrentFile();
        if (pageNavigator.totalRecords().isPresent()) {
            updatePageLabel();
            updateControls();
            refreshFileInfoIfOpen();
            return;
        }
        Task<Long> countTask = new Task<>() {
            @Override
            protected Long call() throws Exception {
                return fileLoader.countRecords(current);
            }
        };
        countTask.setOnSucceeded(_ -> {
            updatePageLabel();
            updateControls();
            refreshFileInfoIfOpen();
        });
        Thread t = new Thread(countTask, "avro-record-counter");
        t.setDaemon(true);
        t.start();
    }

    private void refreshFileInfoIfOpen() {
        if (fileInfoWindow.isShowing() && fileLoader.isFileOpen()) {
            try {
                AvroFileInfo info = fileLoader.getFileInfo();
                Path current = fileLoader.getCurrentFile();
                fileInfoWindow.refresh(info, () -> pageNavigator.totalRecords(), () -> fileLoader.countRecords(current));
            } catch (Exception ignored) {
            }
        }
    }

    private int safeMaxResults() {
        String s = maxResultsField.getText();
        if (s == null || s.isBlank()) {
            maxResultsProperty.set(searchFacade.getDefaultMaxResults());
            return searchFacade.getDefaultMaxResults();
        }
        try {
            int v = Integer.parseInt(s);
            v = Math.max(1, v);
            if (v > MAX_RESULTS_LIMIT) {
                v = MAX_RESULTS_LIMIT;
                statusLabel.setText("Max results clamped to " + MAX_RESULTS_LIMIT);
            }
            maxResultsProperty.set(v);
            return v;
        } catch (NumberFormatException ex) {
            maxResultsProperty.set(searchFacade.getDefaultMaxResults());
            return searchFacade.getDefaultMaxResults();
        }
    }

    private void unbindSearchProperties() {
        if (searchProgressBar != null) {
            searchProgressBar.progressProperty().unbind();
        }
        if (statusLabel != null) {
            statusLabel.textProperty().unbind();
        }
    }

    private void resetSearchButtonAndProgress() {
        if (applyBtn != null) {
            applyBtn.setText("Apply");
            applyBtn.getStyleClass().remove("btn-danger");
            if (!applyBtn.getStyleClass().contains("btn-primary")) {
                applyBtn.getStyleClass().add("btn-primary");
            }
            applyBtn.setDisable(!fileLoader.isFileOpen());
        }
        if (searchProgressBar != null) {
            searchProgressBar.setVisible(false);
            searchProgressBar.setManaged(false);
        }
        activeSearchControl = null;
    }

    private void cancelActiveSearchIfRunning() {
        Task<?> task = activeSearchTask;
        if (task != null && task.isRunning()) {
            task.cancel(true);
        }
        unbindSearchProperties();
        resetSearchButtonAndProgress();
        activeSearchTask = null;
    }

    @FunctionalInterface
    private interface UiAction {
        void run() throws Exception;
    }
}

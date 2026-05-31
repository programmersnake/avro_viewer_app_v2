package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.service.api.ExportFacade;
import com.dkostin.avro_viewer.app.service.api.ExportService;
import com.dkostin.avro_viewer.app.config.FlatteningConfig;
import com.dkostin.avro_viewer.app.util.StructuralFlatteningEngine;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Premium CSV Export Preview and Configuration Dialog.
 * Provides a responsive TableView preview of flattened data and real-time settings toggles.
 */
public class ExportPreviewDialog {

    // --- Layout Constants ---
    private static final double AVG_CHAR_WIDTH = 8.0;
    private static final double HEADER_CHAR_WIDTH = 8.5;
    private static final double PADDING_OFFSET = 16.0;
    private static final double HEADER_PADDING_OFFSET = 24.0;
    private static final double MAX_COLUMN_WIDTH = 400.0;
    
    // --- Throttling Constant ---
    private static final long PROGRESS_THROTTLE_MS = 100L;

    // --- Delimiters Map ---
    private static final Map<String, Character> DELIMITERS = Map.of(
            "Comma (,)", ',',
            "Semicolon (;)", ';',
            "Tab (\\t)", '\t'
    );

    private final ExportFacade exportFacade;
    private Stage stage;

    // --- UI Controls ---
    private RadioButton isolateRadio;
    private RadioButton deepRadio;
    private RadioButton serializeRadio;
    private RadioButton suffixRadio;
    private ComboBox<String> delimiterCombo;
    private TableView<Map<String, String>> previewTable;
    private Label statusLabel;
    private ProgressBar exportProgressBar;
    private VBox exportProgressBox;
    private Button exportBtn;
    private Button cancelBtn;

    // --- State Properties & Bindings ---
    private final BooleanProperty globalDisable = new SimpleBooleanProperty(false);

    // --- State ---
    private List<String> rawSampleRecords;
    private Task<FlattenResult> activePreviewTask;
    private Task<Void> activeExportTask;

    public ExportPreviewDialog(ExportFacade exportFacade) {
        this.exportFacade = Objects.requireNonNull(exportFacade, "exportFacade cannot be null");
    }

    /**
     * Shows the CSV Export Preview and Configuration dialog.
     * <p>
     * On every invocation, it resets any active progress/tasks and reloads 10 sample records
     * from the current browse or search filters state.
     *
     * @param ownerScene The parent application Scene to inherit styling stylesheets from.
     */
    public void show(Scene ownerScene) {
        if (stage == null) {
            initStage(ownerScene);
        }

        // Inherit themes from main app window
        stage.getScene().getStylesheets().setAll(ownerScene.getStylesheets());

        // Cancel any lingering tasks and reload samples from the current search context
        if (activePreviewTask != null && activePreviewTask.isRunning()) {
            activePreviewTask.cancel(true);
        }
        if (activeExportTask != null && activeExportTask.isRunning()) {
            activeExportTask.cancel(true);
        }
        
        loadSamples();

        stage.show();
        stage.toFront();
    }

    private void initStage(Scene ownerScene) {
        stage = new Stage();
        stage.setTitle("Export to CSV Preview");
        stage.initOwner(ownerScene.getWindow());
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        // --- Layout Construction ---
        BorderPane root = new BorderPane();
        root.getStyleClass().add("surface");

        // SplitPane in the Center
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(createLeftPanel(), createRightPanel());
        splitPane.setDividerPositions(0.30); // 30% config, 70% table
        root.setCenter(splitPane);

        // Bottom status & Action bar
        root.setBottom(createBottomBar());

        // Bind control disable states to the single global property
        bindControlProperties();

        Scene scene = new Scene(root, 1000, 650);
        stage.setScene(scene);

        // Stop tasks on stage hide
        stage.setOnHidden(e -> {
            if (activePreviewTask != null && activePreviewTask.isRunning()) {
                activePreviewTask.cancel(true);
            }
            if (activeExportTask != null && activeExportTask.isRunning()) {
                activeExportTask.cancel(true);
            }
        });
    }

    private ScrollPane createLeftPanel() {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("surface-2");
        panel.setPrefWidth(300);

        Label title = new Label("Export Settings");
        title.getStyleClass().add("h1");
        panel.getChildren().add(title);

        // Mode Card
        VBox modeCard = new VBox(8);
        modeCard.getStyleClass().add("card");
        Label modeTitle = new Label("EXPORT MODE");
        modeTitle.getStyleClass().add("muted");
        modeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        ToggleGroup modeGroup = new ToggleGroup();
        isolateRadio = new RadioButton("Isolate Objects (Safe)");
        isolateRadio.setToggleGroup(modeGroup);
        isolateRadio.setTooltip(new Tooltip("Leaves nested complex structures as JSON strings inside cells."));
        isolateRadio.setOnAction(e -> triggerPreviewGeneration());

        deepRadio = new RadioButton("Deep Flattening (Unpack)");
        deepRadio.setToggleGroup(modeGroup);
        deepRadio.setTooltip(new Tooltip("Unpacks hierarchical structures recursively using dot separation."));
        deepRadio.setOnAction(e -> triggerPreviewGeneration());
        deepRadio.setSelected(true); // Default

        modeCard.getChildren().addAll(modeTitle, isolateRadio, deepRadio);
        panel.getChildren().add(modeCard);

        // Array Strategy Card
        VBox arrayCard = new VBox(8);
        arrayCard.getStyleClass().add("card");
        Label arrayTitle = new Label("NESTED ARRAY STRATEGY");
        arrayTitle.getStyleClass().add("muted");
        arrayTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        ToggleGroup arrayGroup = new ToggleGroup();
        serializeRadio = new RadioButton("Serialize to JSON");
        serializeRadio.setToggleGroup(arrayGroup);
        serializeRadio.setTooltip(new Tooltip("Inline arrays are written as nested JSON blocks."));
        serializeRadio.setOnAction(e -> triggerPreviewGeneration());

        suffixRadio = new RadioButton("Index-Based Suffixing");
        suffixRadio.setToggleGroup(arrayGroup);
        suffixRadio.setTooltip(new Tooltip("Unpacks arrays into distinct columns: key.0, key.1"));
        suffixRadio.setOnAction(e -> triggerPreviewGeneration());
        suffixRadio.setSelected(true); // Default

        arrayCard.getChildren().addAll(arrayTitle, serializeRadio, suffixRadio);
        panel.getChildren().add(arrayCard);

        // Delimiter Card
        VBox delimCard = new VBox(8);
        delimCard.getStyleClass().add("card");
        Label delimTitle = new Label("COLUMN DELIMITER");
        delimTitle.getStyleClass().add("muted");
        delimTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        delimiterCombo = new ComboBox<>();
        delimiterCombo.getItems().addAll("Comma (,)", "Semicolon (;)", "Tab (\\t)");
        delimiterCombo.setValue("Comma (,)");
        delimiterCombo.setPrefWidth(Double.MAX_VALUE);
        delimiterCombo.setTooltip(new Tooltip("Delimiter applies only to the output CSV file structure."));
        delimiterCombo.setOnAction(e -> triggerPreviewGeneration());

        Label delimNote = new Label("Applies to final CSV file.");
        delimNote.getStyleClass().add("muted");
        delimNote.setStyle("-fx-font-size: 10px;");

        delimCard.getChildren().addAll(delimTitle, delimiterCombo, delimNote);
        panel.getChildren().add(delimCard);

        ScrollPane scrollPane = new ScrollPane(panel);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("filters-scroll");
        return scrollPane;
    }

    private VBox createRightPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("surface");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Preview Matrix");
        title.getStyleClass().add("h1");
        Label subtitle = new Label("(Sample of first 10 records)");
        subtitle.getStyleClass().add("muted");
        header.getChildren().addAll(title, subtitle);
        panel.getChildren().add(header);

        previewTable = new TableView<>();
        previewTable.setPlaceholder(new Label("No preview data loaded."));
        VBox.setVgrow(previewTable, Priority.ALWAYS);
        panel.getChildren().add(previewTable);

        return panel;
    }

    private HBox createBottomBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("topbar"); // reuse topbar border line
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("muted");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Progress bar container (initially hidden)
        exportProgressBox = new VBox(4);
        exportProgressBox.setAlignment(Pos.CENTER_LEFT);
        exportProgressBox.setPrefWidth(200);
        exportProgressBox.setVisible(false);

        exportProgressBar = new ProgressBar(0);
        exportProgressBar.setPrefWidth(200);
        exportProgressBox.getChildren().addAll(exportProgressBar);

        exportBtn = new Button("Export CSV...");
        exportBtn.getStyleClass().add("btn-primary");
        exportBtn.setOnAction(e -> handleExport());

        cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("btn");
        cancelBtn.setOnAction(e -> handleCancel());

        bar.getChildren().addAll(statusLabel, exportProgressBox, exportBtn, cancelBtn);
        return bar;
    }

    private void bindControlProperties() {
        // Combined bindings to avoid re-binding issues and JavaFX warnings
        BooleanBinding arraysDisableBinding = isolateRadio.selectedProperty().or(globalDisable);

        serializeRadio.disableProperty().bind(arraysDisableBinding);
        suffixRadio.disableProperty().bind(arraysDisableBinding);

        isolateRadio.disableProperty().bind(globalDisable);
        deepRadio.disableProperty().bind(globalDisable);
        delimiterCombo.disableProperty().bind(globalDisable);
        exportBtn.disableProperty().bind(globalDisable);
    }

    private void loadSamples() {
        setControlsDisabled(true);
        previewTable.getColumns().clear();
        previewTable.getItems().clear();
        previewTable.setPlaceholder(new Label("Loading sample records..."));
        statusLabel.setText("Loading sample records...");

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return exportFacade.getSampleRecords(10);
            }
        };

        task.setOnSucceeded(e -> {
            rawSampleRecords = task.getValue();
            if (rawSampleRecords == null || rawSampleRecords.isEmpty()) {
                previewTable.setPlaceholder(new Label("No records to preview"));
                statusLabel.setText("No records found in current context");
            } else {
                setControlsDisabled(false);
                triggerPreviewGeneration();
            }
        });

        task.setOnFailed(e -> {
            Throwable err = task.getException();
            ErrorAlert.showError("Failed to load sample records", err);
            previewTable.setPlaceholder(new Label("Failed to load records"));
            statusLabel.setText("Failed to load sample records");
        });

        Thread t = new Thread(task, "export-sample-loader");
        t.setDaemon(true);
        t.start();
    }

    private void triggerPreviewGeneration() {
        if (rawSampleRecords == null || rawSampleRecords.isEmpty()) {
            return;
        }

        if (activePreviewTask != null && activePreviewTask.isRunning()) {
            activePreviewTask.cancel(true);
        }

        boolean deepFlattening = deepRadio.isSelected();
        boolean indexBasedSuffixing = suffixRadio.isSelected();
        FlatteningConfig config = new FlatteningConfig(deepFlattening, indexBasedSuffixing);

        statusLabel.setText("Generating preview...");

        Task<FlattenResult> task = new Task<>() {
            @Override
            protected FlattenResult call() throws Exception {
                ObjectMapper mapper = new ObjectMapper()
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
                List<Map<String, String>> rows = new ArrayList<>();
                LinkedHashSet<String> headers = new LinkedHashSet<>();

                for (String json : rawSampleRecords) {
                    if (isCancelled()) {
                        break;
                    }
                    JsonNode node = mapper.readTree(json);
                    Map<String, String> flatRow = StructuralFlatteningEngine.flatten(node, config);
                    rows.add(flatRow);
                    headers.addAll(flatRow.keySet());
                }

                return new FlattenResult(rows, headers);
            }
        };

        activePreviewTask = task;

        task.setOnSucceeded(e -> {
            if (activePreviewTask != task) return;

            FlattenResult result = task.getValue();
            updatePreviewTable(result.rows(), result.headers());
            statusLabel.setText("Preview ready");
        });

        task.setOnFailed(e -> {
            if (activePreviewTask != task) return;
            Throwable err = task.getException();
            ErrorAlert.showError("Failed to generate preview", err);
            statusLabel.setText("Preview failed");
        });

        Thread t = new Thread(task, "export-preview-generator");
        t.setDaemon(true);
        t.start();
    }

    private void updatePreviewTable(List<Map<String, String>> rows, LinkedHashSet<String> headers) {
        previewTable.getColumns().clear();
        previewTable.getItems().clear();

        for (String header : headers) {
            TableColumn<Map<String, String>, String> col = new TableColumn<>(header);
            col.setCellValueFactory(data -> {
                String val = data.getValue().get(header);
                return new SimpleStringProperty(val == null ? "" : val);
            });
            previewTable.getColumns().add(col);
        }

        previewTable.getItems().setAll(rows);
        autoFitColumns();
    }

    private void autoFitColumns() {
        for (TableColumn<Map<String, String>, ?> col : previewTable.getColumns()) {
            String colHeader = col.getText();
            double maxW = colHeader.length() * HEADER_CHAR_WIDTH + HEADER_PADDING_OFFSET; // header length + padding

            for (Map<String, String> item : previewTable.getItems()) {
                Object cellVal = col.getCellData(item);
                if (cellVal != null) {
                    String str = String.valueOf(cellVal);
                    double w = str.length() * AVG_CHAR_WIDTH + PADDING_OFFSET;
                    if (w > maxW) {
                        maxW = w;
                    }
                }
            }
            col.setPrefWidth(Math.min(maxW, MAX_COLUMN_WIDTH));
        }
    }

    private void handleExport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save CSV Export");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        fc.setInitialFileName("export-preview.csv");

        File outFile = fc.showSaveDialog(stage);
        if (outFile == null) {
            return;
        }

        char delim = DELIMITERS.getOrDefault(delimiterCombo.getValue(), ',');

        boolean deepFlattening = deepRadio.isSelected();
        boolean indexBasedSuffixing = suffixRadio.isSelected();
        FlatteningConfig config = new FlatteningConfig(deepFlattening, indexBasedSuffixing);

        final char finalDelim = delim;

        // Toggle progress view
        exportProgressBox.setVisible(true);
        setControlsDisabled(true);
        cancelBtn.setText("Cancel Export");
        exportProgressBar.setProgress(0);
        statusLabel.setText("Initializing export...");

        AtomicLong lastProgressUpdate = new AtomicLong(0L);

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                exportFacade.exportToCsvStreaming(
                        outFile.toPath(),
                        config,
                        finalDelim,
                        (pass, current, total) -> {
                            if (isCancelled()) {
                                return;
                            }
                            long now = System.currentTimeMillis();
                            if (pass == 1) {
                                if (now - lastProgressUpdate.get() >= PROGRESS_THROTTLE_MS || current == 1) {
                                    lastProgressUpdate.set(now);
                                    Platform.runLater(() -> {
                                        statusLabel.setText("Analyzing schema (scanned " + current + " records)...");
                                        exportProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                                    });
                                }
                            } else if (pass == 2) {
                                if (now - lastProgressUpdate.get() >= PROGRESS_THROTTLE_MS || current == total) {
                                    lastProgressUpdate.set(now);
                                    double progress = (double) current / total;
                                    Platform.runLater(() -> {
                                        statusLabel.setText("Writing records (" + current + " / " + total + ")...");
                                        exportProgressBar.setProgress(progress);
                                    });
                                }
                            }
                        }
                );
                return null;
            }
        };

        activeExportTask = exportTask;

        exportTask.setOnSucceeded(e -> {
            exportProgressBox.setVisible(false);
            setControlsDisabled(false);
            cancelBtn.setText("Close");
            statusLabel.setText("Exported successfully to: " + outFile.getName());

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Success");
            alert.setHeaderText(null);
            alert.setContentText("Data exported successfully to " + outFile.getName());
            alert.initOwner(stage);
            alert.showAndWait();
        });

        exportTask.setOnFailed(e -> {
            exportProgressBox.setVisible(false);
            setControlsDisabled(false);
            cancelBtn.setText("Close");
            Throwable err = exportTask.getException();
            ErrorAlert.showError("Export failed", err);
            statusLabel.setText("Export failed");
        });

        exportTask.setOnCancelled(e -> {
            exportProgressBox.setVisible(false);
            setControlsDisabled(false);
            cancelBtn.setText("Close");
            statusLabel.setText("Export cancelled");
        });

        Thread t = new Thread(exportTask, "csv-exporter");
        t.setDaemon(true);
        t.start();
    }

    private void handleCancel() {
        if (activeExportTask != null && activeExportTask.isRunning()) {
            activeExportTask.cancel(true);
        } else {
            if (activePreviewTask != null && activePreviewTask.isRunning()) {
                activePreviewTask.cancel(true);
            }
            stage.close();
        }
    }

    private void setControlsDisabled(boolean disabled) {
        globalDisable.set(disabled);
    }

    private record FlattenResult(List<Map<String, String>> rows, LinkedHashSet<String> headers) {}
}

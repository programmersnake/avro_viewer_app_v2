package com.dkostin.avro_viewer.app.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;

import static com.dkostin.avro_viewer.app.ui.Theme.DARK;
import static com.dkostin.avro_viewer.app.ui.Theme.LIGHT;

public class MainController {

    @FXML private ToggleButton themeToggle;

    @FXML private ComboBox<Integer> pageSizeCombo;
    @FXML private Label pageSizeValue;
    @FXML private ComboBox<String> operatorCombo;
    @FXML private TextField searchField;
    @FXML private TextField maxResultsField;

    @FXML private Label activeFiltersLabel;
    @FXML private Label pageLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<RowItem> table;
    @FXML private TableColumn<RowItem, Number> colId;
    @FXML private TableColumn<RowItem, String> colTitle;
    @FXML private TableColumn<RowItem, String> colCountry;
    @FXML private TableColumn<RowItem, Number> colSalary;

    private Scene scene;

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
        pageSizeCombo.setOnAction(_ -> pageSizeValue.setText(String.valueOf(pageSizeCombo.getValue())));

        operatorCombo.setItems(FXCollections.observableArrayList("(all)", "AND", "OR"));
        operatorCombo.setValue("(all)");

        maxResultsField.setText("500");

        // table bindings
        colId.setCellValueFactory(d -> d.getValue().idProperty());
        colTitle.setCellValueFactory(d -> d.getValue().titleProperty());
        colCountry.setCellValueFactory(d -> d.getValue().countryProperty());
        colSalary.setCellValueFactory(d -> d.getValue().salaryProperty());

        // demo data
        ObservableList<RowItem> rows = FXCollections.observableArrayList(
                new RowItem(1, "Engineer", "Ukraine", 19000),
                new RowItem(2, "Engineer", "Ukraine", 125000),
                new RowItem(3, "Engineer", "Ukraine", 130000),
                new RowItem(4, "Engineer", "Ukraine", 130000),
                new RowItem(5, "Engineer", "Ukraine", 130000),
                new RowItem(6, "Engineer", "Ukraine", 190000),
                new RowItem(7, "Engineer", "Ukraine", 190000)
        );
        table.setItems(rows);
    }

    @FXML
    private void onThemeToggle() {
        if (scene == null) return;

        scene.getStylesheets().clear();
        var selectedTheme = themeToggle.isSelected() ? DARK : LIGHT;
        scene.getStylesheets().add(getClass().getResource(selectedTheme.getCssPath()).toExternalForm());
    }

    // --- Actions (placeholders) ---
    @FXML private void onOpenFile()     { statusLabel.setText("Open file clicked"); }
    @FXML private void onExportJson()   { statusLabel.setText("Export JSON clicked"); }
    @FXML private void onExportCsv()    { statusLabel.setText("Export CSV clicked"); }

    @FXML private void onAddFilter()    { activeFiltersLabel.setText("Active: (add filter clicked)"); }
    @FXML private void onApplyFilters() { activeFiltersLabel.setText("Active: (apply filters clicked)"); }
    @FXML private void onClearFilters() { activeFiltersLabel.setText("Active: (none)"); }

    @FXML private void onRunSearch() {
        statusLabel.setText("Searching: '" + searchField.getText() + "', max=" + maxResultsField.getText());
    }

    @FXML private void onPrevPage() { pageLabel.setText("Page (prev)"); }
    @FXML private void onNextPage() { pageLabel.setText("Page (next)"); }
}


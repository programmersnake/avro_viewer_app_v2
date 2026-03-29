package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.domain.model.filter.FilterCriterion;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterRowModel;
import com.dkostin.avro_viewer.app.domain.model.filter.MatchOperation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * UI component for managing dynamic filters (condition strings)
 */
public class FiltersUi {

    private final VBox filtersContainer;
    private final ObservableList<FilterOption> availableFields = FXCollections.observableArrayList();
    private final List<FilterRowModel> filterModels = new ArrayList<>();
    private final List<FilterRowView> filterViews = new ArrayList<>();

    public FiltersUi(VBox filtersContainer) {
        this.filtersContainer = filtersContainer;
    }

    /**
     * Resets all filters: clears the list and adds one empty row
     */
    public void clearFilters() {
        filtersContainer.getChildren().clear();
        filterModels.clear();
        filterViews.clear();
        addFilterRow();
    }

    /**
     * Adds a new filter row (field-condition-value) to the UI
     */
    public void addFilterRow() {
        // Create a default filter model
        FilterRowModel model = new FilterRowModel();
        filterModels.add(model);
        // Create controls for the field, operator, and value
        ComboBox<FilterOption> fieldCombo = new ComboBox<>(availableFields);
        fieldCombo.setPromptText("Field");
        fieldCombo.setPrefWidth(220);

        ComboBox<MatchOperation> opCombo = new ComboBox<>(FXCollections.observableArrayList(MatchOperation.values()));
        opCombo.setPromptText("Condition");
        opCombo.setPrefWidth(180);
        opCombo.setValue(MatchOperation.CONTAINS);

        TextField valueField = new TextField();
        HBox.setHgrow(valueField, Priority.ALWAYS);
        valueField.setPromptText("Value (use 'null')");
        // Delete row button
        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().addAll("btn", "btn-icon");

        // Bind UI fields to the model
        fieldCombo.valueProperty().addListener((_, _, newVal) -> model.setField(newVal));
        opCombo.valueProperty().addListener((_, _, newVal) -> model.setOp(newVal));
        valueField.textProperty().addListener((_, _, newVal) -> model.setValue(newVal));
        // Disable the value field for IS_NULL/NOT_NULL operations
        opCombo.valueProperty().addListener((_, _, newOp) -> {
            boolean noValueNeeded = (newOp == MatchOperation.IS_NULL || newOp == MatchOperation.NOT_NULL);
            valueField.setDisable(noValueNeeded);
            if (noValueNeeded) {
                valueField.clear();
            }
        });

        // Create a row representation and add to the container
        FilterRowView view = new FilterRowView(
                new HBox(10, fieldCombo, opCombo, valueField, removeBtn),
                fieldCombo, opCombo, valueField, removeBtn, model
        );
        filterViews.add(view);
        filtersContainer.getChildren().add(view.root());

        // Handler for the delete row button
        removeBtn.setOnAction(_ -> removeFilterRow(view));
    }

    /**
     * Internal method for removing a filter string
     */
    private void removeFilterRow(FilterRowView view) {
        filtersContainer.getChildren().remove(view.root());
        filterModels.remove(view.model());
        filterViews.remove(view);
        // If all lines are removed – add an empty line so that the interface does not remain empty
        if (filterViews.isEmpty()) {
            addFilterRow();
        }
    }

    /**
     * Updates the list of available fields in all Comboboxes based on the new Avro schema
     */
    public void updateFieldOptions(Schema schema) {
        List<FilterOption> options = new ArrayList<>();
        options.add(FilterOption.ALL_FIELDS); // wildcard always first
        if (schema != null) {
            schema.getFields().stream()
                    .map(f -> FilterOption.ofField(f.name()))
                    .forEach(options::add);
        }
        availableFields.setAll(options);

        // For each filter row, check if the selected field is still available
        for (FilterRowView view : filterViews) {
            FilterOption selected = view.fieldCombo().getValue();
            if (selected != null && !availableFields.contains(selected)) {
                // If the previously selected field is missing in the new schema – reset
                view.fieldCombo().setValue(null);
                view.model().setField(null);
            }
        }
    }

    /**
     * Collects filter criteria from all rows, ignoring incomplete/empty ones
     */
    public List<FilterCriterion> getFilterCriteria() {
        List<FilterCriterion> criteria = new ArrayList<>();
        for (FilterRowModel model : filterModels) {
            FilterOption field = model.getField();
            MatchOperation op = model.getOp();
            String value = model.getValue();
            // Skip if field or operator is not specified
            if (field == null || op == null) continue;
            // If the operator does not require a value (IS_NULL, NOT_NULL)
            if (op == MatchOperation.IS_NULL || op == MatchOperation.NOT_NULL) {
                criteria.add(new FilterCriterion(field, op, null));
            }
            // If a value is required, but the value field is empty – skip
            else if (value != null && !value.isBlank()) {
                String trimmed = value.trim();
                Object parsedValue = trimmed.equalsIgnoreCase("null") ? null : trimmed;
                criteria.add(new FilterCriterion(field, op, parsedValue));
            }
        }
        return criteria;
    }

    public record FilterRowView(
            HBox root,
            ComboBox<FilterOption> fieldCombo,
            ComboBox<MatchOperation> opCombo,
            TextField valueField,
            Button removeBtn,
            FilterRowModel model
    ) {
    }
}



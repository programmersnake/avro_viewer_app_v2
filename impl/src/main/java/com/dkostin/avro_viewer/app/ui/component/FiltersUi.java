package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.config.FilterPathValidator;
import com.dkostin.avro_viewer.app.domain.model.fileinfo.SchemaNode;
import com.dkostin.avro_viewer.app.domain.model.filter.*;
import com.dkostin.avro_viewer.app.util.schema.SchemaCatalog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * UI component for managing dynamic filters (condition strings)
 */
public class FiltersUi {

    private static class FilterGroupState {
        final List<FilterRowModel> models = new ArrayList<>();
        final List<FilterRowView> views = new ArrayList<>();
    }

    private final VBox filtersContainer;
    private final ObservableList<FilterOption> availableFields = FXCollections.observableArrayList();
    private final List<FilterGroupState> groups = new ArrayList<>();
    private Schema currentSchema;

    public FiltersUi(VBox filtersContainer) {
        this.filtersContainer = filtersContainer;
        // Initialize with one group containing one empty row
        FilterGroupState initialGroup = new FilterGroupState();
        groups.add(initialGroup);
        addFilterRow(initialGroup);
        rebuildUI();
    }

    /** Creates a new OR group with one empty filter row and rebuilds the UI. */
    public void addGroup() {
        FilterGroupState group = new FilterGroupState();
        groups.add(group);
        addFilterRow(group);
        rebuildUI();
    }

    /** Resets all filters: one group, one empty row. */
    public void clearFilters() {
        groups.clear();
        FilterGroupState group = new FilterGroupState();
        groups.add(group);
        addFilterRow(group);
        rebuildUI();
    }

    /** Collects filter groups from all groups, ignoring incomplete/empty criteria. */
    public List<FilterGroup> getFilterGroups() {
        List<FilterGroup> result = new ArrayList<>();
        for (FilterGroupState group : groups) {
            List<FilterCriterion> criteria = collectCriteria(group);
            if (!criteria.isEmpty()) {
                result.add(new FilterGroup(criteria));
            }
        }
        return result;
    }

    /** Adds a filter row to a specific group (does NOT call rebuildUI — caller must do that or call it in batch). */
    private void addFilterRow(FilterGroupState group) {
        // Create a default filter model
        FilterRowModel model = new FilterRowModel();
        group.models.add(model);
        
        // Create controls for the field, operator, and value
        FieldPathPicker picker = new FieldPathPicker(availableFields, () -> currentSchema);
        ComboBox<FilterOption> fieldCombo = picker.getComboBox();
        fieldCombo.setPromptText("Field (or a.b.c path)");

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

        // Bind picker to model
        fieldCombo.getEditor().textProperty().addListener((_, _, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                model.setField(null);
            } else if (newVal.equals("* (All Fields)") || newVal.equals("*")) {
                model.setField(FilterOption.ALL_FIELDS);
            } else {
                FilterOption matched = availableFields.stream()
                        .filter(opt -> !opt.wildcard() && opt.fieldName().equalsIgnoreCase(newVal.trim()))
                        .findFirst()
                        .orElseGet(() -> FilterOption.ofField(newVal.trim()));
                model.setField(matched);
            }
        });
        fieldCombo.valueProperty().addListener((_, _, opt) -> model.setField(opt));

        opCombo.valueProperty().addListener((_, _, newVal) -> model.setOp(newVal));
        valueField.textProperty().addListener((_, _, newVal) -> model.setValue(newVal));
        // Disable the value field for IS_NULL/NOT_NULL operations
        opCombo.valueProperty().addListener((_, _, newOp) -> {
            boolean noValueNeeded = (newOp == MatchOperation.IS_NULL || newOp == MatchOperation.NOT_NULL);
            valueField.setDisable(noValueNeeded);
            if (noValueNeeded) {
                valueField.clear();
            } else if (newOp == MatchOperation.IN) {
                valueField.setPromptText("e.g. 2100, 2300 (use \\, for literal comma)");
            } else if (newOp == MatchOperation.SIZE_EQUALS || newOp == MatchOperation.SIZE_GREATER_THAN || newOp == MatchOperation.SIZE_LESS_THAN) {
                valueField.setPromptText("e.g. 1, 5, 10");
            } else {
                valueField.setPromptText("Value (use 'null')");
            }
        });

        // Create a row representation and add to the container
        FilterRowView view = new FilterRowView(
                new HBox(10, fieldCombo, opCombo, valueField, removeBtn),
                picker, opCombo, valueField, removeBtn, model
        );
        group.views.add(view);

        // Handler for the delete row button
        removeBtn.setOnAction(_ -> removeFilterRow(group, view));
    }

    /** Removes a filter row from a group. If the group becomes empty, removes the group. If last group is removed, resets. */
    private void removeFilterRow(FilterGroupState group, FilterRowView view) {
        group.views.remove(view);
        group.models.remove(view.model());
        
        if (group.views.isEmpty()) {
            // Remove the entire group (UX decision: Q3=A)
            groups.remove(group);
            if (groups.isEmpty()) {
                // Last group removed — reset to 1 group with 1 empty row
                clearFilters();
                return;
            }
        }
        rebuildUI();
    }

    /** Rebuilds the entire filtersContainer based on current groups state. */
    private void rebuildUI() {
        filtersContainer.getChildren().clear();
        
        if (groups.size() == 1) {
            // FLAT MODE: no group border, rows directly in container (UX decision: Q4=B)
            FilterGroupState group = groups.getFirst();
            for (FilterRowView view : group.views) {
                filtersContainer.getChildren().add(view.root());
            }
            // "+ Add filter" button at the bottom
            Button addBtn = new Button("+ Add filter");
            addBtn.getStyleClass().add("btn");
            addBtn.setOnAction(_ -> { addFilterRow(group); rebuildUI(); });
            filtersContainer.getChildren().add(addBtn);
        } else {
            // GROUPED MODE: bordered containers with OR dividers
            for (int i = 0; i < groups.size(); i++) {
                if (i > 0) {
                    filtersContainer.getChildren().add(createOrDivider());
                }
                filtersContainer.getChildren().add(buildGroupContainer(groups.get(i)));
            }
        }
    }

    /** Creates a bordered group container with its filter rows, "+ Add filter" button, and "✕ Remove group" button. */
    private VBox buildGroupContainer(FilterGroupState group) {
        VBox rowsBox = new VBox(10);
        for (FilterRowView view : group.views) {
            rowsBox.getChildren().add(view.root());
        }
        
        // Bottom toolbar: "+ Add filter" on left, "✕ Remove group" on right
        Button addBtn = new Button("+ Add filter");
        addBtn.getStyleClass().add("btn");
        addBtn.setOnAction(_ -> { addFilterRow(group); rebuildUI(); });
        
        Button removeGroupBtn = new Button("✕ Remove group");
        removeGroupBtn.getStyleClass().addAll("btn", "btn-danger");
        removeGroupBtn.setOnAction(_ -> {
            groups.remove(group);
            if (groups.isEmpty()) {
                clearFilters();
                return;
            }
            rebuildUI();
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, addBtn, spacer, removeGroupBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        
        VBox container = new VBox(10, rowsBox, toolbar);
        container.getStyleClass().add("filter-group");
        container.setPadding(new Insets(10));
        return container;
    }

    /** Creates the "── OR ──" divider label. */
    private Label createOrDivider() {
        Label divider = new Label("── OR ──");
        divider.getStyleClass().add("or-divider");
        divider.setMaxWidth(Double.MAX_VALUE);
        divider.setAlignment(Pos.CENTER);
        return divider;
    }

    /** Collects valid FilterCriterion from a group (same logic as old getFilterCriteria). */
    private List<FilterCriterion> collectCriteria(FilterGroupState group) {
        List<FilterCriterion> criteria = new ArrayList<>();
        for (FilterRowModel model : group.models) {
            FilterOption field = model.getField();
            MatchOperation op = model.getOp();
            String value = model.getValue();
            // Skip if field or operator is not specified
            if (field == null || op == null) continue;
            
            // Size operations not supported with wildcard
            if ((op == MatchOperation.SIZE_EQUALS || op == MatchOperation.SIZE_GREATER_THAN || op == MatchOperation.SIZE_LESS_THAN) && field.wildcard()) continue;

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

    /**
     * Adds or populates a filter row with the specified dot-path.
     * Fills the first empty row or appends a new row to the last group.
     */
    public void addFilterFor(String path) {
        if (path == null || path.isBlank()) return;

        FilterOption targetOption = availableFields.stream()
                .filter(opt -> !opt.wildcard() && opt.fieldName().equalsIgnoreCase(path.trim()))
                .findFirst()
                .orElseGet(() -> FilterOption.ofField(path.trim()));

        // Look for the first row with empty field
        for (FilterGroupState group : groups) {
            for (FilterRowView view : group.views) {
                if (view.model().getField() == null) {
                    view.picker().setValue(targetOption);
                    view.model().setField(targetOption);
                    return;
                }
            }
        }

        // If no empty row found, append to the last group (or create one if empty)
        if (groups.isEmpty()) {
            addGroup();
        }
        FilterGroupState lastGroup = groups.getLast();
        addFilterRow(lastGroup);
        FilterRowView lastView = lastGroup.views.getLast();
        lastView.picker().setValue(targetOption);
        lastView.model().setField(targetOption);
        rebuildUI();
    }

    /**
     * Updates the list of available fields in all Comboboxes based on the new Avro schema
     */
    public void updateFieldOptions(Schema schema) {
        this.currentSchema = schema;
        List<FilterOption> options = new ArrayList<>();
        options.add(FilterOption.ALL_FIELDS); // wildcard always first
        if (schema != null) {
            for (SchemaNode node : SchemaCatalog.paths(schema)) {
                options.add(FilterOption.ofField(node.path(), node.typeDisplay(), node.depth()));
            }
        }
        availableFields.setAll(options);

        // Check all groups
        for (FilterGroupState group : groups) {
            for (FilterRowView view : group.views) {
                FilterOption selected = view.model().getField();
                if (selected != null && !selected.wildcard()) {
                    if (!FilterPathValidator.isValidPath(schema, selected.fieldName())) {
                        view.picker().setValue(null);
                        view.model().setField(null);
                    }
                }
                view.picker().validateCurrentText();
            }
        }
    }

    public record FilterRowView(
            HBox root,
            FieldPathPicker picker,
            ComboBox<MatchOperation> opCombo,
            TextField valueField,
            Button removeBtn,
            FilterRowModel model
    ) {
        public ComboBox<FilterOption> fieldCombo() {
            return picker.getComboBox();
        }
    }
}

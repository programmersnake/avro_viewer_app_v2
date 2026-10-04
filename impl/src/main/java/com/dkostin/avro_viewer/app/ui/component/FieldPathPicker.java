package com.dkostin.avro_viewer.app.ui.component;

import com.dkostin.avro_viewer.app.config.FilterPathValidator;
import com.dkostin.avro_viewer.app.domain.model.filter.FilterOption;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.apache.avro.Schema;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Autocomplete path picker for filter rows.
 * Encapsulates editable ComboBox with real-time substring filtering,
 * hierarchical indentation, formatted type badges, and inline validation.
 */
public class FieldPathPicker {

    private final ComboBox<FilterOption> comboBox;
    private final FilteredList<FilterOption> filteredList;
    private final ObservableList<FilterOption> masterList;
    private final Supplier<Schema> schemaSupplier;

    private boolean isUpdatingSelection = false;
    private Tooltip invalidTooltip;

    public FieldPathPicker(ObservableList<FilterOption> masterList, Supplier<Schema> schemaSupplier) {
        this.masterList = Objects.requireNonNull(masterList, "masterList");
        this.schemaSupplier = Objects.requireNonNull(schemaSupplier, "schemaSupplier");
        this.filteredList = new FilteredList<>(masterList, _ -> true);

        this.comboBox = new ComboBox<>(filteredList);
        this.comboBox.setEditable(true);
        this.comboBox.setPrefWidth(210);

        setupConverter();
        setupCellFactory();
        setupEditorListeners();
    }

    public ComboBox<FilterOption> getComboBox() {
        return comboBox;
    }

    public FilterOption getValue() {
        return comboBox.getValue();
    }

    public void setValue(FilterOption value) {
        isUpdatingSelection = true;
        try {
            comboBox.setValue(value);
            if (value != null) {
                comboBox.getEditor().setText(value.wildcard() ? "* (All Fields)" : value.fieldName());
            } else {
                comboBox.getEditor().clear();
            }
            validateCurrentText();
        } finally {
            isUpdatingSelection = false;
        }
    }

    public void validateCurrentText() {
        String text = comboBox.getEditor().getText();
        Schema schema = schemaSupplier.get();

        if (text == null || text.isBlank() || "*".equals(text.trim()) || "* (All Fields)".equalsIgnoreCase(text.trim())) {
            setInvalid(false);
            return;
        }

        if (schema == null) {
            setInvalid(false);
            return;
        }

        boolean valid = FilterPathValidator.isValidPath(schema, text.trim());
        setInvalid(!valid);
    }

    private void setInvalid(boolean invalid) {
        if (invalid) {
            if (!comboBox.getStyleClass().contains("field-invalid")) {
                comboBox.getStyleClass().add("field-invalid");
            }
            if (invalidTooltip == null) {
                invalidTooltip = new Tooltip("Unknown field in schema");
            }
            comboBox.setTooltip(invalidTooltip);
        } else {
            comboBox.getStyleClass().remove("field-invalid");
            comboBox.setTooltip(null);
        }
    }

    private void setupConverter() {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FilterOption option) {
                if (option == null) return "";
                return option.wildcard() ? "* (All Fields)" : option.fieldName();
            }

            @Override
            public FilterOption fromString(String string) {
                if (string == null || string.isBlank()) {
                    return null;
                }
                String trimmed = string.trim();
                if (trimmed.equals("*") || trimmed.equalsIgnoreCase("* (All Fields)")) {
                    return FilterOption.ALL_FIELDS;
                }
                for (FilterOption opt : masterList) {
                    if (opt.fieldName().equalsIgnoreCase(trimmed)) {
                        return opt;
                    }
                }
                return FilterOption.ofField(trimmed);
            }
        });
    }

    private void setupCellFactory() {
        comboBox.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(FilterOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                HBox cellBox = new HBox(4);
                cellBox.setAlignment(Pos.CENTER_LEFT);

                // Indentation for nested items
                int depth = Math.max(0, item.depth() - 1);
                if (depth > 0) {
                    Region indent = new Region();
                    indent.setPrefWidth(depth * 14.0);
                    cellBox.getChildren().add(indent);
                }

                if (item.wildcard()) {
                    Label nameLabel = new Label("* (All Fields)");
                    nameLabel.setStyle("-fx-font-weight: 600;");
                    cellBox.getChildren().add(nameLabel);
                } else {
                    String fieldName = item.fieldName();
                    int lastDot = fieldName.lastIndexOf('.');
                    if (lastDot >= 0) {
                        Label prefixLabel = new Label(fieldName.substring(0, lastDot + 1));
                        prefixLabel.getStyleClass().add("muted");

                        Label leafLabel = new Label(fieldName.substring(lastDot + 1));
                        cellBox.getChildren().addAll(prefixLabel, leafLabel);
                    } else {
                        Label nameLabel = new Label(fieldName);
                        cellBox.getChildren().add(nameLabel);
                    }

                    if (item.typeDisplay() != null && !item.typeDisplay().isBlank()) {
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Label typeLabel = new Label(item.typeDisplay());
                        typeLabel.getStyleClass().add("muted");
                        typeLabel.setStyle("-fx-font-size: 11px;");

                        cellBox.getChildren().addAll(spacer, typeLabel);
                    }
                }

                setGraphic(cellBox);
                setText(null);
            }
        });
    }

    private void setupEditorListeners() {
        TextField editor = comboBox.getEditor();

        editor.textProperty().addListener((_, _, newText) -> {
            if (isUpdatingSelection) return;

            validateCurrentText();

            String query = newText == null ? "" : newText.trim();
            filteredList.setPredicate(opt -> FieldSuggestionMatcher.matches(query, opt.fieldName(), opt.wildcard()));

            // Update matching model value if an exact match exists or if typed custom path
            FilterOption matched = comboBox.getConverter().fromString(newText);
            isUpdatingSelection = true;
            try {
                comboBox.setValue(matched);
            } finally {
                isUpdatingSelection = false;
            }

            if (!comboBox.isShowing() && editor.isFocused() && !query.isEmpty()) {
                comboBox.show();
            }
        });

        comboBox.valueProperty().addListener((_, _, newOpt) -> {
            if (isUpdatingSelection) return;
            isUpdatingSelection = true;
            try {
                if (newOpt != null) {
                    editor.setText(newOpt.wildcard() ? "* (All Fields)" : newOpt.fieldName());
                }
                validateCurrentText();
                // Reset filter predicate to show all items on next dropdown open
                filteredList.setPredicate(_ -> true);
            } finally {
                isUpdatingSelection = false;
            }
        });

        // Show all options when gaining focus or opening dropdown
        comboBox.setOnShowing(_ -> {
            String text = editor.getText();
            if (text == null || text.isBlank() || (getValue() != null && text.equals(comboBox.getConverter().toString(getValue())))) {
                filteredList.setPredicate(_ -> true);
            }
        });
    }
}

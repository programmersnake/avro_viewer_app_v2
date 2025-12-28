package com.dkostin.avro_viewer.app.ui.component;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for configuring table columns and displaying records
 */
public class TableViewPresenter {
    private final TableView<Map<String, Object>> tableView;
    private final JsonRowViewerWindow jsonViewer;
    private Schema currentSchema;  // last displayed scheme (to avoid unnecessary column rearrangement)

    public TableViewPresenter(TableView<Map<String, Object>> tableView, JsonRowViewerWindow jsonViewer) {
        this.tableView = tableView;
        this.jsonViewer = jsonViewer;
        configureRowEvents();  // bind event handlers (double click, Enter)
    }

    /**
     * Configures events for table rows: double-click or Enter opens JSON view
     */
    private void configureRowEvents() {
        // Double-click on a line
        tableView.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Map<String, Object> recordMap = row.getItem();
                    // Open JSON window with record data
                    jsonViewer.openJsonWindow(recordMap, tableView.getScene());
                }
            });
            return row;
        });
        // Pressing Enter on the selected line
        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Map<String, Object> recordMap = tableView.getSelectionModel().getSelectedItem();
                if (recordMap != null) {
                    jsonViewer.openJsonWindow(recordMap, tableView.getScene());
                }
                event.consume();
            }
        });
    }

    /**
     * Updates the table structure to the given Avro schema (creates columns as needed)
     */
    private void configureColumns(Schema schema) {
        tableView.getColumns().clear();
        if (schema == null) {
            currentSchema = null;
            return;
        }
        for (Schema.Field field : schema.getFields()) {
            String fieldName = field.name();
            TableColumn<Map<String, Object>, String> col = getMapStringTableColumn(fieldName);
            tableView.getColumns().add(col);
        }
        currentSchema = schema;
    }

    private static TableColumn<Map<String, Object>, String> getMapStringTableColumn(String fieldName) {
        TableColumn<Map<String, Object>, String> col = new TableColumn<>(fieldName);
        // Setting the display of values: null -> "", otherwise toString()
        col.setCellValueFactory(cellData -> {
            Object value = cellData.getValue().get(fieldName);
            return new ReadOnlyStringWrapper(value == null ? "" : value.toString());
        });
        // Set column width to content (minimum 120 px)
        col.setPrefWidth(Math.max(120, fieldName.length() * 12.0));
        return col;
    }

    /**
     * Converts a GenericRecord list to an ObservableList<Map> for TableView
     */
    private ObservableList<Map<String, Object>> recordsToItems(List<GenericRecord> records) {
        ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
        if (currentSchema == null) return items;
        for (GenericRecord record : records) {
            items.add(recordToMap(record));
        }
        return items;
    }

    /**
     * Helper method: converts a single GenericRecord into a Map for display
     */
    private Map<String, Object> recordToMap(GenericRecord record) {
        Map<String, Object> rowMap = new LinkedHashMap<>();
        for (Schema.Field field : currentSchema.getFields()) {
            rowMap.put(field.name(), record.get(field.name()));
        }
        return rowMap;
    }

    /**
     * Updates the data displayed in the table.
     *
     * @param records list of Avro records to display
     * @param schema  Avro schema of these records (used for columns)
     */
    public void updateTableData(List<GenericRecord> records, Schema schema) {
        // Rebuild columns if schema has changed
        if (currentSchema == null || !currentSchema.equals(schema)) {
            configureColumns(schema);
        }
        // Fill the table with data
        ObservableList<Map<String, Object>> items = recordsToItems(records);
        tableView.setItems(items);
    }
}

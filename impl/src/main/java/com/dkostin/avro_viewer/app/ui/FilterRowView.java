package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.filter.FilterRowModel;
import com.dkostin.avro_viewer.app.filter.MatchOperation;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public record FilterRowView(
        HBox root,
        ComboBox<String> fieldCombo,
        ComboBox<MatchOperation> opCombo,
        TextField valueField,
        Button removeBtn,
        FilterRowModel model
) {}

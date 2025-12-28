package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.filter.MatchOperation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public record FilterRowRefs(ComboBox<String> fieldCombo,
                            ComboBox<MatchOperation> opCombo,
                            TextField valueField) {
}

package com.dkostin.avro_viewer.app.ui.component;

import javafx.scene.control.Alert;

public class ErrorAlert {

    public static void showError(String title, Throwable ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(ex.getClass().getSimpleName());
        a.setContentText(ex.getMessage());
        a.showAndWait();
    }

}

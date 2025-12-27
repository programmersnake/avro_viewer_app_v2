package com.dkostin.avro_viewer.app.ui;

import com.dkostin.avro_viewer.app.data.JsonSerializer;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.dkostin.avro_viewer.app.ui.ErrorAlert.showError;

@RequiredArgsConstructor
public class JsonRowViewerWindow {

    private Stage jsonStage;
    private TextArea jsonArea;

    public void openJsonWindow(Map<String, Object> row, Scene ownerScene) {
        try {
            String json = JsonSerializer.toJsonSafe(row);

            if (jsonStage == null) {
                jsonArea = new TextArea();
                jsonArea.setEditable(false);
                jsonArea.setWrapText(false);

                var root = getBorderPane();

                jsonStage = new javafx.stage.Stage();
                jsonStage.setTitle("Record JSON");

                // Owner is main window
                var owner = ownerScene.getWindow();
                jsonStage.initOwner(owner);
                jsonStage.initModality(javafx.stage.Modality.NONE);

                Scene s = new Scene(root, 820, 620);
                s.getStylesheets().setAll(ownerScene.getStylesheets());

                jsonStage.setScene(s);
            }

            jsonArea.setText(json);
            jsonStage.show();
            jsonStage.toFront();

        } catch (Exception ex) {
            showError("JSON view failed", ex);
        }
    }

    private BorderPane getBorderPane() {
        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(_ -> {
            var content = new ClipboardContent();
            content.putString(jsonArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        ToolBar toolBar = new ToolBar(copyBtn);

        var root = new BorderPane(jsonArea);
        root.setTop(toolBar);
        return root;
    }

    public void syncStyles(ObservableList<String> styles) {
        if (jsonStage != null && jsonStage.getScene() != null) {
            jsonStage.getScene().getStylesheets().setAll(styles);
        }
    }

}

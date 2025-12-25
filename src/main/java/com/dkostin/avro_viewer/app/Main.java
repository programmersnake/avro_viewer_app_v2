package com.dkostin.avro_viewer.app;

//import com.dkostin.avro_viewer.app.ui.Controller;

import com.dkostin.avro_viewer.app.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dkostin/avro_viewer/app/ui/main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 720);

        // Default theme: dark
        scene.getStylesheets().add(getClass().getResource("/com/dkostin/avro_viewer/app/ui/css/theme-dark.css").toExternalForm());

        MainController controller = loader.getController();
        controller.initTheme(scene);

        stage.setTitle("Avro Viewer - JavaFX UI");
        stage.setScene(scene);
        stage.show();
    }

    static void main(String[] args) {
        launch(args);
    }

}
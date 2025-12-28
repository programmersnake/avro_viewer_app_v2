package com.dkostin.avro_viewer.app;

import com.dkostin.avro_viewer.app.config.AppContext;
import com.dkostin.avro_viewer.app.config.AppControllerFactory;
import com.dkostin.avro_viewer.app.ui.controller.MainController;
import com.dkostin.avro_viewer.app.domain.Theme;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        AppContext ctx = new AppContext();
        AppControllerFactory controllerFactory = new AppControllerFactory(ctx);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/dkostin/avro_viewer/app/ui/main.fxml"));
        loader.setControllerFactory(controllerFactory::create);

        Scene scene = new Scene(loader.load(), 1280, 720);

        // Default theme: dark
        scene.getStylesheets().add(getClass().getResource(Theme.DARK.getCssPath()).toExternalForm());

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
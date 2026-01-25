module com.dkostin.avro_viewer.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires static lombok;
    requires org.apache.avro;
    requires com.fasterxml.jackson.databind;

    opens com.dkostin.avro_viewer.app.ui to javafx.fxml;
    opens com.dkostin.avro_viewer.app.config to javafx.fxml;

    exports com.dkostin.avro_viewer.app;
    opens com.dkostin.avro_viewer.app.ui.component to javafx.fxml;
    opens com.dkostin.avro_viewer.app.ui.main to javafx.fxml;
    opens com.dkostin.avro_viewer.app.domain.state to javafx.fxml;
    opens com.dkostin.avro_viewer.app.domain.model to javafx.fxml;
}

module com.dkostin.avro_viewer.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;
    requires org.apache.avro;

    opens com.dkostin.avro_viewer.app.ui to javafx.fxml;

    exports com.dkostin.avro_viewer.app;
}

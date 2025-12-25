package com.dkostin.avro_viewer.app.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class RowItem {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty country = new SimpleStringProperty();
    private final IntegerProperty salary = new SimpleIntegerProperty();

    public RowItem(int id, String title, String country, int salary) {
        this.id.set(id);
        this.title.set(title);
        this.country.set(country);
        this.salary.set(salary);
    }

    public IntegerProperty idProperty() { return id; }
    public StringProperty titleProperty() { return title; }
    public StringProperty countryProperty() { return country; }
    public IntegerProperty salaryProperty() { return salary; }
}

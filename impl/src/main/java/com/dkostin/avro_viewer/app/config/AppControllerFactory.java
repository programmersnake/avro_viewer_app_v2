package com.dkostin.avro_viewer.app.config;

import com.dkostin.avro_viewer.app.ui.main.MainController;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class AppControllerFactory {

    private final Map<Class<?>, Supplier<?>> registry = new HashMap<>();

    public AppControllerFactory(AppContext ctx) {
        registry.put(MainController.class, () -> new MainController(ctx));
    }

    public Object create(Class<?> type) {
        Supplier<?> supplier = registry.get(type);
        if (supplier != null) {
            return supplier.get();
        }

        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create controller: " + type.getName(), e);
        }
    }
}

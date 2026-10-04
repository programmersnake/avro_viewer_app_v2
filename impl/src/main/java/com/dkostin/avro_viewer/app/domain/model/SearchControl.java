package com.dkostin.avro_viewer.app.domain.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public record SearchControl(AtomicBoolean stopRequested, Consumer<SearchProgress> progressConsumer) {

    public SearchControl {
        if (stopRequested == null) {
            stopRequested = new AtomicBoolean(false);
        }
        if (progressConsumer == null) {
            progressConsumer = p -> {};
        }
    }

    public static SearchControl noop() {
        return new SearchControl(new AtomicBoolean(false), p -> {});
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public void reportProgress(SearchProgress progress) {
        if (progress != null) {
            progressConsumer.accept(progress);
        }
    }
}

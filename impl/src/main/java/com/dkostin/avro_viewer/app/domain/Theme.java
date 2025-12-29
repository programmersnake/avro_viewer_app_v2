package com.dkostin.avro_viewer.app.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Theme {

    BASE("/com/dkostin/avro_viewer/app/ui/css/theme-base.css"),
    DARK("/com/dkostin/avro_viewer/app/ui/css/theme-dark.css"),
    LIGHT("/com/dkostin/avro_viewer/app/ui/css/theme-light.css");

    private final String cssPath;
}

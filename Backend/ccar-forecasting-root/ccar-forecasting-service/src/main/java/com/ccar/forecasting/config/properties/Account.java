package com.ccar.forecasting.config.properties;

import jakarta.validation.constraints.NotBlank;

/** One account (measure) in display order. */
public class Account {

    @NotBlank
    private String id;
    @NotBlank
    private String measure;

    public Account() {
    }

    public Account(String id, String measure) {
        this.id = id;
        this.measure = measure;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMeasure() {
        return measure;
    }

    public void setMeasure(String measure) {
        this.measure = measure;
    }
}

package com.ccar.forecasting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/** One direct child of the selected node, with its totals. */
@JsonPropertyOrder({"id", "name", "hType", "level", "childCount", "data_rows"})
public record BreakdownItem(
        long id,
        String name,
        @JsonProperty("hType") String hType,
        int level,
        int childCount,
        @JsonProperty("data_rows") List<PeriodValues> dataRows) {
}

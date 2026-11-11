package com.forecast.ccar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Reads {@code forecast_ccar} through the pooled {@link KdbTemplate} (kdb-client-starter).
 * <p>
 * No try/catch-log-rethrow here: KDB failures surface as the starter's typed {@code KdbException}s and are
 * mapped to HTTP (503/504/502) and logged once by {@code GlobalExceptionHandler}.
 */
@Service
public class KdbService {

    private static final Logger log = LoggerFactory.getLogger(KdbService.class);

    static final String FORECAST_QUERY = "select from forecast_ccar";

    private final KdbTemplate kdbTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public KdbService(KdbTemplate kdbTemplate) {
        this.kdbTemplate = kdbTemplate;
    }

    /**
     * Called from the controller. Returns forecast_ccar data as a JSON string.
     */
    public String getForecastCcarJson() {
        log.info("Fetching forecast_ccar data from KDB");

        c.Flip result = kdbTemplate.execute(FORECAST_QUERY, c.Flip.class);
        List<Map<String, Object>> rows = flipToRows(result);
        log.info("Query returned {} row(s)", rows.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("forecast_ccar", Collections.singletonMap("data", rows));

        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            log.debug("JSON response size: {} characters", json.length());
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise forecast_ccar result to JSON", e);
        }
    }

    /**
     * Converts a KDB table (c.Flip) into a list of row maps.
     */
    private List<Map<String, Object>> flipToRows(c.Flip flip) {
        String[] cols = flip.x;
        Object[] data = flip.y;

        List<Map<String, Object>> rows = new ArrayList<>();
        if (data.length == 0) {
            log.warn("KDB result has no columns, returning empty list");
            return rows;
        }

        int rowCount = Array.getLength(data[0]);
        log.debug("Converting {} row(s) with {} column(s): {}", rowCount, cols.length, Arrays.toString(cols));

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int j = 0; j < cols.length; j++) {
                Object value = Array.get(data[j], i);
                if (value instanceof java.util.Date
                        || value instanceof java.time.temporal.TemporalAccessor) {
                    value = value.toString();
                }
                row.put(cols[j], value);
            }
            rows.add(row);
        }
        return rows;
    }
}

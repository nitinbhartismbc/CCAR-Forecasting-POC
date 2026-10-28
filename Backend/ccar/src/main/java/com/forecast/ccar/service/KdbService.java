package com.forecast.ccar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import com.forecast.ccar.kx.c;

@Service
public class KdbService {

    private static final Logger log = LoggerFactory.getLogger(KdbService.class);

    @Value("${kdb.host}")
    private String host;

    @Value("${kdb.port}")
    private int port;

    @Value("${kdb.username}")
    private String username;

    @Value("${kdb.password}")
    private String password;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Opens a new connection to the KDB process.
     */
    private c getConnection() throws IOException, c.KException {
        log.debug("Opening KDB connection to {}:{} as user '{}'", host, port, username);
        long start = System.currentTimeMillis();
        c conn = new c(host, port, username + ":" + password);
        log.info("Connected to KDB at {}:{} in {} ms", host, port, System.currentTimeMillis() - start);
        return conn;
    }

    /**
     * Called from the controller. Returns forecast_ccar data as a JSON string.
     */
    public String getForecastCcarJson() {
        final String query = "select from forecast_ccar";
        log.info("Fetching forecast_ccar data from KDB");
        long start = System.currentTimeMillis();

        c conn = null;
        try {
            conn = getConnection();

            log.debug("Executing KDB query: {}", query);
            Object result = conn.k(query);
            List<Map<String, Object>> rows = flipToRows((c.Flip) result);
            log.info("Query returned {} row(s)", rows.size());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("forecast_ccar", Collections.singletonMap("data", rows));

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            log.debug("JSON response size: {} characters", json.length());
            log.info("forecast_ccar fetch completed in {} ms", System.currentTimeMillis() - start);
            return json;
        } catch (c.KException e) {
            log.error("KDB returned an error for query '{}': {}", query, e.getMessage(), e);
            throw new RuntimeException("KDB query failed: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Unable to communicate with KDB at {}:{}: {}", host, port, e.getMessage(), e);
            throw new RuntimeException("KDB connection error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while fetching data from KDB: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch data from KDB: " + e.getMessage(), e);
        } finally {
            closeQuietly(conn);
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

    private void closeQuietly(c conn) {
        if (conn != null) {
            try {
                conn.close();
                log.debug("KDB connection closed");
            } catch (IOException e) {
                log.warn("Failed to close KDB connection cleanly: {}", e.getMessage());
            }
        }
    }
}
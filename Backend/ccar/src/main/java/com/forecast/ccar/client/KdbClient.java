package com.forecast.ccar.client;


import java.io.IOException;
import java.util.Arrays;

import com.forecast.ccar.kx.c;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.*;

public class KdbClient {
    static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        c conn = null;
        try {
            // host, port, optional "user:password"
            //conn = new c("localhost", 5001);
            conn = new c("192.168.11.200", 5001, "test:test@123");

            // Run a q query
            c.Flip result = (c.Flip) conn.k("select from forecast_ccar");

            // Column names
            String[] cols = result.x;
            // Column data (array of arrays)
            Object[] data = result.y;

            System.out.println(Arrays.toString(cols));

            List<Map<String, Object>> rows = new ArrayList<>();
            int rowCount = Array.getLength(data[0]);
            for (int i = 0; i < rowCount; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int j = 0; j < cols.length; j++) {
                    Object value = Array.get(data[j], i);
                    if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) {
                        value = value.toString();
                    }
                    row.put(cols[j], value);
                }
                rows.add(row);
            }

            // Parent tag -> data node
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("forecast_ccar", Collections.singletonMap("data", rows));

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            System.out.println(json);
            }
        catch (c.KException | IOException ex) {
            throw new RuntimeException(ex);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
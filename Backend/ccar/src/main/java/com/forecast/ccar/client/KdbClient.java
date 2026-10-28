package com.forecast.ccar.client;


import java.util.Arrays;
import com.forecast.ccar.kx.c;

public class KdbClient {
    public static void main(String[] args) {
        c conn = null;
        try {
            // host, port, optional "user:password"
            //conn = new c("localhost", 5001);
            conn = new c("192.168.11.200", 5001, "test:test@123");

            // Run a q query
            c.Flip result = (c.Flip) conn.k("select from trade");

            // Column names
            String[] cols = result.x;
            // Column data (array of arrays)
            Object[] data = result.y;

            System.out.println(Arrays.toString(cols));

            int rowCount = java.lang.reflect.Array.getLength(data[0]);
            for (int i = 0; i < rowCount; i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < cols.length; j++) {
                    row.append(java.lang.reflect.Array.get(data[j], i)).append("\t");
                }
                System.out.println(row);
            }

            // Simple scalar result
            long count = (Long) conn.k("count trade");
            System.out.println("Count is "+count);

            // Filter with a query
            c.Flip aapl = (c.Flip) conn.k("select from trade where sym=`AAPL");
            System.out.println("Filter with AAPL"+aapl);

            // Async (fire and forget), e.g. insert
            conn.ks("`trade insert (`TSLA; 250.0; 75)");
            long countAfterInsert = (Long) conn.k("count trade");
            System.out.println("Count After Insert is "+countAfterInsert);


        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
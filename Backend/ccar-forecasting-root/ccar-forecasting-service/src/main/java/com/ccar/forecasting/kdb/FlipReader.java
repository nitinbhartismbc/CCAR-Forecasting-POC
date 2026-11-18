package com.ccar.forecasting.kdb;

import com.kx.c;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Column access to a KDB table ({@link c.Flip}: column names in {@code x}, column vectors in {@code y}) with
 * case-insensitive column lookup, plus conversions for single values. Use {@link c#at(Object, int)} to read a cell:
 * it returns {@code null} for q nulls ({@code 0N}, {@code 0n}, {@code `}).
 */
public final class FlipReader {

    private final Map<String, Object> columns = new HashMap<>();
    private final int rowCount;

    public FlipReader(c.Flip flip) {
        for (int i = 0; i < flip.x.length; i++) {
            columns.put(flip.x[i].toUpperCase(Locale.ROOT), flip.y[i]);
        }
        rowCount = flip.y.length == 0 ? 0 : Array.getLength(flip.y[0]);
    }

    public int rowCount() {
        return rowCount;
    }

    /** Column vector; {@link IllegalStateException} if the table has no such column. */
    public Object column(String name) {
        Object column = columns.get(name.toUpperCase(Locale.ROOT));
        if (column == null) {
            throw new IllegalStateException("no column " + name);
        }
        return column;
    }

    /** Any KDB integral type (short/int/long); rejects nulls and non-numbers. */
    public static long requireLong(Object value, String column) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException(column + " is " + (value == null ? "null" : "not numeric: " + value));
    }

    /** Symbol (String), q string (char[]) or anything else as trimmed text; null stays null. */
    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        return (value instanceof char[] chars ? new String(chars) : value.toString()).trim();
    }

    /** A q vector (e.g. the result of {@code exec distinct ...}) as a list, q nulls dropped. */
    public static List<Object> list(Object vector) {
        if (vector == null || !vector.getClass().isArray()) {
            return vector == null ? List.of() : List.of(vector);
        }
        int n = Array.getLength(vector);
        List<Object> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Object v = c.at(vector, i);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }
}

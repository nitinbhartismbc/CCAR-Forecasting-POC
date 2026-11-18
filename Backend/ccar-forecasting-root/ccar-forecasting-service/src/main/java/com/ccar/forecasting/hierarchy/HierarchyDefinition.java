package com.ccar.forecasting.hierarchy;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.kdb.QIdentifiers;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where one hierarchy type is stored in KDB and how the fact tables point at it:
 * <pre>
 *   table  &lt;TYPE&gt;_HIERARCHY : &lt;P&gt;_ID, &lt;P&gt;_NAME, LEVEL_ID, &lt;P&gt;_ID_LEVEL1 .. &lt;P&gt;_ID_LEVELn
 *   fact column               : &lt;P&gt;_ID (node the fact row is booked against)
 * </pre>
 * Each hierarchy row carries the full path from the root, so {@code <P>_ID_LEVEL<k>} of any node is its ancestor at
 * level k (the node itself when k = its own level).
 */
public record HierarchyDefinition(String type, String table, String idColumn, String nameColumn, String levelColumn,
                                  String levelColumnPrefix, String factColumn) {

    public static final String LEVEL_COLUMN = "LEVEL_ID";

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");

    public static HierarchyDefinition of(String type, String table, String columnPrefix, String factColumn) {
        String t = normalizeType(type);
        String tbl = isBlank(table) ? t + "_HIERARCHY" : table.trim();
        String prefix = isBlank(columnPrefix) ? t : columnPrefix.trim().toUpperCase(Locale.ROOT);
        String fact = isBlank(factColumn) ? prefix + "_ID" : factColumn.trim();
        QIdentifiers.requireTable(tbl, "hierarchy " + t + " table");
        QIdentifiers.requireColumn(prefix, "hierarchy " + t + " column-prefix");
        QIdentifiers.requireColumn(fact, "hierarchy " + t + " fact-column");
        return new HierarchyDefinition(t, tbl, prefix + "_ID", prefix + "_NAME", LEVEL_COLUMN,
                prefix + "_ID_LEVEL", fact);
    }

    /** Path column holding the ancestor at {@code level}, e.g. {@code BU_ID_LEVEL3}. */
    public String levelColumn(int level) {
        return levelColumnPrefix + level;
    }

    /**
     * Upper-cases and validates a hierarchy type coming from a request or configuration.
     *
     * @throws CcarException 400 INVALID_PARAMETER if it is not a valid type name
     */
    public static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPE.matcher(t).matches()) {
            throw new CcarException(ErrorCode.INVALID_PARAMETER,
                    "Hierarchy type must be 1-30 letters, digits or '_' starting with a letter, e.g. BU or GEO")
                    .with("parameter", "hierarchyType");
        }
        return t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

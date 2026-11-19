package com.forecast.ccar.model;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where and how one hierarchy type is stored in KDB+. Every hierarchy table has the same parent-pointer layout,
 * only the names differ (extra columns such as {@code CHILD_COUNT} are ignored, child counts are computed):
 * <pre>
 *   &lt;PREFIX&gt;_ID, &lt;PREFIX&gt;_NAME, LEVEL_ID, PARENT_&lt;PREFIX&gt;_ID
 *   BU : BU_HIER  -> BU_ID,  BU_NAME,  LEVEL_ID, PARENT_BU_ID
 *   GEO: GEO_HIER -> GEO_ID, GEO_NAME, LEVEL_ID, PARENT_GEO_ID
 * </pre>
 * By convention type {@code XYZ} lives in table {@code XYZ_HIERARCHY} with column prefix {@code XYZ}; both can be
 * overridden per type with {@code ccar.hierarchy.types.<TYPE>.table / .column-prefix}.
 *
 * @param type              upper-case hierarchy type, e.g. {@code BU}
 * @param table             KDB table name
 * @param idColumn          node id column, e.g. {@code BU_ID}
 * @param nameColumn        node name column, e.g. {@code BU_NAME}
 * @param levelColumn       level column ({@code LEVEL_ID}, 1 = root)
 * @param parentColumn      parent node id column, e.g. {@code PARENT_BU_ID} (ignored for the root, level 1)
 */
public record HierarchyDefinition(String type, String table, String idColumn, String nameColumn, String levelColumn,
                                  String parentColumn) {

    public static final String LEVEL_COLUMN = "LEVEL_ID";
    public static final String TABLE_SUFFIX = "_HIERARCHY";

    /** Hierarchy types: letters, digits and '_' only - the type becomes part of a KDB table name. */
    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_.]*");

    /**
     * @param table        null/blank = {@code <TYPE>_HIERARCHY}
     * @param columnPrefix null/blank = the type itself
     */
    public static HierarchyDefinition of(String type, String table, String columnPrefix) {
        String t = normalizeType(type);
        String tbl = isBlank(table) ? t + TABLE_SUFFIX : table.trim();
        String prefix = isBlank(columnPrefix) ? t : columnPrefix.trim().toUpperCase(Locale.ROOT);
        if (!TABLE.matcher(tbl).matches()) {
            throw new IllegalArgumentException("Invalid KDB table name for hierarchy " + t + ": " + tbl);
        }
        if (!IDENTIFIER.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Invalid column prefix for hierarchy " + t + ": " + prefix);
        }
        return new HierarchyDefinition(t, tbl, prefix + "_ID", prefix + "_NAME", LEVEL_COLUMN, "PARENT_" + prefix + "_ID");
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
                    .with("parameter", "type");
        }
        return t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

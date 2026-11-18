package com.ccar.forecasting.kdb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds q literals and validates identifiers that are spliced into q query text. Everything reaching a query is
 * either a validated identifier from configuration, a number, a {@link LocalDate}, or a value matched against
 * {@link #SAFE_VALUE} - so request input can never change the shape of a query.
 */
public final class QIdentifiers {

    private static final Pattern TABLE = Pattern.compile("[A-Za-z][A-Za-z0-9_.]*");
    private static final Pattern COLUMN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    /** Request values used as symbols (portfolio ids): no quotes, backslashes, spaces or q operators. */
    public static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    private static final DateTimeFormatter Q_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private QIdentifiers() {
    }

    public static String requireTable(String name, String what) {
        if (name == null || !TABLE.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid KDB table name for " + what + ": " + name);
        }
        return name;
    }

    public static String requireColumn(String name, String what) {
        if (name == null || !COLUMN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid KDB column name for " + what + ": " + name);
        }
        return name;
    }

    /** {@code 2026.09.30} */
    public static String date(LocalDate date) {
        return date.format(Q_DATE);
    }

    /** {@code `$"PORT_0001"} - works for any {@link #SAFE_VALUE}, including ones with '-' or '.'. */
    public static String symbol(String value) {
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe symbol value: " + value);
        }
        return "`$\"" + value + "\"";
    }

    /** {@code (),1 2 3} - a long list even for one element. */
    public static String longList(Collection<Long> values) {
        if (values.isEmpty()) {
            return "`long$()";
        }
        return "()," + values.stream().map(String::valueOf).collect(Collectors.joining(" "));
    }
}

package com.ccar.forecasting.repository;

import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.constant.ForecastingConstant;
import com.ccar.forecasting.kdb.FlipReader;
import com.ccar.forecasting.kdb.QIdentifiers;
import com.ccar.forecasting.model.Portfolio;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Portfolios (from the portfolio master table) and T0 dates (from the configured reference table), read from KDB. */
@Repository
public class ReferenceDataRepository {

    private final KdbTemplate kdbTemplate;
    private final String table;
    private final String t0DateColumn;
    private final String portfolioMasterTable;
    private final String portfolioIdColumn;
    private final String portfolioNameColumn;

    public ReferenceDataRepository(KdbTemplate kdbTemplate, ForecastingProperties properties) {
        this.kdbTemplate = kdbTemplate;
        this.table = QIdentifiers.requireTable(properties.getReferenceTable(), "reference-table");
        this.t0DateColumn = QIdentifiers.requireColumn(ForecastingConstant.FACT_T0_DATE_COLUMN,
                "fact.t0-date-column");
        this.portfolioMasterTable = QIdentifiers.requireTable(properties.getPortfolioMaster().getTable(),
                "portfolio-master.table");
        this.portfolioIdColumn = QIdentifiers.requireColumn(properties.getPortfolioMaster().getIdColumn(),
                "portfolio-master.id-column");
        this.portfolioNameColumn = QIdentifiers.requireColumn(properties.getPortfolioMaster().getNameColumn(),
                "portfolio-master.name-column");
    }

    /** Portfolios (id + display name), read from the portfolio master table, ascending by id. */
    public List<Portfolio> portfolios() {
        String query = "`" + portfolioIdColumn + " xasc 0!select " + portfolioIdColumn + "," + portfolioNameColumn
                + " from " + portfolioMasterTable;
        c.Flip flip = kdbTemplate.execute(query, c.Flip.class);
        FlipReader rows = new FlipReader(flip);
        Object ids = rows.column(portfolioIdColumn);
        Object names = rows.column(portfolioNameColumn);

        List<Portfolio> result = new ArrayList<>(rows.rowCount());
        for (int row = 0; row < rows.rowCount(); row++) {
            String id = FlipReader.text(c.at(ids, row));
            if (id == null) {
                continue;
            }
            String name = FlipReader.text(c.at(names, row));
            result.add(new Portfolio(id, name == null ? id : name));
        }
        return result;
    }

    /** Distinct T0 dates with data, latest first. */
    public List<LocalDate> t0Dates() {
        Object result = kdbTemplate.execute("desc exec distinct " + t0DateColumn + " from " + table);

        return FlipReader.list(result).stream()
                .filter(Objects::nonNull)
                .map(ReferenceDataRepository::toLocalDate)
                .collect(Collectors.toList());
    }

    /** Text form used when the T0 column is a symbol/string, e.g. {@code 30-Sep-26}. */
    private static final DateTimeFormatter DD_MMM_YY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yy")
            .toFormatter(Locale.ENGLISH);

    /**
     * A q {@code date} arrives as {@link LocalDate} (or {@link java.sql.Date} with older kx drivers); a symbol or
     * string column arrives as text, either ISO ({@code 2026-09-30}) or {@code dd-MMM-yy} ({@code 30-Sep-26}).
     */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        String text = FlipReader.text(value);
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException iso) {
            return LocalDate.parse(text, DD_MMM_YY);
        }
    }
}


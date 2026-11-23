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

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);


        return FlipReader.list(result).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(date -> LocalDate.parse(date, formatter))
                .collect(Collectors.toList());
    }
}


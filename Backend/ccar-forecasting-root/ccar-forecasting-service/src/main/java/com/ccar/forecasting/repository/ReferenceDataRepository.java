package com.ccar.forecasting.repository;

import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.kdb.FlipReader;
import com.ccar.forecasting.kdb.QIdentifiers;
import com.iris.kdb.service.KdbTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Portfolios and T0 dates, read from the configured reference table (by default the forecast table). */
@Repository
public class ReferenceDataRepository {

    private final KdbTemplate kdbTemplate;
    private final String table;
    private final String portfolioColumn;
    private final String t0DateColumn;

    public ReferenceDataRepository(KdbTemplate kdbTemplate, ForecastingProperties properties) {
        this.kdbTemplate = kdbTemplate;
        this.table = QIdentifiers.requireTable(properties.getReferenceTable(), "reference-table");
        this.portfolioColumn = QIdentifiers.requireColumn(properties.getFact().getPortfolioColumn(),
                "fact.portfolio-column");
        this.t0DateColumn = QIdentifiers.requireColumn(properties.getFact().getT0DateColumn(),
                "fact.t0-date-column");
    }

    /** Distinct portfolio ids, ascending. */
    public List<String> portfolioIds() {
        Object result = kdbTemplate.execute("asc exec distinct " + portfolioColumn + " from " + table);
        return FlipReader.list(result).stream().map(FlipReader::text).filter(Objects::nonNull).toList();
    }

    /** Distinct T0 dates with data, latest first. */
    public List<LocalDate> t0Dates() {
        Object result = kdbTemplate.execute("desc exec distinct " + t0DateColumn + " from " + table);
        return FlipReader.list(result).stream()
                .filter(LocalDate.class::isInstance)
                .map(LocalDate.class::cast)
                .toList();
    }
}

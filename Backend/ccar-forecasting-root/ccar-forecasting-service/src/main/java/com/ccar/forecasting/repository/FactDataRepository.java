package com.ccar.forecasting.repository;

import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.hierarchy.HierarchyDefinition;
import com.ccar.forecasting.kdb.FlipReader;
import com.ccar.forecasting.kdb.QIdentifiers;
import com.ccar.forecasting.model.DataSet;
import com.iris.kdb.exception.KdbQueryException;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads forecast / actuals / variance totals from KDB, aggregated in KDB per hierarchy node and account.
 * <p>
 * For nodes {@code ids} at hierarchy level {@code L}, every fact row is joined to the hierarchy on its node column
 * and grouped by its ancestor at level L ({@code <P>_ID_LEVEL<L>}), so a node's total covers its whole subtree:
 * <pre>
 * 0!select T0:sum T0, N__T0:sum not null T0, T_1:sum T_1, N__T_1:sum not null T_1, ...
 *    by NODE__ID, ACCOUNT_ID
 *    from ej[`BU_ID;
 *            select from CCAR_FORECAST where T0_DATE=2026.09.30, PORTFOLIO_ID=`$"PORT_0001";
 *            select BU_ID:BU_ID, NODE__ID:BU_ID_LEVEL2 from BU_HIERARCHY where BU_ID_LEVEL2 in (),2 109]
 * </pre>
 * KDB's {@code sum} treats nulls as 0, so each period also returns its count of non-null values ({@code N__*});
 * a period with no data at all comes back as {@code null}.
 */
@Repository
public class FactDataRepository {

    private static final Logger log = LoggerFactory.getLogger(FactDataRepository.class);

    static final String NODE_COLUMN = "NODE__ID";
    static final String COUNT_PREFIX = "N__";

    private final KdbTemplate kdbTemplate;
    private final ForecastingProperties.Fact fact;
    private final Map<DataSet, String> tables = new EnumMap<>(DataSet.class);
    /** Column of period n (0 = T0). */
    private final List<String> periodColumns;

    public FactDataRepository(KdbTemplate kdbTemplate, ForecastingProperties properties) {
        this.kdbTemplate = kdbTemplate;
        this.fact = properties.getFact();
        QIdentifiers.requireColumn(fact.getAccountColumn(), "fact.account-column");
        QIdentifiers.requireColumn(fact.getPortfolioColumn(), "fact.portfolio-column");
        QIdentifiers.requireColumn(fact.getT0DateColumn(), "fact.t0-date-column");
        for (DataSet ds : DataSet.values()) {
            ForecastingProperties.DataSetTable cfg = properties.getDatasets().get(ds.path());
            if (cfg == null || cfg.getTable() == null) {
                throw new IllegalArgumentException("Missing ccar.forecasting.datasets." + ds.path() + ".table");
            }
            tables.put(ds, QIdentifiers.requireTable(cfg.getTable().trim(), "datasets." + ds.path()));
        }
        if (properties.getPeriodCount() < 0) {
            throw new IllegalArgumentException("ccar.forecasting.period-count must be >= 0");
        }
        List<String> cols = new ArrayList<>(properties.getPeriodCount() + 1);
        cols.add(QIdentifiers.requireColumn(fact.getT0Column(), "fact.t0-column"));
        for (int n = 1; n <= properties.getPeriodCount(); n++) {
            cols.add(QIdentifiers.requireColumn(fact.getPeriodColumnPrefix() + n, "fact.period-column-prefix"));
        }
        this.periodColumns = List.copyOf(cols);
    }

    public int periodCount() {
        return periodColumns.size();
    }

    /**
     * Totals per node and account.
     *
     * @param level       hierarchy level of every node in {@code nodeIds}
     * @param portfolioId portfolio filter, or null for all portfolios (must match {@link QIdentifiers#SAFE_VALUE})
     * @return node id -> account id -> one value per period (null = no data), only for node/account pairs with rows
     */
    public Map<Long, Map<String, Number[]>> totals(DataSet dataSet, HierarchyDefinition hierarchy, int level,
                                                   Collection<Long> nodeIds, LocalDate t0Date, String portfolioId) {
        if (nodeIds.isEmpty()) {
            return Map.of();
        }
        String query = buildQuery(dataSet, hierarchy, level, nodeIds, t0Date, portfolioId);
        log.debug("{} totals query: {}", dataSet, query);
        c.Flip flip = kdbTemplate.execute(query, c.Flip.class);
        try {
            return read(flip);
        } catch (IllegalStateException e) {
            // the query fixes the result's columns, so this means the fact table has an unexpected layout/type
            throw new KdbQueryException("Unexpected " + dataSet + " result layout: " + e.getMessage());
        }
    }

    String buildQuery(DataSet dataSet, HierarchyDefinition h, int level, Collection<Long> nodeIds,
                      LocalDate t0Date, String portfolioId) {
        StringBuilder q = new StringBuilder("0!select ");
        for (int i = 0; i < periodColumns.size(); i++) {
            String col = periodColumns.get(i);
            if (i > 0) {
                q.append(", ");
            }
            q.append(col).append(":sum ").append(col).append(", ")
                    .append(COUNT_PREFIX).append(col).append(":sum not null ").append(col);
        }
        q.append(" by ").append(NODE_COLUMN).append(", ").append(fact.getAccountColumn());

        q.append(" from ej[`").append(h.factColumn()).append(";select from ").append(tables.get(dataSet))
                .append(" where ").append(fact.getT0DateColumn()).append('=').append(QIdentifiers.date(t0Date));
        if (portfolioId != null) {
            q.append(", ").append(fact.getPortfolioColumn()).append('=').append(QIdentifiers.symbol(portfolioId));
        }
        String ancestor = h.levelColumn(level);
        q.append(";select ").append(h.factColumn()).append(':').append(h.idColumn())
                .append(", ").append(NODE_COLUMN).append(':').append(ancestor)
                .append(" from ").append(h.table())
                .append(" where ").append(ancestor).append(" in ").append(QIdentifiers.longList(nodeIds))
                .append(']');
        return q.toString();
    }

    Map<Long, Map<String, Number[]>> read(c.Flip flip) {
        FlipReader table = new FlipReader(flip);
        Object nodes = table.column(NODE_COLUMN);
        Object accounts = table.column(fact.getAccountColumn());
        int periods = periodColumns.size();
        Object[] sums = new Object[periods];
        Object[] counts = new Object[periods];
        for (int p = 0; p < periods; p++) {
            sums[p] = table.column(periodColumns.get(p));
            counts[p] = table.column(COUNT_PREFIX + periodColumns.get(p));
        }

        Map<Long, Map<String, Number[]>> result = new HashMap<>();
        for (int row = 0; row < table.rowCount(); row++) {
            long node = FlipReader.requireLong(c.at(nodes, row), NODE_COLUMN);
            String account = FlipReader.text(c.at(accounts, row));
            if (account == null) {
                continue; // rows without an account cannot be shown
            }
            Number[] values = new Number[periods];
            for (int p = 0; p < periods; p++) {
                Object count = c.at(counts[p], row);
                Object sum = c.at(sums[p], row);
                if (count instanceof Number n && n.longValue() > 0 && sum instanceof Number s) {
                    values[p] = s;
                }
            }
            result.computeIfAbsent(node, k -> new LinkedHashMap<>()).put(account, values);
        }
        return result;
    }
}

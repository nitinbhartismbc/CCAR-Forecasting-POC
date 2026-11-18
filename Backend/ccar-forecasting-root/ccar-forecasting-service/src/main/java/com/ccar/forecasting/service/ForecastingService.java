package com.ccar.forecasting.service;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.hierarchy.Hierarchy;
import com.ccar.forecasting.hierarchy.HierarchyRepository;
import com.ccar.forecasting.kdb.QIdentifiers;
import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.BreakdownItem;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;
import com.ccar.forecasting.model.HierarchyNode;
import com.ccar.forecasting.model.PeriodValues;
import com.ccar.forecasting.model.Portfolio;
import com.ccar.forecasting.model.Scope;
import com.ccar.forecasting.repository.FactDataRepository;
import com.ccar.forecasting.repository.ReferenceDataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Turns the flat KDB results into the JSON the UI renders: periods, one {@link PeriodValues} per account (always
 * every configured account, in configured order, null where there is no data), the selected node as scope and, for
 * the breakdown, a page of its direct children.
 */
@Service
public class ForecastingService {

    private final HierarchyRepository hierarchies;
    private final FactDataRepository facts;
    private final ReferenceDataRepository referenceData;
    private final ForecastingProperties properties;
    /** account id -> measure, display order. */
    private final Map<String, String> accounts;

    public ForecastingService(HierarchyRepository hierarchies, FactDataRepository facts,
                              ReferenceDataRepository referenceData, ForecastingProperties properties) {
        this.hierarchies = hierarchies;
        this.facts = facts;
        this.referenceData = referenceData;
        this.properties = properties;
        Map<String, String> acc = new LinkedHashMap<>();
        properties.getAccounts().forEach(a -> acc.put(a.getId().trim(), a.getMeasure()));
        this.accounts = Collections.unmodifiableMap(acc);
    }

    // ---- reference data ---------------------------------------------------------------------------------------

    /** "All portfolios" first, then every portfolio that has data. */
    public List<Portfolio> portfolios() {
        String all = properties.getAllPortfoliosId();
        List<Portfolio> result = new ArrayList<>();
        result.add(new Portfolio(all, "All Portfolios"));
        referenceData.portfolioIds().stream()
                .filter(id -> !id.equalsIgnoreCase(all))
                .forEach(id -> result.add(new Portfolio(id, id)));
        return result;
    }

    public List<String> t0Dates() {
        return referenceData.t0Dates().stream().map(LocalDate::toString).toList();
    }

    // ---- hierarchy --------------------------------------------------------------------------------------------

    /** The whole hierarchy as a flat list, root first, in table order. */
    public List<HierarchyNode> hierarchy(String type) {
        return hierarchies.get(type).nodes();
    }

    public int reloadHierarchy(String type) {
        return hierarchies.reload(type).size();
    }

    // ---- data -------------------------------------------------------------------------------------------------

    public AggregateSummary aggregateSummary(DataSet dataSet, String hierarchyType, long hierarchyId,
                                             String portfolioId, LocalDate t0Date) {
        Hierarchy h = hierarchies.get(hierarchyType);
        HierarchyNode node = node(h, hierarchyId);
        String portfolio = normalizePortfolio(portfolioId);

        Map<Long, Map<String, Number[]>> totals = facts.totals(dataSet, h.definition(), node.level(),
                List.of(node.id()), t0Date, filterOf(portfolio));
        return new AggregateSummary(scope(h, node), t0Date, portfolio, properties.getCurrency(), periods(dataSet),
                rows(totals.get(node.id())));
    }

    public DetailedBreakdown detailedBreakdown(DataSet dataSet, String hierarchyType, long hierarchyId,
                                               String portfolioId, LocalDate t0Date, int page, Integer size) {
        Hierarchy h = hierarchies.get(hierarchyType);
        HierarchyNode node = node(h, hierarchyId);
        String portfolio = normalizePortfolio(portfolioId);
        int pageSize = pageSize(size);
        if (page < 0) {
            throw invalid("page", "page must be >= 0 but was " + page);
        }

        List<HierarchyNode> children = h.children(node.id());
        int from = (int) Math.min((long) page * pageSize, children.size());
        List<HierarchyNode> slice = children.subList(from, Math.min(from + pageSize, children.size()));

        Map<Long, Map<String, Number[]>> totals = slice.isEmpty() ? Map.of()
                : facts.totals(dataSet, h.definition(), node.level() + 1,
                slice.stream().map(HierarchyNode::id).toList(), t0Date, filterOf(portfolio));

        List<BreakdownItem> items = slice.stream()
                .map(c -> new BreakdownItem(c.id(), c.name(), h.type(), c.level(), c.childCount(),
                        rows(totals.get(c.id()))))
                .toList();
        int totalPages = (children.size() + pageSize - 1) / pageSize;
        return new DetailedBreakdown(scope(h, node), t0Date, portfolio, properties.getCurrency(), periods(dataSet),
                page, pageSize, children.size(), totalPages, items);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private List<String> periods(DataSet dataSet) {
        return IntStream.range(0, facts.periodCount()).mapToObj(dataSet::periodLabel).toList();
    }

    /** Every configured account (null-filled when it has no data), then any other account found, by id. */
    List<PeriodValues> rows(Map<String, Number[]> byAccount) {
        Map<String, Number[]> data = byAccount == null ? Map.of() : byAccount;
        List<PeriodValues> rows = new ArrayList<>(accounts.size());
        accounts.forEach((id, measure) -> rows.add(new PeriodValues(id, measure, values(data.get(id)))));
        new TreeSet<>(data.keySet()).stream()
                .filter(id -> !accounts.containsKey(id))
                .forEach(id -> rows.add(new PeriodValues(id, id, values(data.get(id)))));
        return rows;
    }

    private List<Number> values(Number[] values) {
        // Arrays.asList keeps nulls (List.of would reject them)
        return values == null ? Arrays.asList(new Number[facts.periodCount()]) : Arrays.asList(values);
    }

    private static HierarchyNode node(Hierarchy h, long id) {
        return h.node(id).orElseThrow(() ->
                new CcarException(ErrorCode.HIERARCHY_NODE_NOT_FOUND, h.type() + " node " + id + " not found")
                        .with("hierarchyType", h.type())
                        .with("nodeId", id));
    }

    private static Scope scope(Hierarchy h, HierarchyNode node) {
        return new Scope(h.type(), node.id(), node.name(), node.level());
    }

    private String normalizePortfolio(String portfolioId) {
        String p = portfolioId == null || portfolioId.isBlank() ? properties.getAllPortfoliosId() : portfolioId.trim();
        if (p.equalsIgnoreCase(properties.getAllPortfoliosId())) {
            return properties.getAllPortfoliosId();
        }
        if (!QIdentifiers.SAFE_VALUE.matcher(p).matches()) {
            throw invalid("portfolioId", "portfolioId must be 1-64 letters, digits, '_', '.' or '-'");
        }
        return p;
    }

    /** null = no portfolio filter. */
    private String filterOf(String portfolio) {
        return portfolio.equals(properties.getAllPortfoliosId()) ? null : portfolio;
    }

    private int pageSize(Integer size) {
        int s = size == null ? properties.getDefaultPageSize() : size;
        if (s < 1 || s > properties.getMaxPageSize()) {
            throw invalid("size", "size must be between 1 and " + properties.getMaxPageSize() + " but was " + s);
        }
        return s;
    }

    private static CcarException invalid(String parameter, String detail) {
        return new CcarException(ErrorCode.INVALID_PARAMETER, detail).with("parameter", parameter);
    }
}

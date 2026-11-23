package com.ccar.forecasting.service.impl;

import com.ccar.common.error.CcarException;
import com.ccar.common.error.ErrorCode;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.constant.ForecastingConstant;
import com.ccar.forecasting.hierarchy.HierarchyDefinition;
import com.ccar.forecasting.hierarchy.HierarchyNodeView;
import com.ccar.forecasting.hierarchy.HierarchyRepository;
import com.ccar.forecasting.kdb.QIdentifiers;
import com.ccar.forecasting.model.AggregateSummary;
import com.ccar.forecasting.model.BreakdownItem;
import com.ccar.forecasting.model.DataSet;
import com.ccar.forecasting.model.DetailedBreakdown;
import com.ccar.forecasting.model.HierarchyNode;
import com.ccar.forecasting.model.PeriodValues;
import com.ccar.forecasting.model.Scope;
import com.ccar.forecasting.repository.FactDataRepository;
import com.ccar.forecasting.service.ForecastingService;
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
 * Default {@link ForecastingService} implementation: turns the flat KDB results into the JSON the UI renders -
 * periods, one {@link PeriodValues} per account (always every configured account, in configured order, null where
 * there is no data), the selected node as scope and, for the breakdown, a page of its direct children.
 */
@Service
public class ForecastingServiceImpl implements ForecastingService {

    private final HierarchyRepository hierarchies;
    private final FactDataRepository facts;
    /** account id -> measure, display order. */
    private final Map<String, String> accounts;

    public ForecastingServiceImpl(HierarchyRepository hierarchies, FactDataRepository facts,
                                   ForecastingProperties properties) {
        this.hierarchies = hierarchies;
        this.facts = facts;
        Map<String, String> acc = new LinkedHashMap<>();
        properties.getAccounts().forEach(a -> acc.put(a.getId().trim(), a.getMeasure()));
        this.accounts = Collections.unmodifiableMap(acc);
    }


    // ---- data -------------------------------------------------------------------------------------------------

    @Override
    public AggregateSummary aggregateSummary(DataSet dataSet, String hierarchyType, long hierarchyId,
                                             String portfolioId, LocalDate t0Date) {
        String portfolio = normalizePortfolio(portfolioId);
        HierarchyNodeView h = node(hierarchyType, hierarchyId, false);
        HierarchyNode node = h.node();

        Map<Long, Map<String, Number[]>> totals = facts.totals(dataSet, h.definition(), node.level(),
                List.of(node.id()), t0Date, filterOf(portfolio));
        return new AggregateSummary(scope(h), t0Date, portfolio, ForecastingConstant.DEFAULT_CURRENCY,
                periods(dataSet), rows(totals.get(node.id())));
    }

    @Override
    public DetailedBreakdown detailedBreakdown(DataSet dataSet, String hierarchyType, long hierarchyId,
                                               String portfolioId, LocalDate t0Date, int page, Integer size) {
        String portfolio = normalizePortfolio(portfolioId);
        int pageSize = pageSize(size);
        if (page < 0) {
            throw invalid("page", "page must be >= 0 but was " + page);
        }
        HierarchyNodeView h = node(hierarchyType, hierarchyId, true);
        HierarchyNode node = h.node();

        List<HierarchyNode> children = h.children();
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
        return new DetailedBreakdown(scope(h), t0Date, portfolio, ForecastingConstant.DEFAULT_CURRENCY,
                periods(dataSet), page, pageSize, children.size(), totalPages, items);
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

    /**
     * The node (and, if asked, its direct children) from ccar-hierarchy-service. The type is checked against the
     * configured ones first, so an unsupported type never reaches the hierarchy service.
     */
    private HierarchyNodeView node(String hierarchyType, long id, boolean withChildren) {
        HierarchyDefinition def = hierarchies.definition(hierarchyType);
        return hierarchies.find(def, id, withChildren).orElseThrow(() ->
                new CcarException(ErrorCode.HIERARCHY_NODE_NOT_FOUND, def.type() + " node " + id + " not found")
                        .with("hierarchyType", def.type())
                        .with("nodeId", id));
    }

    private static Scope scope(HierarchyNodeView h) {
        HierarchyNode node = h.node();
        return new Scope(h.type(), node.id(), node.name(), node.level());
    }

    private String normalizePortfolio(String portfolioId) {
        String p = portfolioId == null || portfolioId.isBlank()
                ? ForecastingConstant.ALL_PORTFOLIOS_ID : portfolioId.trim();
        if (p.equalsIgnoreCase(ForecastingConstant.ALL_PORTFOLIOS_ID)) {
            return ForecastingConstant.ALL_PORTFOLIOS_ID;
        }
        if (!QIdentifiers.SAFE_VALUE.matcher(p).matches()) {
            throw invalid("portfolioId", "portfolioId must be 1-64 letters, digits, '_', '.' or '-'");
        }
        return p;
    }

    /** null = no portfolio filter. */
    private String filterOf(String portfolio) {
        return portfolio.equals(ForecastingConstant.ALL_PORTFOLIOS_ID) ? null : portfolio;
    }

    private int pageSize(Integer size) {
        int s = size == null ? ForecastingConstant.DEFAULT_PAGE_SIZE : size;
        if (s < 1 || s > ForecastingConstant.MAX_PAGE_SIZE) {
            throw invalid("size", "size must be between 1 and " + ForecastingConstant.MAX_PAGE_SIZE
                    + " but was " + s);
        }
        return s;
    }

    private static CcarException invalid(String parameter, String detail) {
        return new CcarException(ErrorCode.INVALID_PARAMETER, detail).with("parameter", parameter);
    }
}

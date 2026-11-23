package com.ccar.forecasting.config;

import com.ccar.forecasting.config.properties.Account;
import com.ccar.forecasting.config.properties.DataSetTable;
import com.ccar.forecasting.config.properties.Hierarchy;
import com.ccar.forecasting.config.properties.HierarchyServiceEndpoint;
import com.ccar.forecasting.config.properties.PortfolioMaster;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code ccar.forecasting.*}: where the forecast / actuals / variance data lives in KDB and how it is shaped.
 * <p>
 * Every data set is one flat KDB table with this layout (fact column names are fixed, see
 * {@link com.ccar.forecasting.constant.ForecastingConstant}):
 * <pre>
 *   BU_ID (j)  GEO_ID (j)  ACCOUNT_ID (j|s)  PORTFOLIO_ID (s)  T0_DATE (d)  T0  T_1 .. T_36 (j|f)
 * </pre>
 * One row per (leaf node, account, portfolio, T0 date). {@code BU_ID} / {@code GEO_ID} are the ids of the node the
 * row is booked against in each hierarchy (the {@code fact-column} of the hierarchy type). Values are summed in KDB
 * over every node under the requested one, using the {@code <PREFIX>_ID_LEVELn} path columns of the hierarchy table.
 * <p>
 * {@code @Validated} + Bean Validation annotations below make Spring fail fast at application startup
 * (context refresh fails with a clear message) when required config is missing, rather than surfacing as an
 * obscure {@code NullPointerException}/{@code IllegalArgumentException} the first time a repository uses it.
 * <p>
 * The nested configuration classes ({@link Account}, {@link DataSetTable}, {@link Hierarchy},
 * {@code HierarchyType}, {@link PortfolioMaster}) live under {@code com.ccar.forecasting.config.properties}.
 */
@Validated
@ConfigurationProperties(prefix = "ccar.forecasting")
public class ForecastingProperties {

    /** Table that portfolios and T0 dates are read from. */
    @NotBlank
    private String referenceTable = "Actuals";

    /** Portfolio master table (id + display name). */
    @Valid
    private PortfolioMaster portfolioMaster = new PortfolioMaster();

    /** Data set (forecast, actuals, variance) -> KDB table. At least one must be configured. */
    @NotEmpty
    private Map<String, @Valid DataSetTable> datasets = new LinkedHashMap<>();

    /** Accounts (measures) in display order. */
    private List<@Valid Account> accounts = new ArrayList<>();

    @Valid
    private Hierarchy hierarchy = new Hierarchy();

    /** ccar-hierarchy-service, the source of hierarchy nodes (name, level, children). */
    @Valid
    private HierarchyServiceEndpoint hierarchyService = new HierarchyServiceEndpoint();

    public String getReferenceTable() {
        return referenceTable;
    }

    public void setReferenceTable(String referenceTable) {
        this.referenceTable = referenceTable;
    }

    public PortfolioMaster getPortfolioMaster() {
        return portfolioMaster;
    }

    public void setPortfolioMaster(PortfolioMaster portfolioMaster) {
        this.portfolioMaster = portfolioMaster;
    }

    public Map<String, DataSetTable> getDatasets() {
        return datasets;
    }

    public void setDatasets(Map<String, DataSetTable> datasets) {
        this.datasets = datasets;
    }


    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public Hierarchy getHierarchy() {
        return hierarchy;
    }

    public void setHierarchy(Hierarchy hierarchy) {
        this.hierarchy = hierarchy;
    }

    public HierarchyServiceEndpoint getHierarchyService() {
        return hierarchyService;
    }

    public void setHierarchyService(HierarchyServiceEndpoint hierarchyService) {
        this.hierarchyService = hierarchyService;
    }
}

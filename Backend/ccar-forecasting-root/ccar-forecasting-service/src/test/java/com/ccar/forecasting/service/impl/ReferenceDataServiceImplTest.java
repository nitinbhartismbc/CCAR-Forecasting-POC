package com.ccar.forecasting.service.impl;

import com.ccar.forecasting.TestData;
import com.ccar.forecasting.config.ForecastingProperties;
import com.ccar.forecasting.constant.ForecastingConstant;
import com.ccar.forecasting.model.Portfolio;
import com.ccar.forecasting.repository.ReferenceDataRepository;
import com.iris.kdb.service.KdbTemplate;
import com.kx.c;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceDataServiceImplTest {

    /** Fake KDB: portfolio master and T0 dates; records every query so we can assert caching behaviour. */
    private static final class FakeKdb extends KdbTemplate {
        final List<String> queries = new ArrayList<>();
        c.Flip portfolioMaster = TestData.flip(
                new String[]{"PORTFOLIO_ID", "PORTFOLIO_NAME"},
                new String[]{"PORT_0001", "PORT_0002"},
                new String[]{"PORT_0001", "PORT_0002"});

        FakeKdb() {
            super(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String query) {
            queries.add(query);
            if (query.startsWith("desc exec distinct T0_DATE")) {
                return (T) new LocalDate[]{LocalDate.of(2026, 9, 30), LocalDate.MIN, LocalDate.of(2025, 12, 31)};
            }
            throw new IllegalArgumentException(query);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(String query, Class<T> clazz) {
            queries.add(query);
            return (T) portfolioMaster;
        }
    }

    private final FakeKdb kdb = new FakeKdb();
    private final ForecastingProperties props = TestData.properties();
    private final ReferenceDataServiceImpl service =
            new ReferenceDataServiceImpl(new ReferenceDataRepository(kdb, props));

    @Test
    void portfoliosStartWithAllAndDatesDropNulls() {
        assertEquals(List.of(new Portfolio(ForecastingConstant.ALL_PORTFOLIOS_ID, "All Portfolios"),new Portfolio("PORT_0001", "PORT_0001"),
                new Portfolio("PORT_0002", "PORT_0002")), service.portfolios());
        assertEquals(List.of("2026-09-30", "2025-12-31"), service.t0Dates());
    }

    @Test
    void warmUpPopulatesTheCacheSoLaterCallsDoNotHitKdbAgain() {
        service.warmUp();
        int queriesAfterWarmUp = kdb.queries.size();

        assertTrue(queriesAfterWarmUp >= 2); // one for portfolios, one for T0 dates
        service.portfolios();
        service.t0Dates();

        assertEquals(queriesAfterWarmUp, kdb.queries.size()); // served from cache, no extra KDB calls
    }

    @Test
    void withoutWarmUpFirstCallLazilyLoadsAndCachesForNextTime() {
        service.portfolios();
        int queriesAfterFirstCall = kdb.queries.size();

        service.portfolios();

        assertEquals(queriesAfterFirstCall, kdb.queries.size()); // second call served from cache
    }

    @Test
    void refreshReReadsBothCaches() {
        service.warmUp();
        int queriesAfterWarmUp = kdb.queries.size();

        service.refresh();

        assertEquals(queriesAfterWarmUp + 2, kdb.queries.size());
    }
}

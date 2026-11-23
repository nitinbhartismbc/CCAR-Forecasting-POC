package com.ccar.forecasting;

import com.iris.kdb.service.KdbTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CcarForecastingServiceApplicationTests {

    /** No real KDB in tests (hierarchy nodes come from ccar-hierarchy-service, called only per request). */
    @MockitoBean
    private KdbTemplate kdbTemplate;

    @Test
    void contextLoads() {
    }
}

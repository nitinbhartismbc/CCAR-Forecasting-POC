package com.ccar.forecasting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CcarForecastingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcarForecastingServiceApplication.class, args);
    }
}

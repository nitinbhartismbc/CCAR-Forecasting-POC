package com.forecast.ccar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CcarApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(CcarApplication.class, args);
	}

}

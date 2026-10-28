package com.forecast.ccar;

import com.forecast.ccar.service.BuHierarchyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CcarApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(CcarApplication.class, args);
		BuHierarchyService buHierarchyService=new BuHierarchyService();
	}

}

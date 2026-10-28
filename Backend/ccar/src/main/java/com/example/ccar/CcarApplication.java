package com.example.ccar;

import com.example.ccar.service.BuHierarchyService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CcarApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(CcarApplication.class, args);
		BuHierarchyService buHierarchyService=new BuHierarchyService();
		//System.out.println(buHierarchyService.buildJson("BU_HIERARCHY1.csv"));
	}

}

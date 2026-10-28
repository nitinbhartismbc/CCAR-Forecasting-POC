package com.example.ccar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuHierarchyController {

    @Autowired
    BuHierarchyService hierarchyService;

    @GetMapping("/bu")
    public String getJsonBUHierarchy() throws Exception {
        return hierarchyService.buildJson("BU_HIERARCHY1.csv");
    }
}

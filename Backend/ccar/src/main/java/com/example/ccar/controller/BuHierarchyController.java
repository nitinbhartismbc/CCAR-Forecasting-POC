package com.example.ccar.controller;

import com.example.ccar.service.BuHierarchyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hierarchy")
public class BuHierarchyController {

    @Autowired
    BuHierarchyService hierarchyService;

    @GetMapping("/bu")
    public String getJsonBUHierarchy() throws Exception {
        return hierarchyService.buildJson("BU_HIERARCHY1.csv");
    }
}

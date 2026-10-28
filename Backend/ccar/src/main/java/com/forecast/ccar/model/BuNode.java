package com.forecast.ccar.model;

import java.util.ArrayList;
import java.util.List;

public class BuNode {

    private Long id;
    private String name;
    private Integer levelId;
    private List<BuNode> children = new ArrayList();

    public BuNode() {
    }

    public BuNode(Long id, String name, Integer levelId) {
        this.id = id;
        this.name = name;
        this.levelId = levelId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLevelId() {
        return levelId;
    }

    public void setLevelId(Integer levelId) {
        this.levelId = levelId;
    }

    public List<BuNode> getChildren() {
        return children;
    }

    public void setChildren(List<BuNode> children) {
        this.children = children;
    }
}
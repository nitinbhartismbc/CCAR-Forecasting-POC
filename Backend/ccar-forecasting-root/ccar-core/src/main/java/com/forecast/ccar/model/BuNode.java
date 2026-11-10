package com.forecast.ccar.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * One Business Unit in the BU hierarchy tree.
 * <p>
 * {@code childCount} is the real number of children even when {@code children} has been
 * truncated by a depth-limited view, so a UI tree can show an expand arrow and lazy-load
 * the children on demand.
 */
@JsonPropertyOrder({"id", "name", "levelId", "parentId", "childCount", "children"})
public class BuNode {

    private Long id;
    private String name;
    private Integer levelId;
    private Long parentId;
    private int childCount;
    private List<BuNode> children = new ArrayList<>();

    public BuNode() {
    }

    public BuNode(Long id, String name, Integer levelId, Long parentId) {
        this.id = id;
        this.name = name;
        this.levelId = levelId;
        this.parentId = parentId;
    }

    /**
     * Returns a copy of this subtree containing at most {@code depth} levels below this node.
     * depth 0 = this node only (children empty, childCount preserved).
     */
    public BuNode copy(int depth) {
        BuNode copy = new BuNode(id, name, levelId, parentId);
        copy.childCount = childCount;
        if (depth > 0) {
            List<BuNode> copied = new ArrayList<>(children.size());
            for (BuNode child : children) {
                copied.add(child.copy(depth - 1));
            }
            copy.children = copied;
        }
        return copy;
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public int getChildCount() {
        return childCount;
    }

    public void setChildCount(int childCount) {
        this.childCount = childCount;
    }

    public List<BuNode> getChildren() {
        return children;
    }

    public void setChildren(List<BuNode> children) {
        this.children = children;
    }
}

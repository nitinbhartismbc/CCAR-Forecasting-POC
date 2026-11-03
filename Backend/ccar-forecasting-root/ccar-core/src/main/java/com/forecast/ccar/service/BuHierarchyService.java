package com.forecast.ccar.service;

import com.forecast.ccar.model.BuNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.util.*;

@Service
public class BuHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(BuHierarchyService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public BuNode buildHierarchy(String csvFilePath) throws Exception {

        log.info("Building BU hierarchy from CSV file: {}", csvFilePath);
        long start = System.currentTimeMillis();

        Map<Long, BuNode> nodeMap = new HashMap<>();
        Map<Long, Long> parentMap = new HashMap<>();

        int lineNumber = 1; // header is line 1

        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                log.warn("CSV file is empty: {}", csvFilePath);
                return null;
            }
            log.debug("CSV headers: {}", Arrays.toString(headers));

            String[] row;

            while ((row = reader.readNext()) != null) {
                lineNumber++;

                try {
                    Long buId = Long.valueOf(row[0]);
                    String buName = row[1];
                    Integer levelId = Integer.valueOf(row[2]);

                    BuNode node = new BuNode(buId, buName, levelId);

                    if (nodeMap.put(buId, node) != null) {
                        log.warn("Duplicate BU id {} found at line {}, previous entry overwritten",
                                buId, lineNumber);
                    }

                    if (levelId > 1) {
                        Long parentId = getParentId(row, levelId);
                        parentMap.put(buId, parentId);
                        log.trace("Line {}: BU {} ('{}') level {} -> parent {}",
                                lineNumber, buId, buName, levelId, parentId);
                    } else {
                        log.trace("Line {}: BU {} ('{}') level {} (top level)",
                                lineNumber, buId, buName, levelId);
                    }

                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    log.error("Invalid data at line {} of {}: {} | row={}",
                            lineNumber, csvFilePath, e.getMessage(), Arrays.toString(row), e);
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Failed to read CSV file '{}': {}", csvFilePath, e.getMessage(), e);
            throw e;
        }

        log.info("Read {} BU node(s) from CSV, {} with a parent", nodeMap.size(), parentMap.size());

        BuNode root = null;
        int rootCount = 0;
        int orphanCount = 0;

        for (Map.Entry<Long, BuNode> entry : nodeMap.entrySet()) {

            Long nodeId = entry.getKey();
            BuNode node = entry.getValue();

            if (!parentMap.containsKey(nodeId)) {
                root = node;
                rootCount++;
                log.debug("Root node identified: {} ('{}')", nodeId, node.getName());
                continue;
            }

            Long parentId = parentMap.get(nodeId);
            BuNode parentNode = nodeMap.get(parentId);

            if (parentNode != null) {
                parentNode.getChildren().add(node);
            } else {
                orphanCount++;
                log.warn("Parent BU {} not found for BU {}, node will not be attached", parentId, nodeId);
            }
        }

        if (rootCount == 0) {
            log.error("No root node found in hierarchy from {}", csvFilePath);
        } else if (rootCount > 1) {
            log.warn("{} root nodes found, only the last one is returned (root id: {})",
                    rootCount, root.getId());
        }
        if (orphanCount > 0) {
            log.warn("{} node(s) could not be attached to a parent", orphanCount);
        }

        log.info("BU hierarchy built in {} ms", System.currentTimeMillis() - start);
        return root;
    }

    private Long getParentId(String[] row, int levelId) {

        int parentColumnIndex = levelId + 1;

        log.trace("Reading parent id from column index {} for level {}", parentColumnIndex, levelId);

        return Long.valueOf(row[parentColumnIndex]);
    }

    public String buildJson(String csvFilePath) throws Exception {

        log.info("Generating BU hierarchy JSON for file: {}", csvFilePath);

        BuNode root = buildHierarchy(csvFilePath);

        if (root == null) {
            log.warn("No hierarchy built from {}, JSON output will be 'null'", csvFilePath);
        }

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        log.debug("BU hierarchy JSON generated, size: {} characters", json.length());
        return json;
    }
}
package com.forecast.ccar.service;

import com.forecast.ccar.model.BuNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.util.*;

@Service
public class BuHierarchyService {

    public BuNode buildHierarchy(String csvFilePath) throws Exception {

        Map<Long, BuNode> nodeMap = new HashMap();
        Map<Long, Long> parentMap = new HashMap();

        try (CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {

            String[] headers = reader.readNext();
            String[] row;

            while ((row = reader.readNext()) != null) {

                Long buId = Long.valueOf(row[0]);
                String buName = row[1];
                Integer levelId = Integer.valueOf(row[2]);

                BuNode node =
                        new BuNode(buId, buName, levelId);

                nodeMap.put(buId, node);

                if (levelId > 1) {

                    Long parentId =
                            getParentId(row, levelId);

                    parentMap.put(buId, parentId);
                }
            }
        }

        BuNode root = null;

        for (Map.Entry<Long, BuNode> entry : nodeMap.entrySet()) {

            Long nodeId = entry.getKey();
            BuNode node = entry.getValue();

            if (!parentMap.containsKey(nodeId)) {
                root = node;
                continue;
            }

            Long parentId = parentMap.get(nodeId);

            BuNode parentNode =
                    nodeMap.get(parentId);

            if (parentNode != null) {
                parentNode.getChildren().add(node);
            }
        }

        return root;
    }

    private Long getParentId(String[] row,
                             int levelId) {

        int parentColumnIndex = levelId + 1;

        return Long.valueOf(
                row[parentColumnIndex]
        );
    }

    public String buildJson(String csvFilePath)
            throws Exception {

        BuNode root =
                buildHierarchy(csvFilePath);

        ObjectMapper mapper =
                new ObjectMapper();

        return mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(root);
    }
}

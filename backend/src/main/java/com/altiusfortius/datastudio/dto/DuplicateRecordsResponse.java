package com.altiusfortius.datastudio.dto;

import java.util.List;
import java.util.Map;

public record DuplicateRecordsResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String columnName,
        String matchingValue,
        long matchingRecords,
        List<Map<String, Object>> records
) {
}
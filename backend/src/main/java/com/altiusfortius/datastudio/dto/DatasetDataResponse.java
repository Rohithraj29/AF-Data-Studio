package com.altiusfortius.datastudio.dto;

import java.util.List;
import java.util.Map;

public record DatasetDataResponse(
        Long datasetId,
        String tableName,
        long totalRecords,
        int page,
        int pageSize,
        int totalPages,
        List<Map<String, Object>> records
) {
}
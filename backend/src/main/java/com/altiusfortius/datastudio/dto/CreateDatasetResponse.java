package com.altiusfortius.datastudio.dto;

public record CreateDatasetResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String status,
        int totalColumns,
        String message
) {
}
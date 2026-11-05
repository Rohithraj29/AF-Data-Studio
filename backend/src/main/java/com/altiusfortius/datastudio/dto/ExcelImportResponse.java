package com.altiusfortius.datastudio.dto;

public record ExcelImportResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String status,
        long totalRows,
        int totalColumns,
        String message
) {
}
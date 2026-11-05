package com.altiusfortius.datastudio.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DatasetDetailsResponse(
        Long id,
        String datasetName,
        String originalFileName,
        String tableName,
        String sheetName,
        Long totalRows,
        Integer totalColumns,
        String importStatus,
        OffsetDateTime importedAt,
        List<ColumnDetails> columns
) {
    public record ColumnDetails(
            String excelColumnName,
            String databaseColumnName,
            String dataType,
            Integer ordinalPosition
    ) {
    }
}
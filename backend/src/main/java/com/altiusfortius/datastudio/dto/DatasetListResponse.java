package com.altiusfortius.datastudio.dto;

import java.time.OffsetDateTime;

public record DatasetListResponse(
        Long id,
        String datasetName,
        String originalFileName,
        String tableName,
        Long totalRows,
        Integer totalColumns,
        String importStatus,
        OffsetDateTime importedAt
) {
}
package com.altiusfortius.datastudio.dto;

import java.util.List;

public record DataQualityResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        long totalRecords,
        int totalColumns,
        long totalBlankValues,
        long totalDuplicateValues,
        long totalInvalidValues,
        double completenessPercentage,
        double validityPercentage,
        double uniquenessPercentage,
        double overallQualityScore,
        List<ColumnQualityResponse> columns) {
}
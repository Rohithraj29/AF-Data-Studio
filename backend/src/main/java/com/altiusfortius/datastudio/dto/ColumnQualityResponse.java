package com.altiusfortius.datastudio.dto;

public record ColumnQualityResponse(
        String columnName,
        String databaseColumnName,
        String dataType,
        long totalRecords,
        long blankRecords,
        long completeRecords,
        double completenessPercentage,
        long duplicateRecords,
        long invalidRecords,
        double validityPercentage,
        double uniquenessPercentage) {
}
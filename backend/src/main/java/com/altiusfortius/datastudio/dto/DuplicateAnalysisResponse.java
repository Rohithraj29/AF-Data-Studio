package com.altiusfortius.datastudio.dto;

import java.util.List;

public record DuplicateAnalysisResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String columnName,
        long totalRecords,
        long blankRecords,
        long duplicateGroups,
        long duplicateRecords,
        long uniqueRecords,
        List<DuplicateGroupResponse> groups
) {
}
package com.altiusfortius.datastudio.dto;

import java.util.List;

public record NearDuplicateAnalysisResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String columnName,
        double thresholdPercentage,
        long totalUniqueValues,
        long candidatePairs,
        List<NearDuplicatePairResponse> matches
) {
}
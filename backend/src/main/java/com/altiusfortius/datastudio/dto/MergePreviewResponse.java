package com.altiusfortius.datastudio.dto;

import java.util.List;

public record MergePreviewResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        List<Long> sourceRecordIds,
        List<MergeFieldResponse> fields,
        String status,
        String message
) {
}
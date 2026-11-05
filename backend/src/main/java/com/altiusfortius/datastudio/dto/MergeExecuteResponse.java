package com.altiusfortius.datastudio.dto;

import java.util.List;

public record MergeExecuteResponse(
        Long mergeOperationId,
        Long datasetId,
        Long survivorRecordId,
        List<Long> mergedRecordIds,
        String status,
        String message
) {
}
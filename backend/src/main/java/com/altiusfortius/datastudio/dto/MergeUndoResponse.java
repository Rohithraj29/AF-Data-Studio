package com.altiusfortius.datastudio.dto;

import java.util.List;

public record MergeUndoResponse(
        Long mergeOperationId,
        Long datasetId,
        Long restoredSurvivorRecordId,
        List<Long> reactivatedRecordIds,
        String status,
        String message
) {
}
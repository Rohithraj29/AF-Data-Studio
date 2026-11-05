package com.altiusfortius.datastudio.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MergeHistoryResponse(
        Long mergeOperationId,
        Long datasetId,
        Long survivorRecordId,
        String status,
        Map<String, String> fieldSelections,
        Map<String, Object> finalRecord,
        OffsetDateTime createdAt,
        OffsetDateTime reversedAt,
        List<MergeHistoryRecordResponse> originalRecords
) {
}
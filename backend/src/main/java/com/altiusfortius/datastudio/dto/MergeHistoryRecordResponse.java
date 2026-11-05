package com.altiusfortius.datastudio.dto;

import java.util.Map;

public record MergeHistoryRecordResponse(
        Long recordId,
        String role,
        Map<String, Object> originalRecord
) {
}
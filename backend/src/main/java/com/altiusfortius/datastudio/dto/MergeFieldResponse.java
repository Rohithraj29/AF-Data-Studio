package com.altiusfortius.datastudio.dto;

import java.util.List;

public record MergeFieldResponse(
        String fieldName,
        List<String> availableValues,
        boolean hasConflict,
        String suggestedValue
) {
}
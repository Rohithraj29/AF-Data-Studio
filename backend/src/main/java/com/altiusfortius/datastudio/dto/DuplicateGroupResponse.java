package com.altiusfortius.datastudio.dto;

public record DuplicateGroupResponse(
        String value,
        long occurrences
) {
}
package com.altiusfortius.datastudio.dto;

public record NearDuplicatePairResponse(
        String value1,
        String value2,
        double similarityPercentage,
        long occurrences1,
        long occurrences2
) {
}
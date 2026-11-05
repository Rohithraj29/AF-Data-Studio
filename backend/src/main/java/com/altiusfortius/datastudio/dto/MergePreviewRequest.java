
package com.altiusfortius.datastudio.dto;

import java.util.List;

public record MergePreviewRequest(
        List<Long> recordIds,
        String matchingColumn,
        String matchingValue,
        String mergeMode,
        String matchingValue2,
        Double similarityThreshold
) {
}

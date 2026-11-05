
package com.altiusfortius.datastudio.dto;

import java.util.List;
import java.util.Map;

public record MergeExecuteRequest(
        List<Long> recordIds,
        Long survivorRecordId,
        Map<String, String> fieldSelections,
        String matchingColumn,
        String matchingValue,
        String mergeMode,
        String matchingValue2,
        Double similarityThreshold
) {
}

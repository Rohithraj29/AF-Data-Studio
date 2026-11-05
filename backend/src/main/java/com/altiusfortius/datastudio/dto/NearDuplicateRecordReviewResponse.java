
package com.altiusfortius.datastudio.dto;

import java.util.List;
import java.util.Map;

public record NearDuplicateRecordReviewResponse(
        Long datasetId,
        String datasetName,
        String tableName,
        String columnName,
        String matchingValue1,
        String matchingValue2,
        double similarityPercentage,
        long matchingRecords,
        int returnedRecords,
        boolean truncated,
        List<Map<String, Object>> records
) {
}

package com.altiusfortius.datastudio.dto;

import java.util.List;

public record CreateDatasetRequest(
        String datasetName,
        String originalFileName,
        String sheetName,
        List<ColumnMappingRequest> columns
) {
}
package com.altiusfortius.datastudio.dto;

import java.util.List;

public record ExcelImportRequest(
        String datasetName,
        List<ColumnMappingRequest> columns
) {
}
package com.altiusfortius.datastudio.dto;

import com.altiusfortius.datastudio.enums.ColumnDataType;

public record ColumnMappingRequest(
        String excelColumnName,
        String databaseColumnName,
        ColumnDataType dataType
) {
}
package com.altiusfortius.datastudio.dto;

import java.util.List;
import java.util.Map;

public record ExcelPreviewResponse(
        String fileName,
        String sheetName,
        List<String> columns,
        long totalRows,
        int previewedRows,
        List<Map<String, String>> previewData
) {
}
package com.altiusfortius.datastudio.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.altiusfortius.datastudio.dto.ExcelPreviewResponse;

@Service
public class ExcelImportService {

    private static final int MAX_PREVIEW_ROWS = 100;

    public ExcelPreviewResponse previewExcel(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select an Excel file.");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null ||
                !(fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || fileName.toLowerCase(Locale.ROOT).endsWith(".xls"))) {

            throw new IllegalArgumentException(
                    "Only .xlsx and .xls files are supported.");
        }

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "The Excel file does not contain any sheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalArgumentException(
                        "The first row must contain column headers.");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();

            int columnCount = 0;

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row != null && row.getLastCellNum() > columnCount) {
                    columnCount = row.getLastCellNum();
                }
            }

            if (columnCount == 0) {
                throw new IllegalArgumentException(
                        "No columns were found in the Excel file.");
            }

            List<String> columns = new ArrayList<>();
            Set<String> usedNames = new HashSet<>();

            for (int i = 0; i < columnCount; i++) {

                String columnName = formatter.formatCellValue(
                        headerRow.getCell(i), evaluator).trim();

                if (columnName.isBlank()) {
                    columnName = "Column_" + (i + 1);
                }

                String uniqueName = columnName;
                int suffix = 2;

                while (!usedNames.add(uniqueName.toLowerCase(Locale.ROOT))) {
                    uniqueName = columnName + "_" + suffix++;
                }

                columns.add(uniqueName);
            }

            List<Map<String, String>> previewData = new ArrayList<>();
            long totalRows = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                Map<String, String> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (int j = 0; j < columnCount; j++) {

                    String value = formatter.formatCellValue(
                            row.getCell(j), evaluator);

                    if (!value.isBlank()) {
                        hasData = true;
                    }

                    rowData.put(columns.get(j), value);
                }

                if (!hasData) {
                    continue;
                }

                totalRows++;

                if (previewData.size() < MAX_PREVIEW_ROWS) {
                    previewData.add(rowData);
                }
            }

            return new ExcelPreviewResponse(
                    fileName,
                    sheet.getSheetName(),
                    columns,
                    totalRows,
                    previewData.size(),
                    previewData
            );
        }
    }
}
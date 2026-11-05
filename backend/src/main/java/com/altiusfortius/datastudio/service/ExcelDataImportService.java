package com.altiusfortius.datastudio.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.altiusfortius.datastudio.dto.ColumnMappingRequest;
import com.altiusfortius.datastudio.dto.CreateDatasetRequest;
import com.altiusfortius.datastudio.dto.CreateDatasetResponse;
import com.altiusfortius.datastudio.dto.ExcelImportRequest;
import com.altiusfortius.datastudio.dto.ExcelImportResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.enums.ColumnDataType;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class ExcelDataImportService {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final DatasetService datasetService;
    private final DatasetRepository datasetRepository;

    public ExcelDataImportService(
            JdbcTemplate jdbcTemplate,
            DatasetService datasetService,
            DatasetRepository datasetRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetService = datasetService;
        this.datasetRepository = datasetRepository;
    }

    @Transactional
    public ExcelImportResponse importExcel(
            MultipartFile file,
            ExcelImportRequest request) {

        validateFile(file);
        validateImportRequest(request);

        String fileName = file.getOriginalFilename();

        try (Workbook workbook =
                     WorkbookFactory.create(file.getInputStream())) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "The Excel file does not contain any worksheets.");
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

            validateExcelHeaders(
                    headerRow, request.columns(), formatter, evaluator);

            CreateDatasetRequest datasetRequest =
                    new CreateDatasetRequest(
                            request.datasetName(),
                            fileName,
                            sheet.getSheetName(),
                            request.columns()
                    );

            CreateDatasetResponse dataset =
                    datasetService.createDataset(datasetRequest);

            String insertSql = buildInsertSql(
                    dataset.tableName(), request.columns());

            List<Object[]> batch = new ArrayList<>();
            long importedRows = 0;

            for (int rowIndex = 1;
                    rowIndex <= sheet.getLastRowNum();
                    rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                Object[] values = new Object[request.columns().size()];
                boolean hasData = false;

                for (int columnIndex = 0;
                        columnIndex < request.columns().size();
                        columnIndex++) {

                    ColumnMappingRequest mapping =
                            request.columns().get(columnIndex);

                    int excelColumnIndex = findHeaderIndex(
                            headerRow,
                            mapping.excelColumnName(),
                            formatter,
                            evaluator
                    );

                    String value = formatter.formatCellValue(
                            row.getCell(excelColumnIndex), evaluator).trim();

                    if (!value.isEmpty()) {
                        hasData = true;
                    }

                    values[columnIndex] =
                            convertValue(value, mapping.dataType());
                }

                if (!hasData) {
                    continue;
                }

                batch.add(values);
                importedRows++;

                if (batch.size() >= BATCH_SIZE) {
                    insertBatch(insertSql, batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                insertBatch(insertSql, batch);
            }

            Dataset savedDataset = datasetRepository
                    .findById(dataset.datasetId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Dataset metadata could not be found."));

            savedDataset.setTotalRows(importedRows);
            savedDataset.setImportStatus("IMPORTED");
            datasetRepository.save(savedDataset);

            return new ExcelImportResponse(
                    savedDataset.getId(),
                    savedDataset.getDatasetName(),
                    savedDataset.getTableName(),
                    savedDataset.getImportStatus(),
                    importedRows,
                    request.columns().size(),
                    "Excel data imported successfully."
            );

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Unable to read the Excel file. Please check that it is valid.",
                    e
            );
        }
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload an Excel file.");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null ||
                !(fileName.toLowerCase().endsWith(".xlsx")
                || fileName.toLowerCase().endsWith(".xls"))) {

            throw new IllegalArgumentException(
                    "Only .xlsx and .xls files are supported.");
        }
    }

    private void validateImportRequest(ExcelImportRequest request) {

        if (request == null
                || request.datasetName() == null
                || request.datasetName().isBlank()
                || request.columns() == null
                || request.columns().isEmpty()) {

            throw new IllegalArgumentException(
                    "Dataset name and column mappings are required.");
        }
    }

    private void validateExcelHeaders(
            Row headerRow,
            List<ColumnMappingRequest> mappings,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        for (ColumnMappingRequest mapping : mappings) {

            if (mapping == null
                    || mapping.excelColumnName() == null
                    || mapping.excelColumnName().isBlank()
                    || mapping.databaseColumnName() == null
                    || mapping.dataType() == null) {

                throw new IllegalArgumentException(
                        "Every column mapping must be complete.");
            }

            if (findHeaderIndex(
                    headerRow,
                    mapping.excelColumnName(),
                    formatter,
                    evaluator) < 0) {

                throw new IllegalArgumentException(
                        "Excel column not found: "
                                + mapping.excelColumnName());
            }
        }
    }

    private int findHeaderIndex(
            Row headerRow,
            String expectedHeader,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {

            String actualHeader = formatter.formatCellValue(
                    headerRow.getCell(i), evaluator).trim();

            if (actualHeader.equals(expectedHeader.trim())) {
                return i;
            }
        }

        return -1;
    }

    private String buildInsertSql(
            String tableName,
            List<ColumnMappingRequest> mappings) {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO \"")
           .append(tableName)
           .append("\" (");

        for (int i = 0; i < mappings.size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("\"")
               .append(mappings.get(i).databaseColumnName())
               .append("\"");
        }

        sql.append(") VALUES (");

        for (int i = 0; i < mappings.size(); i++) {

            if (i > 0) {
                sql.append(", ");
            }

            sql.append("?");
        }

        sql.append(")");

        return sql.toString();
    }

    private Object convertValue(
            String value,
            ColumnDataType dataType) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return switch (dataType) {
                case TEXT -> value;
                case INTEGER -> Integer.valueOf(value);
                case BIGINT -> Long.valueOf(value);
                case DECIMAL -> new BigDecimal(value);
                case BOOLEAN -> parseBoolean(value);
                case DATE -> java.sql.Date.valueOf(
                        LocalDate.parse(value));
                case TIMESTAMP -> Timestamp.valueOf(
                        LocalDateTime.parse(value));
            };

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid value '" + value
                            + "' for database type " + dataType
                            + ". Check your Excel data and column mapping.",
                    e
            );
        }
    }

    private Boolean parseBoolean(String value) {

        if (value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("yes")
                || value.equals("1")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")
                || value.equalsIgnoreCase("no")
                || value.equals("0")) {
            return false;
        }

        throw new IllegalArgumentException(
                "Invalid boolean value: " + value);
    }

    private void insertBatch(
            String sql,
            List<Object[]> batch) {

        jdbcTemplate.batchUpdate(sql, batch);
    }
}
package com.altiusfortius.datastudio.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.ColumnMappingRequest;
import com.altiusfortius.datastudio.dto.CreateDatasetRequest;
import com.altiusfortius.datastudio.dto.CreateDatasetResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.enums.ColumnDataType;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DatasetService {

    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public DatasetService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    @Transactional
    public CreateDatasetResponse createDataset(
            CreateDatasetRequest request) {

        validateRequest(request);

        String tableName = generateTableName();

        Dataset dataset = new Dataset();
        dataset.setDatasetName(request.datasetName().trim());
        dataset.setOriginalFileName(request.originalFileName().trim());
        dataset.setSheetName(request.sheetName().trim());
        dataset.setTableName(tableName);
        dataset.setTotalRows(0L);
        dataset.setTotalColumns(request.columns().size());
        dataset.setImportStatus("TABLE_CREATED");

        dataset = datasetRepository.save(dataset);

        StringBuilder sql = new StringBuilder();

        sql.append("CREATE TABLE \"")
           .append(tableName)
           .append("\" (")
           .append("\"id\" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, ")
           .append("\"created_at\" TIMESTAMP NOT NULL DEFAULT clock_timestamp()");

        for (ColumnMappingRequest column : request.columns()) {

            String columnName =
                    column.databaseColumnName().trim();

            String sqlType = getSqlType(column.dataType());

            sql.append(", \"")
               .append(columnName)
               .append("\" ")
               .append(sqlType);
        }

        sql.append(")");

        jdbcTemplate.execute(sql.toString());

        int position = 1;

        for (ColumnMappingRequest mapping : request.columns()) {

            DatasetColumn datasetColumn = new DatasetColumn();

            datasetColumn.setDataset(dataset);
            datasetColumn.setExcelColumnName(
                    mapping.excelColumnName().trim());
            datasetColumn.setDbColumnName(
                    mapping.databaseColumnName().trim());
            datasetColumn.setDataType(
                    mapping.dataType().name());
            datasetColumn.setOrdinalPosition(position++);

            datasetColumnRepository.save(datasetColumn);
        }

        return new CreateDatasetResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                dataset.getImportStatus(),
                request.columns().size(),
                "PostgreSQL table created successfully."
        );
    }

    private void validateRequest(CreateDatasetRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dataset request cannot be empty.");
        }

        if (request.datasetName() == null
                || request.datasetName().isBlank()
                || request.datasetName().length() > 150) {

            throw new IllegalArgumentException(
                    "Dataset name is required and must not exceed 150 characters.");
        }

        if (request.originalFileName() == null
                || request.originalFileName().isBlank()
                || request.originalFileName().length() > 255) {

            throw new IllegalArgumentException(
                    "Original file name is required and must not exceed 255 characters.");
        }

        if (request.sheetName() == null
                || request.sheetName().isBlank()
                || request.sheetName().length() > 100) {

            throw new IllegalArgumentException(
                    "Sheet name is required and must not exceed 100 characters.");
        }

        List<ColumnMappingRequest> columns = request.columns();

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one column mapping is required.");
        }

        if (columns.size() > 200) {
            throw new IllegalArgumentException(
                    "A maximum of 200 columns is supported.");
        }

        Set<String> excelNames = new HashSet<>();
        Set<String> databaseNames = new HashSet<>();

        for (ColumnMappingRequest column : columns) {

            if (column == null
                    || column.excelColumnName() == null
                    || column.excelColumnName().isBlank()
                    || column.databaseColumnName() == null
                    || column.dataType() == null) {

                throw new IllegalArgumentException(
                        "Every mapping must contain an Excel column, database column and data type.");
            }

            String excelName = column.excelColumnName().trim();

            String databaseName =
                    column.databaseColumnName().trim();

            if (excelName.length() > 255) {
                throw new IllegalArgumentException(
                        "Excel column names cannot exceed 255 characters.");
            }

            if (!IDENTIFIER_PATTERN.matcher(databaseName).matches()) {
                throw new IllegalArgumentException(
                        "Invalid database column name: " + databaseName
                        + ". Use lowercase letters, numbers and underscores, starting with a letter.");
            }

            if (databaseName.equals("id")
                    || databaseName.equals("created_at")) {

                throw new IllegalArgumentException(
                        "'" + databaseName + "' is reserved for internal record metadata.");
            }

            if (!excelNames.add(excelName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Duplicate Excel column mapping: " + excelName);
            }

            if (!databaseNames.add(databaseName.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Duplicate database column mapping: " + databaseName);
            }
        }
    }

    private String generateTableName() {

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        return "afds_" + suffix;
    }

    private String getSqlType(ColumnDataType dataType) {

        return switch (dataType) {
            case TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case DECIMAL -> "NUMERIC";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
        };
    }
}
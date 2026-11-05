package com.altiusfortius.datastudio.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.DuplicateRecordsResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DuplicateRecordReviewService {

    private static final Pattern SAFE_TABLE =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public DuplicateRecordReviewService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public DuplicateRecordsResponse getDuplicateRecords(
            Long datasetId,
            String requestedColumn,
            String matchingValue) {

        if (requestedColumn == null || requestedColumn.isBlank()) {
            throw new IllegalArgumentException(
                    "Column name is required.");
        }

        if (matchingValue == null || matchingValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Matching value is required.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null || !SAFE_TABLE.matcher(tableName).matches()) {
            throw new IllegalStateException(
                    "Invalid dataset table name.");
        }

        String columnName = datasetColumnRepository
                .findByDatasetIdOrderByOrdinalPosition(datasetId)
                .stream()
                .filter(column -> column.getDbColumnName()
                        .equalsIgnoreCase(requestedColumn.trim()))
                .map(column -> column.getDbColumnName())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Column does not exist in this dataset."));

        if (!SAFE_COLUMN.matcher(columnName).matches()) {
            throw new IllegalStateException(
                    "Invalid database column name.");
        }

        String sql =
                "SELECT t.* FROM \"" + tableName + "\" t "
                + "WHERE CAST(t.\"" + columnName + "\" AS TEXT) = ? "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id) "
                + "ORDER BY t.id";

        List<Map<String, Object>> records =
                jdbcTemplate.queryForList(sql, matchingValue, datasetId);

        return new DuplicateRecordsResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                columnName,
                matchingValue,
                records.size(),
                records
        );
    }
}
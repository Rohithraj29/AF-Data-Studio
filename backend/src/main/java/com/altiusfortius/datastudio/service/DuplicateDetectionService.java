package com.altiusfortius.datastudio.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.DuplicateAnalysisResponse;
import com.altiusfortius.datastudio.dto.DuplicateGroupResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DuplicateDetectionService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN_NAME =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public DuplicateDetectionService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public DuplicateAnalysisResponse analyzeExactDuplicates(
            Long datasetId,
            String requestedColumnName) {

        if (requestedColumnName == null
                || requestedColumnName.isBlank()) {
            throw new IllegalArgumentException(
                    "Please provide a database column name.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found with ID: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalStateException(
                    "Invalid dynamic table name in dataset metadata.");
        }

        DatasetColumn selectedColumn =
                findDatasetColumn(datasetId, requestedColumnName.trim());

        String columnName = selectedColumn.getDbColumnName();

        if (!SAFE_COLUMN_NAME.matcher(columnName).matches()) {
            throw new IllegalStateException(
                    "Invalid database column name in metadata.");
        }

        String quotedTable = "\"" + tableName + "\"";
        String quotedColumn = "t.\"" + columnName + "\"";

        String activeCondition =
                " FROM " + quotedTable + " t "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id)";

        long totalRecords = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + activeCondition,
                Long.class,
                datasetId
        );

        String blankCountSql =
                "SELECT COUNT(*)" + activeCondition
                + " AND (" + quotedColumn + " IS NULL"
                + " OR BTRIM(CAST(" + quotedColumn + " AS TEXT)) = '')";

        long blankRecords = jdbcTemplate.queryForObject(
                blankCountSql,
                Long.class,
                datasetId
        );

        String duplicateSql =
                "SELECT CAST(" + quotedColumn + " AS TEXT) AS duplicate_value, "
                + "COUNT(*) AS occurrences "
                + activeCondition
                + " AND " + quotedColumn + " IS NOT NULL "
                + "AND BTRIM(CAST(" + quotedColumn + " AS TEXT)) <> '' "
                + "GROUP BY " + quotedColumn + " "
                + "HAVING COUNT(*) > 1 "
                + "ORDER BY occurrences DESC, duplicate_value ASC";

        List<DuplicateGroupResponse> groups =
                jdbcTemplate.query(
                        duplicateSql,
                        (resultSet, rowNumber) ->
                                new DuplicateGroupResponse(
                                        resultSet.getString("duplicate_value"),
                                        resultSet.getLong("occurrences")
                                ),
                        datasetId
                );

        long duplicateRecords = groups.stream()
                .mapToLong(DuplicateGroupResponse::occurrences)
                .sum();

        long nonBlankRecords = totalRecords - blankRecords;

        long uniqueRecords = nonBlankRecords - duplicateRecords;

        return new DuplicateAnalysisResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                columnName,
                totalRecords,
                blankRecords,
                groups.size(),
                duplicateRecords,
                uniqueRecords,
                groups
        );
    }

    private DatasetColumn findDatasetColumn(
            Long datasetId,
            String requestedColumnName) {

        return datasetColumnRepository
                .findByDatasetIdOrderByOrdinalPosition(datasetId)
                .stream()
                .filter(column -> column.getDbColumnName()
                        .equalsIgnoreCase(requestedColumnName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Column not found in this dataset: "
                                + requestedColumnName));
    }
}
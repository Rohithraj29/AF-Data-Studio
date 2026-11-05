package com.altiusfortius.datastudio.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.MergeUndoResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class MergeUndoService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final ObjectMapper objectMapper;

    public MergeUndoService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            ObjectMapper objectMapper) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MergeUndoResponse undoMerge(Long datasetId, Long operationId) {

        if (datasetId == null || datasetId <= 0
                || operationId == null || operationId <= 0) {
            throw new IllegalArgumentException(
                    "Dataset ID and merge operation ID must be positive.");
        }

        // Serialize merge and undo operations for this dataset.
        acquireDatasetLock(datasetId);

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalStateException(
                    "Invalid dataset table name.");
        }

        Map<String, Object> operation;

        try {
            operation = jdbcTemplate.queryForMap(
                    """
                    SELECT id, survivor_record_id, status
                    FROM merge_operations
                    WHERE id = ? AND dataset_id = ?
                    FOR UPDATE
                    """,
                    operationId,
                    datasetId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException(
                    "Merge operation not found for this dataset.");
        }

        if (!"COMPLETED".equals(operation.get("status"))) {
            throw new IllegalArgumentException(
                    "Only completed merge operations can be undone.");
        }

        Long latestOperationId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM merge_operations
                WHERE dataset_id = ? AND status = 'COMPLETED'
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                datasetId
        );

        if (!operationId.equals(latestOperationId)) {
            throw new IllegalArgumentException(
                    "Only the latest completed merge operation can be undone. "
                    + "Undo newer merges first.");
        }

        Long survivorId =
                ((Number) operation.get("survivor_record_id")).longValue();

        List<Map<String, Object>> snapshots =
                jdbcTemplate.queryForList(
                        """
                        SELECT record_id, record_role,
                               original_record::text AS original_record
                        FROM merge_operation_records
                        WHERE merge_operation_id = ?
                        ORDER BY record_id
                        """,
                        operationId
                );

        if (snapshots.size() < 2) {
            throw new IllegalStateException(
                    "Merge audit snapshots are incomplete. Undo was stopped.");
        }

        Map<String, Object> survivorSnapshot = snapshots.stream()
                .filter(row -> "SURVIVOR".equals(row.get("record_role")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Original survivor snapshot was not found."));

        if (((Number) survivorSnapshot.get("record_id")).longValue()
                != survivorId) {
            throw new IllegalStateException(
                    "Audit survivor does not match the merge operation.");
        }

        String snapshotJson =
                (String) survivorSnapshot.get("original_record");

        Map<String, Object> originalSurvivor;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed =
                    objectMapper.readValue(snapshotJson, Map.class);

            originalSurvivor = parsed;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read original survivor snapshot.", exception);
        }

        List<DatasetColumn> columns =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId);

        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "Dataset column metadata was not found.");
        }

        String assignments = columns.stream()
                .map(column -> {
                    String columnName = column.getDbColumnName();

                    if (columnName == null
                            || !columnName.matches("[a-z][a-z0-9_]{0,62}")) {
                        throw new IllegalStateException(
                                "Invalid database column name in metadata.");
                    }

                    return "\"" + columnName + "\" = ?";
                })
                .collect(java.util.stream.Collectors.joining(", "));

        List<Object> parameters = new ArrayList<>();

        for (DatasetColumn column : columns) {

            String columnName = column.getDbColumnName();

            if (!originalSurvivor.containsKey(columnName)) {
                throw new IllegalStateException(
                        "Original snapshot is missing field: " + columnName);
            }

            parameters.add(convertValue(
                    originalSurvivor.get(columnName),
                    column.getDataType()
            ));
        }

        parameters.add(survivorId);

        String updateSql = "UPDATE \"" + tableName
                + "\" SET " + assignments + " WHERE id = ?";

        int updatedRows = jdbcTemplate.update(
                updateSql, parameters.toArray());

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Original survivor record could not be restored.");
        }

        List<Long> reactivatedRecordIds = snapshots.stream()
                .filter(row -> "MERGED".equals(row.get("record_role")))
                .map(row -> ((Number) row.get("record_id")).longValue())
                .toList();

        if (reactivatedRecordIds.size() != snapshots.size() - 1) {
            throw new IllegalStateException(
                    "Merge audit contains unexpected record roles.");
        }

        jdbcTemplate.update(
                """
                DELETE FROM dataset_record_states
                WHERE merge_operation_id = ? AND dataset_id = ?
                """,
                operationId,
                datasetId
        );

        jdbcTemplate.update(
                """
                UPDATE merge_operations
                SET status = 'REVERSED',
                    reversed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND dataset_id = ? AND status = 'COMPLETED'
                """,
                operationId,
                datasetId
        );

        return new MergeUndoResponse(
                operationId,
                datasetId,
                survivorId,
                reactivatedRecordIds,
                "REVERSED",
                "Merge reversed. Original survivor values restored and merged records reactivated."
        );
    }

    private void acquireDatasetLock(Long datasetId) {

        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(?)",
                Object.class,
                datasetId
        );
    }

    private Object convertValue(Object value, String dataType) {

        if (value == null) {
            return null;
        }

        String text = value.toString();

        return switch (dataType.toUpperCase()) {
            case "TEXT" -> text;
            case "INTEGER" -> Integer.valueOf(text);
            case "BIGINT" -> Long.valueOf(text);
            case "DECIMAL" -> new BigDecimal(text);
            case "BOOLEAN" -> {
                if (!text.equalsIgnoreCase("true")
                        && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Invalid boolean value in original snapshot.");
                }
                yield Boolean.valueOf(text);
            }
            case "DATE" ->
                    java.sql.Date.valueOf(LocalDate.parse(text));
            case "TIMESTAMP" ->
                    Timestamp.valueOf(LocalDateTime.parse(text));
            default -> throw new IllegalArgumentException(
                    "Unsupported column data type: " + dataType);
        };
    }
}
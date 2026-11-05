package com.altiusfortius.datastudio.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.MergeHistoryRecordResponse;
import com.altiusfortius.datastudio.dto.MergeHistoryResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.repository.DatasetRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class MergeHistoryService {

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public MergeHistoryService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            ObjectMapper objectMapper) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
    }

    public List<MergeHistoryResponse> getMergeHistory(Long datasetId) {

        datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        List<Map<String, Object>> operations = jdbcTemplate.queryForList(
                """
                SELECT id, dataset_id, survivor_record_id, status,
                       field_selections::text AS field_selections,
                       final_record::text AS final_record,
                       created_at, reversed_at
                FROM merge_operations
                WHERE dataset_id = ?
                ORDER BY id DESC
                """,
                datasetId
        );

        return operations.stream()
                .map(this::mapHistory)
                .toList();
    }

    private MergeHistoryResponse mapHistory(Map<String, Object> operation) {

        Long operationId = ((Number) operation.get("id")).longValue();

        List<Map<String, Object>> originalRows =
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

        List<MergeHistoryRecordResponse> originalRecords =
                originalRows.stream()
                        .map(row -> new MergeHistoryRecordResponse(
                                ((Number) row.get("record_id")).longValue(),
                                (String) row.get("record_role"),
                                readJsonMap((String) row.get("original_record"))
                        ))
                        .toList();

        return new MergeHistoryResponse(
                operationId,
                ((Number) operation.get("dataset_id")).longValue(),
                ((Number) operation.get("survivor_record_id")).longValue(),
                (String) operation.get("status"),
                readStringMap((String) operation.get("field_selections")),
                readJsonMap((String) operation.get("final_record")),
                toOffsetDateTime(operation.get("created_at")),
                toOffsetDateTime(operation.get("reversed_at")),
                originalRecords
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read merge history JSON.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readStringMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read merge field selections.", exception);
        }
    }

    private java.time.OffsetDateTime toOffsetDateTime(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof java.time.OffsetDateTime dateTime) {
            return dateTime;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant()
                    .atOffset(java.time.ZoneOffset.UTC);
        }

        return java.time.OffsetDateTime.parse(value.toString());
    }
}
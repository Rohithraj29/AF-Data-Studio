
package com.altiusfortius.datastudio.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.MergeExecuteRequest;
import com.altiusfortius.datastudio.dto.MergeExecuteResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.enums.ColumnDataType;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class MergeExecutionService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN_NAME =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final ObjectMapper objectMapper;

    public MergeExecutionService(
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
    public MergeExecuteResponse executeMerge(
            Long datasetId,
            MergeExecuteRequest request) {

        validateRequest(request);

        String mergeMode = normalizeMode(request.mergeMode());

        acquireDatasetLock(datasetId);

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()) {

            throw new IllegalArgumentException(
                    "Invalid dataset table name.");
        }

        List<Long> recordIds = request.recordIds().stream()
                .distinct()
                .toList();

        if (recordIds.size() != request.recordIds().size()) {
            throw new IllegalArgumentException(
                    "Duplicate record IDs are not allowed.");
        }

        Long survivorId = request.survivorRecordId();

        if (!recordIds.contains(survivorId)) {
            throw new IllegalArgumentException(
                    "The survivor record must be included in recordIds.");
        }

        List<DatasetColumn> columns =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId);

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dataset has no column metadata.");
        }

        Map<String, ColumnDataType> columnTypes =
                new LinkedHashMap<>();

        for (DatasetColumn column : columns) {

            String columnName = column.getDbColumnName();

            if (columnName == null
                    || !SAFE_COLUMN_NAME.matcher(columnName).matches()) {

                throw new IllegalArgumentException(
                        "Invalid database column name in metadata.");
            }

            columnTypes.put(
                    columnName,
                    ColumnDataType.valueOf(column.getDataType()));
        }

        String matchingColumn = columns.stream()
                .map(DatasetColumn::getDbColumnName)
                .filter(name -> name.equalsIgnoreCase(
                        request.matchingColumn().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Matching column does not belong to this dataset."));

        if (!SAFE_COLUMN_NAME.matcher(matchingColumn).matches()) {
            throw new IllegalArgumentException(
                    "Invalid matching column name.");
        }

        DatasetColumn matchingMetadata = columns.stream()
                .filter(column -> column.getDbColumnName()
                        .equals(matchingColumn))
                .findFirst()
                .orElseThrow();

        if ("NEAR".equals(mergeMode)
                && !"TEXT".equalsIgnoreCase(
                        matchingMetadata.getDataType())) {

            throw new IllegalArgumentException(
                    "Near duplicate merging supports TEXT columns only.");
        }

        validateMatchingValues(request, mergeMode);

        String placeholders = recordIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));

        String selectSql =
                "SELECT t.* FROM \"" + tableName + "\" t "
                + "WHERE t.id IN (" + placeholders + ") "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id) "
                + "ORDER BY t.id FOR UPDATE OF t";

        List<Object> selectParameters = new ArrayList<>(recordIds);
        selectParameters.add(datasetId);

        List<Map<String, Object>> originalRecords =
                jdbcTemplate.queryForList(
                        selectSql,
                        selectParameters.toArray());

        if (originalRecords.size() != recordIds.size()) {
            throw new IllegalArgumentException(
                    "One or more selected records do not exist "
                    + "or have already been merged.");
        }

        // Validate the actual matching values of the selected records.

        Set<String> observedValues = new LinkedHashSet<>();

        for (Map<String, Object> record : originalRecords) {

            Object rawValue = record.get(matchingColumn);

            if (rawValue == null || rawValue.toString().isBlank()) {
                throw new IllegalArgumentException(
                        "Selected records cannot have blank matching values.");
            }

            String actualValue = rawValue.toString();

            if ("EXACT".equals(mergeMode)) {

                if (!actualValue.equals(request.matchingValue())) {
                    throw new IllegalArgumentException(
                            "All selected records must have the exact "
                            + "matching value.");
                }

            } else {

                boolean belongsToPair =
                        actualValue.equals(request.matchingValue())
                        || actualValue.equals(request.matchingValue2());

                if (!belongsToPair) {
                    throw new IllegalArgumentException(
                            "Selected records do not belong to the "
                            + "specified near-duplicate pair.");
                }

                observedValues.add(actualValue);
            }
        }

        if ("NEAR".equals(mergeMode)) {

            if (observedValues.size() != 2) {
                throw new IllegalArgumentException(
                        "Selected records must contain records "
                        + "from both near-duplicate values.");
            }

            double similarity = calculateSimilarity(
                    normalize(request.matchingValue()),
                    normalize(request.matchingValue2()));

            if (similarity < request.similarityThreshold()) {
                throw new IllegalArgumentException(
                        "The selected values do not meet the "
                        + "requested similarity threshold.");
            }
        }

        // Build the consolidated record.

        Map<String, Object> finalRecord = new LinkedHashMap<>();

        Set<String> conflictFields = new LinkedHashSet<>();

        for (DatasetColumn column : columns) {

            String columnName = column.getDbColumnName();

            Set<String> availableValues = new LinkedHashSet<>();

            for (Map<String, Object> record : originalRecords) {

                Object value = record.get(columnName);

                if (value != null && !value.toString().isBlank()) {
                    availableValues.add(value.toString());
                }
            }

            String selectedValue = null;

            if (availableValues.size() > 1) {

                conflictFields.add(columnName);

                selectedValue =
                        request.fieldSelections().get(columnName);

                if (selectedValue == null
                        || !availableValues.contains(selectedValue)) {

                    throw new IllegalArgumentException(
                            "Select a valid available value for conflicting field: "
                            + columnName);
                }

            } else if (availableValues.size() == 1) {

                selectedValue = availableValues.iterator().next();
            }

            finalRecord.put(columnName, selectedValue);
        }

        for (String selectedField :
                request.fieldSelections().keySet()) {

            if (!conflictFields.contains(selectedField)) {
                throw new IllegalArgumentException(
                        "Field selection supplied for a non-conflicting "
                        + "or unknown field: " + selectedField);
            }
        }

        // Preserve original snapshots for audit history.

        List<Map<String, Object>> auditSnapshots =
                new ArrayList<>();

        for (Map<String, Object> record : originalRecords) {
            auditSnapshots.add(normalizeRecord(record));
        }

        String fieldSelectionsJson =
                writeJson(request.fieldSelections());

        String finalRecordJson = writeJson(finalRecord);

        // Create merge operation.

        Long operationId = jdbcTemplate.queryForObject(
                """
                INSERT INTO merge_operations
                    (dataset_id, survivor_record_id, status,
                     field_selections, final_record)
                VALUES (?, ?, 'COMPLETED', ?::jsonb, ?::jsonb)
                RETURNING id
                """,
                Long.class,
                datasetId,
                survivorId,
                fieldSelectionsJson,
                finalRecordJson
        );

        // Save original records before modification.

        for (int i = 0; i < originalRecords.size(); i++) {

            Map<String, Object> record = originalRecords.get(i);

            Long recordId =
                    ((Number) record.get("id")).longValue();

            String role = recordId.equals(survivorId)
                    ? "SURVIVOR"
                    : "MERGED";

            jdbcTemplate.update(
                    """
                    INSERT INTO merge_operation_records
                        (merge_operation_id, record_id,
                         record_role, original_record)
                    VALUES (?, ?, ?, ?::jsonb)
                    """,
                    operationId,
                    recordId,
                    role,
                    writeJson(auditSnapshots.get(i))
            );
        }

        // Update survivor record.

        String updateAssignments = columns.stream()
                .map(column ->
                        "\"" + column.getDbColumnName() + "\" = ?")
                .collect(Collectors.joining(", "));

        String updateSql = "UPDATE \"" + tableName
                + "\" SET " + updateAssignments
                + " WHERE id = ?";

        List<Object> updateParameters = new ArrayList<>();

        for (DatasetColumn column : columns) {

            String columnName = column.getDbColumnName();

            updateParameters.add(convertValue(
                    (String) finalRecord.get(columnName),
                    columnTypes.get(columnName)));
        }

        updateParameters.add(survivorId);

        int updatedRows = jdbcTemplate.update(
                updateSql,
                updateParameters.toArray());

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Unable to update survivor record.");
        }

        // Mark remaining records as merged.

        List<Long> mergedRecordIds = recordIds.stream()
                .filter(id -> !id.equals(survivorId))
                .toList();

        for (Long mergedRecordId : mergedRecordIds) {

            jdbcTemplate.update(
                    """
                    INSERT INTO dataset_record_states
                        (dataset_id, record_id, record_status,
                         merged_into_record_id, merge_operation_id)
                    VALUES (?, ?, 'MERGED', ?, ?)
                    """,
                    datasetId,
                    mergedRecordId,
                    survivorId,
                    operationId
            );
        }

        String successMessage = "NEAR".equals(mergeMode)
                ? "Near-duplicate records merged successfully. "
                    + "Original records are preserved in audit history."
                : "Records merged successfully. "
                    + "Original records are preserved in audit history.";

        return new MergeExecuteResponse(
                operationId,
                datasetId,
                survivorId,
                mergedRecordIds,
                "COMPLETED",
                successMessage);
    }

    private void validateRequest(MergeExecuteRequest request) {

        if (request == null
                || request.recordIds() == null
                || request.recordIds().size() < 2) {

            throw new IllegalArgumentException(
                    "Select at least two records to merge.");
        }

        if (request.recordIds().stream().anyMatch(
                id -> id == null || id <= 0)) {

            throw new IllegalArgumentException(
                    "Record IDs must be positive numbers.");
        }

        if (request.survivorRecordId() == null
                || request.survivorRecordId() <= 0) {

            throw new IllegalArgumentException(
                    "A valid survivor record ID is required.");
        }

        if (request.matchingColumn() == null
                || request.matchingColumn().isBlank()
                || request.matchingValue() == null
                || request.matchingValue().isBlank()) {

            throw new IllegalArgumentException(
                    "Matching column and matching value are required.");
        }

        if (request.fieldSelections() == null) {
            throw new IllegalArgumentException(
                    "Field selections cannot be null. "
                    + "Use an empty JSON object if there are no conflicts.");
        }
    }

    private String normalizeMode(String mode) {

        if (mode == null || mode.isBlank()) {
            return "EXACT";
        }

        String normalized = mode.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("EXACT")
                && !normalized.equals("NEAR")) {

            throw new IllegalArgumentException(
                    "Merge mode must be EXACT or NEAR.");
        }

        return normalized;
    }

    private void validateMatchingValues(
            MergeExecuteRequest request,
            String mergeMode) {

        if ("EXACT".equals(mergeMode)) {

            if (request.matchingValue2() != null
                    && !request.matchingValue2().isBlank()) {

                throw new IllegalArgumentException(
                        "EXACT mode does not accept a second matching value.");
            }

            return;
        }

        if (request.matchingValue2() == null
                || request.matchingValue2().isBlank()) {

            throw new IllegalArgumentException(
                    "NEAR mode requires matchingValue2.");
        }

        if (request.matchingValue().equals(request.matchingValue2())) {
            throw new IllegalArgumentException(
                    "Near-duplicate values must be different.");
        }

        Double threshold = request.similarityThreshold();

        if (threshold == null
                || threshold < 0.50
                || threshold > 0.99) {

            throw new IllegalArgumentException(
                    "Similarity threshold must be between 0.50 and 0.99.");
        }
    }

    private String normalize(String value) {

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private double calculateSimilarity(
            String first,
            String second) {

        if (first.equals(second)) {
            return 1.0;
        }

        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }

        int distance = levenshteinDistance(first, second);

        int maximumLength = Math.max(
                first.length(),
                second.length());

        return 1.0 - ((double) distance / maximumLength);
    }

    private int levenshteinDistance(
            String first,
            String second) {

        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];

        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {

            current[0] = i;

            for (int j = 1; j <= second.length(); j++) {

                int cost = first.charAt(i - 1)
                        == second.charAt(j - 1) ? 0 : 1;

                current[j] = Math.min(
                        Math.min(
                                current[j - 1] + 1,
                                previous[j] + 1),
                        previous[j - 1] + cost);
            }

            int[] temporary = previous;
            previous = current;
            current = temporary;
        }

        return previous[second.length()];
    }

    private Object convertValue(
            String value,
            ColumnDataType dataType) {

        if (value == null) {
            return null;
        }

        return switch (dataType) {
            case TEXT -> value;
            case INTEGER -> Integer.valueOf(value);
            case BIGINT -> Long.valueOf(value);
            case DECIMAL -> new BigDecimal(value);
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true")
                        && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Invalid boolean value: " + value);
                }
                yield Boolean.valueOf(value);
            }
            case DATE -> LocalDate.parse(value);
            case TIMESTAMP ->
                    Timestamp.valueOf(LocalDateTime.parse(value));
        };
    }

    private Map<String, Object> normalizeRecord(
            Map<String, Object> record) {

        Map<String, Object> normalized = new LinkedHashMap<>();

        record.forEach((key, value) -> {

            if (value instanceof java.sql.Date date) {
                normalized.put(key, date.toLocalDate().toString());

            } else if (value instanceof Timestamp timestamp) {
                normalized.put(
                        key,
                        timestamp.toLocalDateTime().toString());

            } else {
                normalized.put(key, value);
            }
        });

        return normalized;
    }

    private String writeJson(Object value) {

        try {
            return objectMapper.writeValueAsString(value);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create merge audit JSON.",
                    exception);
        }
    }

    private void acquireDatasetLock(Long datasetId) {

        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(?)",
                Object.class,
                datasetId);
    }
}

package com.altiusfortius.datastudio.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.MergeFieldResponse;
import com.altiusfortius.datastudio.dto.MergePreviewRequest;
import com.altiusfortius.datastudio.dto.MergePreviewResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class MergePreviewService {

    private static final Pattern SAFE_TABLE =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public MergePreviewService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public MergePreviewResponse createPreview(
            Long datasetId,
            MergePreviewRequest request) {

        validateRequest(request);

        String mergeMode = normalizeMode(request.mergeMode());

        Set<Long> uniqueIds =
                new LinkedHashSet<>(request.recordIds());

        if (uniqueIds.size() != request.recordIds().size()
                || uniqueIds.contains(null)
                || uniqueIds.stream().anyMatch(id -> id <= 0)) {

            throw new IllegalArgumentException(
                    "Record IDs must be unique positive numbers.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null
                || !SAFE_TABLE.matcher(tableName).matches()) {

            throw new IllegalStateException(
                    "Invalid dynamic table name in dataset metadata.");
        }

        List<DatasetColumn> columns =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId);

        DatasetColumn matchingColumn = columns.stream()
                .filter(column -> column.getDbColumnName()
                        .equalsIgnoreCase(
                                request.matchingColumn().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Matching column does not belong to this dataset."));

        String columnName = matchingColumn.getDbColumnName();

        if (!SAFE_COLUMN.matcher(columnName).matches()) {
            throw new IllegalStateException(
                    "Invalid matching column name.");
        }

        if ("NEAR".equals(mergeMode)
                && !"TEXT".equalsIgnoreCase(
                        matchingColumn.getDataType())) {

            throw new IllegalArgumentException(
                    "Near duplicate merging supports TEXT columns only.");
        }

        validateMatchingValues(request, mergeMode);

        String placeholders = String.join(
                ", ",
                uniqueIds.stream().map(id -> "?").toList());

        /*
         * Check whether this dataset table has created_at.
         * Older datasets created before this update will not have it.
         */
        boolean hasCreatedAt = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = current_schema()
                              AND table_name = ?
                              AND column_name = 'created_at'
                        )
                        """,
                        Boolean.class,
                        tableName));

        /*
         * New datasets:
         *   Newest timestamp first, then highest ID.
         *
         * Existing datasets:
         *   Highest ID first as a fallback.
         */
        String recordOrder = hasCreatedAt
                ? "t.created_at DESC NULLS LAST, t.id DESC"
                : "t.id DESC";

        String sql =
                "SELECT t.* FROM \"" + tableName + "\" t "
                + "WHERE t.id IN (" + placeholders + ") "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id) "
                + "ORDER BY " + recordOrder;

        List<Object> parameters = new ArrayList<>(uniqueIds);
        parameters.add(datasetId);

        List<Map<String, Object>> records =
                jdbcTemplate.queryForList(
                        sql,
                        parameters.toArray());

        if (records.size() != uniqueIds.size()) {
            throw new IllegalArgumentException(
                    "One or more selected records do not exist "
                    + "or have already been merged.");
        }

        Set<String> observedValues = new LinkedHashSet<>();

        for (Map<String, Object> record : records) {

            Object rawValue = record.get(columnName);

            if (rawValue == null
                    || rawValue.toString().isBlank()) {

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

                boolean belongsToCandidatePair =
                        actualValue.equals(request.matchingValue())
                        || actualValue.equals(request.matchingValue2());

                if (!belongsToCandidatePair) {
                    throw new IllegalArgumentException(
                            "Selected records do not belong to the "
                            + "specified near-duplicate pair.");
                }

                observedValues.add(actualValue);
            }
        }

        if ("NEAR".equals(mergeMode)
                && observedValues.size() != 2) {

            throw new IllegalArgumentException(
                    "Select records from both near-duplicate values.");
        }

        if ("NEAR".equals(mergeMode)) {

            double similarity = calculateSimilarity(
                    normalize(request.matchingValue()),
                    normalize(request.matchingValue2()));

            if (similarity < request.similarityThreshold()) {
                throw new IllegalArgumentException(
                        "The selected values do not meet the "
                        + "requested similarity threshold.");
            }
        }

        /*
         * Build the field suggestions.
         *
         * Records are already ordered newest-first.
         * Therefore, the first non-blank value for each field
         * is the value from the newest record that contains data.
         */
        List<MergeFieldResponse> fields = new ArrayList<>();

        for (DatasetColumn column : columns) {

            String fieldName = column.getDbColumnName();

            if (fieldName == null
                    || !SAFE_COLUMN.matcher(fieldName).matches()) {

                throw new IllegalStateException(
                        "Invalid column name in dataset metadata.");
            }

            Set<String> availableValues = new LinkedHashSet<>();

            for (Map<String, Object> record : records) {

                Object value = record.get(fieldName);

                if (value != null && !value.toString().isBlank()) {
                    availableValues.add(value.toString());
                }
            }

            List<String> options =
                    new ArrayList<>(availableValues);

            boolean hasConflict = options.size() > 1;

            /*
             * The first available value belongs to the newest
             * record containing a non-blank value.
             *
             * Manual override remains available in the frontend.
             */
            String suggestedValue = options.isEmpty()
                    ? null
                    : options.get(0);

            fields.add(new MergeFieldResponse(
                    fieldName,
                    options,
                    hasConflict,
                    suggestedValue));
        }

        String message = "NEAR".equals(mergeMode)
                ? "Validated near-duplicate pair. No records have been modified."
                : "Validated exact duplicate group. No records have been modified.";

        return new MergePreviewResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                new ArrayList<>(uniqueIds),
                fields,
                "PREVIEW_READY",
                message);
    }

    private void validateRequest(MergePreviewRequest request) {

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

        if (request.matchingColumn() == null
                || request.matchingColumn().isBlank()
                || request.matchingValue() == null
                || request.matchingValue().isBlank()) {

            throw new IllegalArgumentException(
                    "Matching column and matching value are required.");
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
            MergePreviewRequest request,
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
}
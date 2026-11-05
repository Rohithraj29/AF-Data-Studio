
package com.altiusfortius.datastudio.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.NearDuplicateRecordReviewResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class NearDuplicateRecordReviewService {

    private static final Pattern SAFE_TABLE =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private static final int MAX_RETURNED_RECORDS = 500;

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public NearDuplicateRecordReviewService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public NearDuplicateRecordReviewResponse reviewRecords(
            Long datasetId,
            String requestedColumn,
            String value1,
            String value2,
            double threshold) {

        if (requestedColumn == null || requestedColumn.isBlank()
                || value1 == null || value1.isBlank()
                || value2 == null || value2.isBlank()) {

            throw new IllegalArgumentException(
                    "Column name and both matching values are required.");
        }

        if (value1.equals(value2)) {
            throw new IllegalArgumentException(
                    "Near-duplicate values must be different.");
        }

        if (threshold < 0.50 || threshold > 0.99) {
            throw new IllegalArgumentException(
                    "Similarity threshold must be between 0.50 and 0.99.");
        }

        double similarity = calculateSimilarity(
                normalize(value1),
                normalize(value2));

        if (similarity < threshold) {
            throw new IllegalArgumentException(
                    "The supplied values do not meet the similarity threshold.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        String tableName = dataset.getTableName();

        if (tableName == null
                || !SAFE_TABLE.matcher(tableName).matches()) {

            throw new IllegalStateException(
                    "Invalid dataset table name.");
        }

        DatasetColumn selectedColumn =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId)
                        .stream()
                        .filter(column -> column.getDbColumnName()
                                .equalsIgnoreCase(requestedColumn.trim()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Column does not belong to this dataset."));

        if (!"TEXT".equalsIgnoreCase(
                selectedColumn.getDataType())) {

            throw new IllegalArgumentException(
                    "Near duplicate review supports TEXT columns only.");
        }

        String columnName = selectedColumn.getDbColumnName();

        if (!SAFE_COLUMN.matcher(columnName).matches()) {
            throw new IllegalStateException(
                    "Invalid database column name.");
        }

        String countSql =
                "SELECT COUNT(*) FROM \"" + tableName + "\" t "
                + "WHERE CAST(t.\"" + columnName + "\" AS TEXT) IN (?, ?) "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id)";

        Long totalRecords = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                value1,
                value2,
                datasetId);

        String recordsSql =
                "SELECT t.* FROM \"" + tableName + "\" t "
                + "WHERE CAST(t.\"" + columnName + "\" AS TEXT) IN (?, ?) "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id) "
                + "ORDER BY t.id LIMIT ?";

        List<Map<String, Object>> records =
                jdbcTemplate.queryForList(
                        recordsSql,
                        value1,
                        value2,
                        datasetId,
                        MAX_RETURNED_RECORDS);

        long matchingRecords =
                totalRecords == null ? 0 : totalRecords;

        return new NearDuplicateRecordReviewResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                columnName,
                value1,
                value2,
                Math.round(similarity * 10000.0) / 100.0,
                matchingRecords,
                records.size(),
                matchingRecords > records.size(),
                records);
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

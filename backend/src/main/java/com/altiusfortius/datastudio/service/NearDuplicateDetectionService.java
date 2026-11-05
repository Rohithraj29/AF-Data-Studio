package com.altiusfortius.datastudio.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.NearDuplicateAnalysisResponse;
import com.altiusfortius.datastudio.dto.NearDuplicatePairResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class NearDuplicateDetectionService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN_NAME =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private static final int MAX_UNIQUE_VALUES = 2000;
    private static final int MAX_RETURNED_MATCHES = 500;

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public NearDuplicateDetectionService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public NearDuplicateAnalysisResponse analyzeNearDuplicates(
            Long datasetId,
            String requestedColumn,
            double threshold) {

        if (requestedColumn == null || requestedColumn.isBlank()) {
            throw new IllegalArgumentException(
                    "Column name is required.");
        }

        if (threshold < 0.50 || threshold > 0.99) {
            throw new IllegalArgumentException(
                    "Similarity threshold must be between 0.50 and 0.99.");
        }

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found: " + datasetId));

        DatasetColumn selectedColumn =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId)
                        .stream()
                        .filter(column -> column.getDbColumnName()
                                .equalsIgnoreCase(requestedColumn.trim()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Column not found: " + requestedColumn));

        if (!"TEXT".equalsIgnoreCase(selectedColumn.getDataType())) {
            throw new IllegalArgumentException(
                    "Near duplicate detection currently supports TEXT columns only.");
        }

        String tableName = dataset.getTableName();
        String columnName = selectedColumn.getDbColumnName();

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()
                || !SAFE_COLUMN_NAME.matcher(columnName).matches()) {

            throw new IllegalStateException(
                    "Invalid table or column name in dataset metadata.");
        }

        String sql =
                "SELECT CAST(t.\"" + columnName
                + "\" AS TEXT) AS value, COUNT(*) AS occurrences "
                + "FROM \"" + tableName + "\" t "
                + "WHERE t.\"" + columnName + "\" IS NOT NULL "
                + "AND BTRIM(CAST(t.\"" + columnName + "\" AS TEXT)) <> '' "
                + "AND NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? AND s.record_id = t.id) "
                + "GROUP BY t.\"" + columnName + "\" "
                + "ORDER BY value";

        List<Map<String, Object>> values =
                jdbcTemplate.queryForList(sql, datasetId);

        if (values.size() > MAX_UNIQUE_VALUES) {
            throw new IllegalArgumentException(
                    "This column contains more than "
                    + MAX_UNIQUE_VALUES
                    + " unique values. Narrow the dataset before running near duplicate detection.");
        }

        List<NearDuplicatePairResponse> matches = new ArrayList<>();

        for (int i = 0; i < values.size(); i++) {

            String value1 = (String) values.get(i).get("value");

            long occurrences1 =
                    ((Number) values.get(i).get("occurrences")).longValue();

            for (int j = i + 1; j < values.size(); j++) {

                String value2 = (String) values.get(j).get("value");

                if (normalize(value1).equals(normalize(value2))) {
                    continue;
                }

                double similarity = calculateSimilarity(
                        normalize(value1),
                        normalize(value2));

                if (similarity >= threshold) {

                    long occurrences2 =
                            ((Number) values.get(j)
                                    .get("occurrences")).longValue();

                    matches.add(new NearDuplicatePairResponse(
                            value1,
                            value2,
                            Math.round(similarity * 10000.0) / 100.0,
                            occurrences1,
                            occurrences2
                    ));
                }
            }
        }

        matches.sort(
                Comparator.comparingDouble(
                        NearDuplicatePairResponse::similarityPercentage)
                        .reversed()
        );

        long candidatePairs = matches.size();

        List<NearDuplicatePairResponse> previewMatches =
                matches.stream()
                        .limit(MAX_RETURNED_MATCHES)
                        .toList();

        return new NearDuplicateAnalysisResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                tableName,
                columnName,
                threshold * 100,
                values.size(),
                candidatePairs,
                previewMatches
        );
    }

    private String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private double calculateSimilarity(String first, String second) {

        if (first.equals(second)) {
            return 1.0;
        }

        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }

        int distance = levenshteinDistance(first, second);

        int maximumLength = Math.max(first.length(), second.length());

        return 1.0 - ((double) distance / maximumLength);
    }

    private int levenshteinDistance(String first, String second) {

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
                                previous[j] + 1
                        ),
                        previous[j - 1] + cost
                );
            }

            int[] temporary = previous;
            previous = current;
            current = temporary;
        }

        return previous[second.length()];
    }
}

package com.altiusfortius.datastudio.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.dto.ColumnQualityResponse;
import com.altiusfortius.datastudio.dto.DataQualityResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.entity.DatasetColumn;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DataQualityService {

    private static final Pattern SAFE_TABLE =
            Pattern.compile("afds_[a-f0-9]{8}");

    private static final Pattern SAFE_COLUMN =
            Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private static final String ACTIVE_RECORDS =
            " FROM %s t WHERE NOT EXISTS (" +
            "SELECT 1 FROM dataset_record_states s " +
            "WHERE s.dataset_id = ? AND s.record_id = t.id)";

    private final JdbcTemplate jdbcTemplate;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;

    public DataQualityService(
            JdbcTemplate jdbcTemplate,
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository) {

        this.jdbcTemplate = jdbcTemplate;
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
    }

    public DataQualityResponse analyze(Long datasetId) {

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found with ID: " + datasetId));

        String table = dataset.getTableName();

        if (table == null || !SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException(
                    "Invalid dynamic table name in dataset metadata.");
        }

        String from = String.format(
                ACTIVE_RECORDS, "\"" + table + "\"");

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from,
                Long.class,
                datasetId);

        List<DatasetColumn> datasetColumns =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId);

        List<ColumnQualityResponse> results = new ArrayList<>();

        long allBlanks = 0;
        long allDuplicates = 0;
        long allInvalid = 0;

        double completenessSum = 0;
        double validitySum = 0;
        double uniquenessSum = 0;

        for (DatasetColumn metadata : datasetColumns) {

            String column = metadata.getDbColumnName();

            if (column == null ||
                    !SAFE_COLUMN.matcher(column).matches()) {

                throw new IllegalStateException(
                        "Invalid database column name in dataset metadata.");
            }

            String quoted = "t.\"" + column + "\"";
            String value = "CAST(" + quoted + " AS TEXT)";

            long blanks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*)" + from +
                    " AND (" + quoted + " IS NULL OR BTRIM(" +
                    value + ") = '')",
                    Long.class,
                    datasetId);

            long complete = total - blanks;

            long duplicateRecords = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(group_count), 0) FROM (" +
                    "SELECT COUNT(*) AS group_count" + from +
                    " AND " + quoted + " IS NOT NULL AND BTRIM(" +
                    value + ") <> '' GROUP BY " + quoted +
                    " HAVING COUNT(*) > 1) duplicate_groups",
                    Long.class,
                    datasetId);

            long invalid = 0;

            boolean emailColumn =
                    column.toLowerCase().contains("email") ||
                    metadata.getExcelColumnName()
                            .toLowerCase().contains("email");

            if (emailColumn) {

                invalid = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + from +
                        " AND " + quoted +
                        " IS NOT NULL AND BTRIM(" + value +
                        ") <> '' AND BTRIM(" + value +
                        ") !~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$'",
                        Long.class,
                        datasetId);
            }

            double completeness = percentage(complete, total);

            double validity = emailColumn
                    ? percentage(complete - invalid, complete)
                    : 100.0;

            double uniqueness = percentage(
                    complete - duplicateRecords, complete);

            results.add(new ColumnQualityResponse(
                    metadata.getExcelColumnName(),
                    column,
                    metadata.getDataType(),
                    total,
                    blanks,
                    complete,
                    round(completeness),
                    duplicateRecords,
                    invalid,
                    round(validity),
                    round(uniqueness)));

            allBlanks += blanks;
            allDuplicates += duplicateRecords;
            allInvalid += invalid;

            completenessSum += completeness;
            validitySum += validity;
            uniquenessSum += uniqueness;
        }

        int count = results.size();

        double completeness =
                count == 0 ? 100 : completenessSum / count;

        double validity =
                count == 0 ? 100 : validitySum / count;

        double uniqueness =
                count == 0 ? 100 : uniquenessSum / count;

        double overall =
                (completeness + validity + uniqueness) / 3.0;

        return new DataQualityResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                table,
                total,
                count,
                allBlanks,
                allDuplicates,
                allInvalid,
                round(completeness),
                round(validity),
                round(uniqueness),
                round(overall),
                List.copyOf(results));
    }

    private static double percentage(
            long numerator, long denominator) {

        if (denominator <= 0) {
            return 100.0;
        }

        return 100.0 * numerator / denominator;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

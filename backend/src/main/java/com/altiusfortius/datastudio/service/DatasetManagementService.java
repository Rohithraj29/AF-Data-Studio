package com.altiusfortius.datastudio.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.DatasetDataResponse;
import com.altiusfortius.datastudio.dto.DatasetDetailsResponse;
import com.altiusfortius.datastudio.dto.DatasetListResponse;
import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DatasetManagementService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final JdbcTemplate jdbcTemplate;

    public DatasetManagementService(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            JdbcTemplate jdbcTemplate) {

        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DatasetListResponse> getAllDatasets() {

        return datasetRepository.findAll(
                Sort.by(Sort.Direction.DESC, "importedAt")
        ).stream().map(dataset -> new DatasetListResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                dataset.getOriginalFileName(),
                dataset.getTableName(),
                dataset.getTotalRows(),
                dataset.getTotalColumns(),
                dataset.getImportStatus(),
                dataset.getImportedAt()
        )).toList();
    }

    public DatasetDetailsResponse getDatasetDetails(Long datasetId) {

        Dataset dataset = findDataset(datasetId);

        List<DatasetDetailsResponse.ColumnDetails> columns =
                datasetColumnRepository
                        .findByDatasetIdOrderByOrdinalPosition(datasetId)
                        .stream()
                        .map(column ->
                                new DatasetDetailsResponse.ColumnDetails(
                                        column.getExcelColumnName(),
                                        column.getDbColumnName(),
                                        column.getDataType(),
                                        column.getOrdinalPosition()
                                ))
                        .toList();

        return new DatasetDetailsResponse(
                dataset.getId(),
                dataset.getDatasetName(),
                dataset.getOriginalFileName(),
                dataset.getTableName(),
                dataset.getSheetName(),
                dataset.getTotalRows(),
                dataset.getTotalColumns(),
                dataset.getImportStatus(),
                dataset.getImportedAt(),
                columns
        );
    }

    public DatasetDataResponse getDatasetData(
            Long datasetId, int page, int pageSize) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative.");
        }

        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 500.");
        }

        Dataset dataset = findDataset(datasetId);
        String tableName = validateTableName(dataset.getTableName());

        String activeCondition =
                " WHERE NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? "
                + "AND s.record_id = t.id)";

        String countSql = "SELECT COUNT(*) FROM \"" + tableName
                + "\" t" + activeCondition;

        Long totalRecords = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                datasetId
        );

        long offset = (long) page * pageSize;

        String dataSql = "SELECT t.* FROM \"" + tableName
                + "\" t"
                + activeCondition
                + " ORDER BY t.id LIMIT ? OFFSET ?";

        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                dataSql,
                datasetId,
                pageSize,
                offset
        );

        int totalPages = (int) Math.ceil(
                (double) totalRecords / pageSize);

        return new DatasetDataResponse(
                dataset.getId(),
                tableName,
                totalRecords,
                page,
                pageSize,
                totalPages,
                records
        );
    }

    @Transactional
    public void deleteDataset(Long datasetId) {

        Dataset dataset = findDataset(datasetId);

        String tableName = validateTableName(dataset.getTableName());

        jdbcTemplate.execute(
                "DROP TABLE \"" + tableName + "\""
        );

        datasetRepository.delete(dataset);
    }

    private Dataset findDataset(Long datasetId) {

        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found with ID: " + datasetId));
    }

    private String validateTableName(String tableName) {

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()) {

            throw new IllegalStateException(
                    "Invalid dataset table name in metadata.");
        }

        return tableName;
    }
}
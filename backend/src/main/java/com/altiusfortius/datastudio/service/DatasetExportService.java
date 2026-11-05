package com.altiusfortius.datastudio.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import com.altiusfortius.datastudio.entity.Dataset;
import com.altiusfortius.datastudio.repository.DatasetColumnRepository;
import com.altiusfortius.datastudio.repository.DatasetRepository;

@Service
public class DatasetExportService {

    private static final Pattern SAFE_TABLE_NAME =
            Pattern.compile("afds_[a-f0-9]{8}");

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final JdbcTemplate jdbcTemplate;

    public DatasetExportService(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            JdbcTemplate jdbcTemplate) {
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void writeCsv(Long datasetId, OutputStream outputStream)
            throws IOException {

        Dataset dataset = findDataset(datasetId);
        String tableName = validateTableName(dataset.getTableName());
        List<ColumnExport> columns = getColumns(datasetId);
        String sql = activeRecordsSql(tableName);

        Writer writer = new OutputStreamWriter(
                outputStream, StandardCharsets.UTF_8);

        writer.write('\uFEFF');

        writeCsvRow(
                writer,
                columns.stream().map(ColumnExport::excelName).toList()
        );

        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) rs -> {
            while (rs.next()) {
                List<String> values = columns.stream()
                        .map(column ->
                                readValue(rs, column.databaseName()))
                        .toList();

                try {
                    writeCsvRow(writer, values);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
            return null;
        }, datasetId);

        writer.flush();
    }

    public void writeExcel(Long datasetId, OutputStream outputStream)
            throws IOException {

        Dataset dataset = findDataset(datasetId);
        String tableName = validateTableName(dataset.getTableName());
        List<ColumnExport> columns = getColumns(datasetId);
        String sql = activeRecordsSql(tableName);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(500)) {

            workbook.setCompressTempFiles(true);

            Sheet sheet = workbook.createSheet("Data");

            Row header = sheet.createRow(0);

            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i)
                        .setCellValue(columns.get(i).excelName());
            }

            final int[] rowNumber = {1};

            jdbcTemplate.query(sql, (ResultSetExtractor<Void>) rs -> {

                while (rs.next()) {
                    Row row = sheet.createRow(rowNumber[0]++);

                    for (int i = 0; i < columns.size(); i++) {
                        String value = readValue(
                                rs,
                                columns.get(i).databaseName()
                        );

                        if (value != null) {
                            row.createCell(i).setCellValue(value);
                        }
                    }
                }

                return null;
            }, datasetId);

            workbook.write(outputStream);
            outputStream.flush();
            workbook.dispose();
        }
    }

    private List<ColumnExport> getColumns(Long datasetId) {

        List<ColumnExport> columns = datasetColumnRepository
                .findByDatasetIdOrderByOrdinalPosition(datasetId)
                .stream()
                .map(column -> new ColumnExport(
                        column.getExcelColumnName(),
                        column.getDbColumnName()
                ))
                .toList();

        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "No column metadata found for this dataset."
            );
        }

        return columns;
    }

    private String activeRecordsSql(String tableName) {

        return "SELECT t.* FROM \"" + tableName + "\" t "
                + "WHERE NOT EXISTS ("
                + "SELECT 1 FROM dataset_record_states s "
                + "WHERE s.dataset_id = ? "
                + "AND s.record_id = t.id) "
                + "ORDER BY t.id";
    }

    private String readValue(ResultSet rs, String columnName) {

        try {
            Object value = rs.getObject(columnName);
            return value == null ? null : value.toString();

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to read exported column: " + columnName,
                    exception
            );
        }
    }

    private void writeCsvRow(
            Writer writer,
            List<String> values) throws IOException {

        for (int i = 0; i < values.size(); i++) {

            if (i > 0) {
                writer.write(',');
            }

            String value = values.get(i);

            if (value == null) {
                value = "";
            }

            // Prevent spreadsheet formula execution in CSV exports.
            if (!value.isEmpty()
                    && "=+-@".indexOf(value.charAt(0)) >= 0) {
                value = "'" + value;
            }

            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }

        writer.write("\r\n");
    }

    private Dataset findDataset(Long datasetId) {

        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dataset not found with ID: " + datasetId
                ));
    }

    private String validateTableName(String tableName) {

        if (tableName == null
                || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalStateException(
                    "Invalid dataset table name in metadata."
            );
        }

        return tableName;
    }

    private record ColumnExport(
            String excelName,
            String databaseName) {
    }
}
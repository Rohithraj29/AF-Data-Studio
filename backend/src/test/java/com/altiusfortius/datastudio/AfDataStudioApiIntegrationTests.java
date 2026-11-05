
package com.altiusfortius.datastudio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.ColumnMappingRequest;
import com.altiusfortius.datastudio.dto.ExcelImportRequest;
import com.altiusfortius.datastudio.enums.ColumnDataType;
import com.altiusfortius.datastudio.service.ExcelDataImportService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AfDataStudioApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExcelDataImportService excelDataImportService;

    @Test
    void testGetAllDatasetsApi() throws Exception {

        importTestDataset("API Dataset List Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"}
                });

        mockMvc.perform(get("/api/datasets"))
                .andExpect(status().isOk());
    }

    @Test
    void testDatasetDetailsAndDataApi() throws Exception {

        Long datasetId = importTestDataset(
                "API Dataset Details Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                });

        mockMvc.perform(get("/api/datasets/{id}", datasetId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/datasets/{id}/data", datasetId)
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void testExactDuplicateDetectionApi() throws Exception {

        Long datasetId = importTestDataset(
                "API Exact Duplicate Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rohith B", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                });

        mockMvc.perform(get(
                        "/api/datasets/{id}/duplicates/exact", datasetId)
                        .param("column", "email"))
                .andExpect(status().isOk());
    }

    @Test
    void testNearDuplicateDetectionApi() throws Exception {

        Long datasetId = importTestDataset(
                "API Near Duplicate Test",
                new String[][] {
                        {"Rohith", "one@example.com"},
                        {"Rohith B", "two@example.com"},
                        {"Rahul", "three@example.com"}
                });

        mockMvc.perform(get(
                        "/api/datasets/{id}/duplicates/near", datasetId)
                        .param("column", "name")
                        .param("threshold", "0.70"))
                .andExpect(status().isOk());
    }

    @Test
    void testMergePreviewExecuteHistoryAndUndoApis()
            throws Exception {

        Long datasetId = importTestDataset(
                "API Merge Workflow Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rohith B", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                });

        String tableName = getTableName(datasetId);

        List<Long> recordIds = jdbcTemplate.queryForList(
                "SELECT id FROM \"" + tableName
                        + "\" WHERE email = ? ORDER BY id",
                Long.class,
                "rohith@example.com");

        Long survivorId = recordIds.get(0);

        String previewBody = """
                {
                    "recordIds": [%d, %d],
                    "matchingColumn": "email",
                    "matchingValue": "rohith@example.com"
                }
                """.formatted(recordIds.get(0), recordIds.get(1));

        mockMvc.perform(post(
                        "/api/datasets/{id}/merge/preview", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk());

        String executeBody = """
                {
                    "recordIds": [%d, %d],
                    "survivorRecordId": %d,
                    "fieldSelections": {
                        "name": "Rohith"
                    },
                    "matchingColumn": "email",
                    "matchingValue": "rohith@example.com",
                    "mergeMode": "EXACT",
                    "matchingValue2": null,
                    "similarityThreshold": null
                }
                """.formatted(
                recordIds.get(0),
                recordIds.get(1),
                survivorId);

        mockMvc.perform(post(
                        "/api/datasets/{id}/merge/execute", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executeBody))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/datasets/{id}/merge/history", datasetId))
                .andExpect(status().isOk());

        Long operationId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM merge_operations
                WHERE dataset_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                datasetId);

        mockMvc.perform(post(
                        "/api/datasets/{datasetId}/merge/{operationId}/undo",
                        datasetId,
                        operationId))
                .andExpect(status().isOk());

        String restoredName = jdbcTemplate.queryForObject(
                "SELECT name FROM \"" + tableName + "\" WHERE id = ?",
                String.class,
                survivorId);

        org.junit.jupiter.api.Assertions.assertEquals(
                "Rohith", restoredName);
    }

    @Test
    void testInvalidExactDuplicateColumnReturnsBadRequest()
            throws Exception {

        Long datasetId = importTestDataset(
                "API Invalid Column Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"}
                });

        mockMvc.perform(get(
                        "/api/datasets/{id}/duplicates/exact", datasetId)
                        .param("column", "invalid_column"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExcelPreviewApi() throws Exception {

        byte[] excelBytes;

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("Customers");

            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Rohith");
            row.createCell(1).setCellValue("rohith@example.com");

            workbook.write(output);
            excelBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "api-test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes);

        mockMvc.perform(multipart("/api/import/preview")
                        .file(file))
                .andExpect(status().isOk());
    }

    @Test
    void testMissingExcelFileReturnsClientError() throws Exception {

        mockMvc.perform(multipart("/api/import/preview"))
                .andExpect(status().is4xxClientError());
    }

    private Long importTestDataset(
            String datasetName,
            String[][] records) throws Exception {

        byte[] excelBytes;

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            var sheet = workbook.createSheet("Customers");

            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email");

            for (int i = 0; i < records.length; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(records[i][0]);
                row.createCell(1).setCellValue(records[i][1]);
            }

            workbook.write(output);
            excelBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integration-test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes);

        ExcelImportRequest request = new ExcelImportRequest(
                datasetName,
                List.of(
                        new ColumnMappingRequest(
                                "Name", "name", ColumnDataType.TEXT),
                        new ColumnMappingRequest(
                                "Email", "email", ColumnDataType.TEXT)
                )
        );

        excelDataImportService.importExcel(file, request);

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM datasets
                WHERE dataset_name = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                datasetName);
    }

    private String getTableName(Long datasetId) {

        return jdbcTemplate.queryForObject(
                "SELECT table_name FROM datasets WHERE id = ?",
                String.class,
                datasetId);
    }
    
    @Test
    void verifyTestDatabaseConnection() {
        String databaseName = jdbcTemplate.queryForObject(
                "SELECT current_database()", String.class);

        System.out.println("CONNECTED TEST DATABASE: " + databaseName);

        org.junit.jupiter.api.Assertions.assertEquals(
                "dataforge_test_db", databaseName);
    }
    
}


package com.altiusfortius.datastudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.altiusfortius.datastudio.dto.ColumnMappingRequest;
import com.altiusfortius.datastudio.dto.ExcelImportRequest;
import com.altiusfortius.datastudio.dto.MergeExecuteRequest;
import com.altiusfortius.datastudio.service.ExcelDataImportService;
import com.altiusfortius.datastudio.service.DuplicateDetectionService;
import com.altiusfortius.datastudio.service.NearDuplicateDetectionService;
import com.altiusfortius.datastudio.service.MergeExecutionService;
import com.altiusfortius.datastudio.enums.ColumnDataType;
import com.altiusfortius.datastudio.service.MergeUndoService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AfDataStudioApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExcelDataImportService excelDataImportService;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Autowired
    private NearDuplicateDetectionService nearDuplicateDetectionService;

    @Autowired
    private MergeExecutionService mergeExecutionService;
    
    @Autowired
    private MergeUndoService mergeUndoService;

    @Test
    void contextLoads() {
        assertNotNull(jdbcTemplate);
    }

    @Test
    void testDatabaseConnection() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class);

        assertEquals(1, result);
    }

    @Test
    void testRequiredTablesExist() {

        List<String> tables = List.of(
                "datasets",
                "dataset_columns",
                "merge_operations",
                "merge_operation_records",
                "dataset_record_states"
        );

        for (String table : tables) {

            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                    AND table_name = ?
                    """,
                    Integer.class,
                    table
            );

            assertEquals(1, count, "Missing table: " + table);
        }
    }

    @Test
    void testExcelImport() throws Exception {

        Long datasetId = importTestDataset(
                "Import Integration Test",
                new String[][] {
                        {"Test Customer", "test@example.com"},
                        {"Second Customer", "second@example.com"}
                }
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM datasets WHERE id = ?",
                Integer.class,
                datasetId
        );

        assertEquals(1, count);
    }

    @Test
    void testExactDuplicateDetection() throws Exception {

        Long datasetId = importTestDataset(
                "Exact Duplicate Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rohith B", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                }
        );

        var result = duplicateDetectionService
                .analyzeExactDuplicates(datasetId, "email");

        assertNotNull(result);
        assertEquals(3L, result.totalRecords());
        assertEquals(1L, result.duplicateGroups());
        assertEquals(2L, result.duplicateRecords());
        assertEquals(1L, result.uniqueRecords());
        assertEquals(0L, result.blankRecords());

        assertEquals(
                "rohith@example.com",
                result.groups().get(0).value()
        );

        assertEquals(
                2L,
                result.groups().get(0).occurrences()
        );
    }

    @Test
    void testNearDuplicateDetection() throws Exception {

        Long datasetId = importTestDataset(
                "Near Duplicate Test",
                new String[][] {
                        {"Rohith", "one@example.com"},
                        {"Rohith B", "two@example.com"},
                        {"Rahul", "three@example.com"}
                }
        );

        var result = nearDuplicateDetectionService
                .analyzeNearDuplicates(datasetId, "name", 0.70);

        assertNotNull(result);
        assertTrue(result.candidatePairs() >= 1);
    }

    @Test
    void testMergeExecution() throws Exception {

        Long datasetId = importTestDataset(
                "Merge Execution Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rohith B", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                }
        );

        String tableName = getTableName(datasetId);

        List<Long> recordIds = jdbcTemplate.queryForList(
                "SELECT id FROM \"" + tableName
                        + "\" WHERE email = ? ORDER BY id",
                Long.class,
                "rohith@example.com"
        );

        assertEquals(2, recordIds.size());

        Long survivorId = recordIds.get(0);

        MergeExecuteRequest request = new MergeExecuteRequest(
                recordIds,
                survivorId,
                Map.of("name", "Rohith"),
                "email",
                "rohith@example.com",
                "EXACT",
                null,
                null
        );

        var response = mergeExecutionService.executeMerge(
                datasetId,
                request
        );

        assertNotNull(response);

        Integer activeRecords = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM """ + "\"" + tableName + "\"" + """
                t
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM dataset_record_states s
                    WHERE s.dataset_id = ?
                    AND s.record_id = t.id
                )
                """,
                Integer.class,
                datasetId
        );

        assertEquals(2, activeRecords);

        Integer mergedStates = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dataset_record_states
                WHERE dataset_id = ?
                AND record_id = ?
                """,
                Integer.class,
                datasetId,
                recordIds.get(1)
        );

        assertEquals(1, mergedStates);

        String survivorName = jdbcTemplate.queryForObject(
                "SELECT name FROM \"" + tableName + "\" WHERE id = ?",
                String.class,
                survivorId
        );

        assertEquals("Rohith", survivorName);

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM merge_operations
                WHERE dataset_id = ?
                AND status = 'COMPLETED'
                """,
                Integer.class,
                datasetId
        );

        assertEquals(1, auditCount);
    }

    @Test
    void testInvalidMergeRejectsDifferentDuplicateGroups()
            throws Exception {

        Long datasetId = importTestDataset(
                "Invalid Merge Test",
                new String[][] {
                        {"Rohith", "rohith@example.com"},
                        {"Rahul", "rahul@example.com"}
                }
        );

        String tableName = getTableName(datasetId);

        List<Long> recordIds = jdbcTemplate.queryForList(
                "SELECT id FROM \"" + tableName + "\" ORDER BY id",
                Long.class
        );

        MergeExecuteRequest request = new MergeExecuteRequest(
                recordIds,
                recordIds.get(0),
                Map.of(),
                "email",
                "rohith@example.com",
                "EXACT",
                null,
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> mergeExecutionService.executeMerge(
                        datasetId,
                        request
                )
        );

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM merge_operations
                WHERE dataset_id = ?
                """,
                Integer.class,
                datasetId
        );

        assertEquals(0, auditCount);
    }

    private String getTableName(Long datasetId) {

        return jdbcTemplate.queryForObject(
                "SELECT table_name FROM datasets WHERE id = ?",
                String.class,
                datasetId
        );
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
                excelBytes
        );

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
                datasetName
        );
    }
    

@Test
void testMergeUndoRestoresOriginalRecords() throws Exception {

    Long datasetId = importTestDataset(
            "Merge Undo Test",
            new String[][] {
                    {"Rohith", "rohith@example.com"},
                    {"Rohith B", "rohith@example.com"},
                    {"Rahul", "rahul@example.com"}
            }
    );

    String tableName = getTableName(datasetId);

    List<Long> recordIds = jdbcTemplate.queryForList(
            "SELECT id FROM \"" + tableName
                    + "\" WHERE email = ? ORDER BY id",
            Long.class,
            "rohith@example.com"
    );

    Long survivorId = recordIds.get(0);

    MergeExecuteRequest request = new MergeExecuteRequest(
            recordIds,
            survivorId,
            Map.of("name", "Rohith B"),
            "email",
            "rohith@example.com",
            "EXACT",
            null,
            null
    );

    mergeExecutionService.executeMerge(datasetId, request);

    Long operationId = jdbcTemplate.queryForObject(
            """
            SELECT id FROM merge_operations
            WHERE dataset_id = ?
            ORDER BY id DESC LIMIT 1
            """,
            Long.class,
            datasetId
    );

    String mergedName = jdbcTemplate.queryForObject(
            "SELECT name FROM \"" + tableName + "\" WHERE id = ?",
            String.class,
            survivorId
    );

    assertEquals("Rohith B", mergedName);

    var undoResponse = mergeUndoService.undoMerge(
            datasetId,
            operationId
    );

    assertNotNull(undoResponse);
    assertEquals("REVERSED", undoResponse.status());

    String restoredName = jdbcTemplate.queryForObject(
            "SELECT name FROM \"" + tableName + "\" WHERE id = ?",
            String.class,
            survivorId
    );

    assertEquals("Rohith", restoredName);

    Integer remainingStates = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dataset_record_states
            WHERE dataset_id = ?
            """,
            Integer.class,
            datasetId
    );

    assertEquals(0, remainingStates);

    String operationStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM merge_operations WHERE id = ?",
            String.class,
            operationId
    );

    assertEquals("REVERSED", operationStatus);
}

@Test
void testOnlyLatestMergeCanBeUndone() throws Exception {

    Long datasetId = importTestDataset(
            "Latest Merge Undo Test",
            new String[][] {
                    {"Rohith", "rohith@example.com"},
                    {"Rohith B", "rohith@example.com"},
                    {"Rahul", "rahul@example.com"},
                    {"Rahul B", "rahul@example.com"}
            }
    );

    String tableName = getTableName(datasetId);

    List<Long> firstGroup = jdbcTemplate.queryForList(
            "SELECT id FROM \"" + tableName
                    + "\" WHERE email = ? ORDER BY id",
            Long.class,
            "rohith@example.com"
    );

    List<Long> secondGroup = jdbcTemplate.queryForList(
            "SELECT id FROM \"" + tableName
                    + "\" WHERE email = ? ORDER BY id",
            Long.class,
            "rahul@example.com"
    );

    mergeExecutionService.executeMerge(
            datasetId,
            new MergeExecuteRequest(
                    firstGroup,
                    firstGroup.get(0),
                    Map.of("name", "Rohith"),
                    "email",
                    "rohith@example.com",
                    "EXACT",
                    null,
                    null
            )
    );

    Long firstOperationId = jdbcTemplate.queryForObject(
            """
            SELECT id FROM merge_operations
            WHERE dataset_id = ?
            ORDER BY id DESC LIMIT 1
            """,
            Long.class,
            datasetId
    );

    mergeExecutionService.executeMerge(
            datasetId,
            new MergeExecuteRequest(
                    secondGroup,
                    secondGroup.get(0),
                    Map.of("name", "Rahul"),
                    "email",
                    "rahul@example.com",
                    "EXACT",
                    null,
                    null
            )
    );

    assertThrows(
            IllegalArgumentException.class,
            () -> mergeUndoService.undoMerge(
                    datasetId,
                    firstOperationId
            )
    );
}

}

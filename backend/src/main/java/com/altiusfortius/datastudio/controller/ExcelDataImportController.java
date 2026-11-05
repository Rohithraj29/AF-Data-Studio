package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.altiusfortius.datastudio.dto.ExcelImportRequest;
import com.altiusfortius.datastudio.dto.ExcelImportResponse;
import com.altiusfortius.datastudio.service.ExcelDataImportService;

import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/import")
public class ExcelDataImportController {

    private final ExcelDataImportService excelDataImportService;
    private final ObjectMapper objectMapper;

    public ExcelDataImportController(
            ExcelDataImportService excelDataImportService,
            ObjectMapper objectMapper) {

        this.excelDataImportService = excelDataImportService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = "/execute",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ExcelImportResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mapping") String mappingJson) {

        try {
            ExcelImportRequest request = objectMapper.readValue(
                    mappingJson,
                    ExcelImportRequest.class
            );

            ExcelImportResponse response =
                    excelDataImportService.importExcel(file, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid mapping JSON. Please check your request."
            );
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage())
        );
    }
}
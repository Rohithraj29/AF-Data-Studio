package com.altiusfortius.datastudio.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.altiusfortius.datastudio.dto.ExcelPreviewResponse;
import com.altiusfortius.datastudio.service.ExcelImportService;

@RestController
@RequestMapping("/api/import")
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    public ExcelImportController(
            ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    @PostMapping(
            value = "/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> previewExcel(
            @RequestParam("file") MultipartFile file) {

        try {
            ExcelPreviewResponse response =
                    excelImportService.previewExcel(file);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (IOException e) {

            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error",
                            "Unable to read the Excel file. Please check that it is a valid, unprotected workbook."
                    ));
        }
    }
}
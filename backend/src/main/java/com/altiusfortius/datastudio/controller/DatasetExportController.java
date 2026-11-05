package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.altiusfortius.datastudio.service.DatasetExportService;

@RestController
@RequestMapping("/api/datasets")
public class DatasetExportController {

    private final DatasetExportService exportService;

    public DatasetExportController(
            DatasetExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{id}/export/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable Long id) {

        StreamingResponseBody body =
                outputStream -> exportService.writeCsv(id, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "text/csv; charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("dataset-" + id + ".csv")
                                .build()
                                .toString()
                )
                .body(body);
    }

    @GetMapping("/{id}/export/excel")
    public ResponseEntity<StreamingResponseBody> exportExcel(
            @PathVariable Long id) {

        StreamingResponseBody body =
                outputStream -> exportService.writeExcel(id, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("dataset-" + id + ".xlsx")
                                .build()
                                .toString()
                )
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage()));
    }
}
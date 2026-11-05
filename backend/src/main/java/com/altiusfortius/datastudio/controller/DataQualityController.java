package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.DataQualityResponse;
import com.altiusfortius.datastudio.service.DataQualityService;

@RestController
@RequestMapping("/api/datasets")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping("/{id}/quality")
    public ResponseEntity<DataQualityResponse> analyzeQuality(
            @PathVariable Long id) {

        return ResponseEntity.ok(dataQualityService.analyze(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage()));
    }
}
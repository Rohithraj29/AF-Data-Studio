
package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.DuplicateAnalysisResponse;
import com.altiusfortius.datastudio.dto.NearDuplicateAnalysisResponse;
import com.altiusfortius.datastudio.service.DuplicateDetectionService;
import com.altiusfortius.datastudio.service.NearDuplicateDetectionService;

@RestController
@RequestMapping("/api/datasets")
public class DuplicateDetectionController {

    private final DuplicateDetectionService duplicateDetectionService;
    private final NearDuplicateDetectionService nearDuplicateDetectionService;

    public DuplicateDetectionController(
            DuplicateDetectionService duplicateDetectionService,
            NearDuplicateDetectionService nearDuplicateDetectionService) {

        this.duplicateDetectionService = duplicateDetectionService;
        this.nearDuplicateDetectionService = nearDuplicateDetectionService;
    }

    @GetMapping("/{id}/duplicates/exact")
    public ResponseEntity<DuplicateAnalysisResponse> analyzeExactDuplicates(
            @PathVariable Long id,
            @RequestParam String column) {

        return ResponseEntity.ok(
                duplicateDetectionService.analyzeExactDuplicates(id, column)
        );
    }

    @GetMapping("/{id}/duplicates/near")
    public ResponseEntity<NearDuplicateAnalysisResponse> analyzeNearDuplicates(
            @PathVariable Long id,
            @RequestParam String column,
            @RequestParam(defaultValue = "0.80") double threshold) {

        return ResponseEntity.ok(
                nearDuplicateDetectionService.analyzeNearDuplicates(
                        id, column, threshold)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage())
        );
    }
}

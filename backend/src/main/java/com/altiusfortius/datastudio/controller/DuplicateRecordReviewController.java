package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.DuplicateRecordsResponse;
import com.altiusfortius.datastudio.service.DuplicateRecordReviewService;

@RestController
@RequestMapping("/api/datasets")
public class DuplicateRecordReviewController {

    private final DuplicateRecordReviewService reviewService;

    public DuplicateRecordReviewController(
            DuplicateRecordReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{id}/duplicates/records")
    public ResponseEntity<DuplicateRecordsResponse> getDuplicateRecords(
            @PathVariable Long id,
            @RequestParam String column,
            @RequestParam String value) {

        return ResponseEntity.ok(
                reviewService.getDuplicateRecords(
                        id, column, value));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage()));
    }
}

package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.NearDuplicateRecordReviewResponse;
import com.altiusfortius.datastudio.service.NearDuplicateRecordReviewService;

@RestController
@RequestMapping("/api/datasets")
public class NearDuplicateRecordReviewController {

    private final NearDuplicateRecordReviewService reviewService;

    public NearDuplicateRecordReviewController(
            NearDuplicateRecordReviewService reviewService) {

        this.reviewService = reviewService;
    }

    @GetMapping("/{id}/duplicates/near/records")
    public ResponseEntity<NearDuplicateRecordReviewResponse> reviewRecords(
            @PathVariable Long id,
            @RequestParam String column,
            @RequestParam String value1,
            @RequestParam String value2,
            @RequestParam(defaultValue = "0.80") double threshold) {

        return ResponseEntity.ok(
                reviewService.reviewRecords(
                        id,
                        column,
                        value1,
                        value2,
                        threshold));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage()));
    }
}

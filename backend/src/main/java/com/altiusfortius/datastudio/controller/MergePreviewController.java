package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.MergePreviewRequest;
import com.altiusfortius.datastudio.dto.MergePreviewResponse;
import com.altiusfortius.datastudio.service.MergePreviewService;

@RestController
@RequestMapping("/api/datasets")
public class MergePreviewController {

    private final MergePreviewService mergePreviewService;

    public MergePreviewController(
            MergePreviewService mergePreviewService) {
        this.mergePreviewService = mergePreviewService;
    }

    @PostMapping("/{id}/merge/preview")
    public ResponseEntity<MergePreviewResponse> createPreview(
            @PathVariable Long id,
            @RequestBody MergePreviewRequest request) {

        return ResponseEntity.ok(
                mergePreviewService.createPreview(id, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage()));
    }
}
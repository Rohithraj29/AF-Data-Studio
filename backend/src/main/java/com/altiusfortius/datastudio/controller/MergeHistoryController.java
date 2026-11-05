package com.altiusfortius.datastudio.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.service.MergeHistoryService;
import com.altiusfortius.datastudio.service.MergeUndoService;

@RestController
@RequestMapping("/api/datasets/{datasetId}/merge")
public class MergeHistoryController {

    private final MergeHistoryService mergeHistoryService;
    private final MergeUndoService mergeUndoService;

    public MergeHistoryController(
            MergeHistoryService mergeHistoryService,
            MergeUndoService mergeUndoService) {

        this.mergeHistoryService = mergeHistoryService;
        this.mergeUndoService = mergeUndoService;
    }

    @GetMapping("/history")
    public ResponseEntity<?> getMergeHistory(
            @PathVariable Long datasetId) {

        try {
            return ResponseEntity.ok(
                    mergeHistoryService.getMergeHistory(datasetId));

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{operationId}/undo")
    public ResponseEntity<?> undoMerge(
            @PathVariable Long datasetId,
            @PathVariable Long operationId) {

        try {
            return ResponseEntity.ok(
                    mergeUndoService.undoMerge(datasetId, operationId));

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage()));
        }
    }
}
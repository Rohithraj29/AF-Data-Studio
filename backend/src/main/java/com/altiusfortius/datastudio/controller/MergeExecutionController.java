package com.altiusfortius.datastudio.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.altiusfortius.datastudio.dto.MergeExecuteRequest;
import com.altiusfortius.datastudio.dto.MergeExecuteResponse;
import com.altiusfortius.datastudio.service.MergeExecutionService;

@RestController
@RequestMapping("/api/datasets")
public class MergeExecutionController {

    private final MergeExecutionService mergeExecutionService;

    public MergeExecutionController(
            MergeExecutionService mergeExecutionService) {
        this.mergeExecutionService = mergeExecutionService;
    }

    @PostMapping("/{id}/merge/execute")
    public ResponseEntity<?> executeMerge(
            @PathVariable Long id,
            @RequestBody MergeExecuteRequest request) {

        try {
            MergeExecuteResponse response =
                    mergeExecutionService.executeMerge(id, request);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", exception.getMessage())
            );
        }
    }
}
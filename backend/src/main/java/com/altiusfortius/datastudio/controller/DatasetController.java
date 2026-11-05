package com.altiusfortius.datastudio.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.altiusfortius.datastudio.dto.CreateDatasetRequest;
import com.altiusfortius.datastudio.dto.CreateDatasetResponse;
import com.altiusfortius.datastudio.dto.DatasetDataResponse;
import com.altiusfortius.datastudio.dto.DatasetDetailsResponse;
import com.altiusfortius.datastudio.dto.DatasetListResponse;
import com.altiusfortius.datastudio.service.DatasetManagementService;
import com.altiusfortius.datastudio.service.DatasetService;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final DatasetManagementService managementService;

    public DatasetController(
            DatasetService datasetService,
            DatasetManagementService managementService) {

        this.datasetService = datasetService;
        this.managementService = managementService;
    }

    // Create an empty dynamic dataset table
    @PostMapping
    public ResponseEntity<?> createDataset(
            @RequestBody CreateDatasetRequest request) {

        try {
            CreateDatasetResponse response =
                    datasetService.createDataset(request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // List all datasets
    @GetMapping
    public ResponseEntity<List<DatasetListResponse>> getAllDatasets() {

        return ResponseEntity.ok(
                managementService.getAllDatasets());
    }

    // Get dataset details and column mappings
    @GetMapping("/{id}")
    public ResponseEntity<DatasetDetailsResponse> getDatasetDetails(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                managementService.getDatasetDetails(id));
    }

    // Get dataset records with pagination
    @GetMapping("/{id}/data")
    public ResponseEntity<DatasetDataResponse> getDatasetData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(
                managementService.getDatasetData(id, page, pageSize));
    }

    // Delete dataset and its dynamic table
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDataset(
            @PathVariable Long id) {

        managementService.deleteDataset(id);

        return ResponseEntity.ok(
                Map.of("message", "Dataset and its data table deleted successfully."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity.badRequest().body(
                Map.of("error", exception.getMessage()));
    }
}
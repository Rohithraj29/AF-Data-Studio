package com.altiusfortius.datastudio.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_name", nullable = false, length = 150)
    private String datasetName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "table_name", nullable = false, unique = true, length = 63)
    private String tableName;

    @Column(name = "sheet_name", length = 100)
    private String sheetName;

    @Column(name = "total_rows", nullable = false)
    private Long totalRows = 0L;

    @Column(name = "total_columns", nullable = false)
    private Integer totalColumns = 0;

    @Column(name = "import_status", nullable = false, length = 30)
    private String importStatus = "PENDING";

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    public Dataset() {
    }

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }

    public Long getTotalRows() { return totalRows; }
    public void setTotalRows(Long totalRows) { this.totalRows = totalRows; }

    public Integer getTotalColumns() { return totalColumns; }
    public void setTotalColumns(Integer totalColumns) { this.totalColumns = totalColumns; }

    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }

    public OffsetDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(OffsetDateTime importedAt) { this.importedAt = importedAt; }
}
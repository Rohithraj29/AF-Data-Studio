package com.altiusfortius.datastudio.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.altiusfortius.datastudio.entity.DatasetColumn;

@Repository
public interface DatasetColumnRepository
        extends JpaRepository<DatasetColumn, Long> {

    List<DatasetColumn> findByDatasetIdOrderByOrdinalPosition(Long datasetId);
}
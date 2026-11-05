package com.altiusfortius.datastudio.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.altiusfortius.datastudio.entity.Dataset;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByTableName(String tableName);

    boolean existsByTableName(String tableName);
}
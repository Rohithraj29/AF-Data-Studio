
CREATE TABLE IF NOT EXISTS datasets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_name VARCHAR(150) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(63) NOT NULL UNIQUE,
    sheet_name VARCHAR(100),
    total_rows BIGINT NOT NULL DEFAULT 0,
    total_columns INTEGER NOT NULL DEFAULT 0,
    import_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dataset_columns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    excel_column_name VARCHAR(255) NOT NULL,
    db_column_name VARCHAR(63) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    ordinal_position INTEGER NOT NULL,
    CONSTRAINT uq_dataset_column_name
        UNIQUE (dataset_id, db_column_name),
    CONSTRAINT uq_dataset_column_position
        UNIQUE (dataset_id, ordinal_position),
    CONSTRAINT fk_dataset_columns_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES datasets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merge_operations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_id BIGINT NOT NULL,
    survivor_record_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    field_selections JSONB NOT NULL,
    final_record JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_merge_operations_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT chk_merge_operation_status
        CHECK (status IN ('COMPLETED', 'REVERSED'))
);

CREATE TABLE IF NOT EXISTS merge_operation_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merge_operation_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    record_role VARCHAR(20) NOT NULL,
    original_record JSONB NOT NULL,
    CONSTRAINT fk_merge_records_operation
        FOREIGN KEY (merge_operation_id)
        REFERENCES merge_operations(id) ON DELETE CASCADE,
    CONSTRAINT chk_merge_record_role
        CHECK (record_role IN ('SURVIVOR', 'MERGED'))
);

CREATE TABLE IF NOT EXISTS dataset_record_states (
    dataset_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    record_status VARCHAR(20) NOT NULL DEFAULT 'MERGED',
    merged_into_record_id BIGINT NOT NULL,
    merge_operation_id BIGINT NOT NULL,
    PRIMARY KEY (dataset_id, record_id),
    CONSTRAINT fk_record_states_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES datasets(id) ON DELETE CASCADE,
    CONSTRAINT fk_record_states_operation
        FOREIGN KEY (merge_operation_id)
        REFERENCES merge_operations(id) ON DELETE CASCADE,
    CONSTRAINT chk_record_status
        CHECK (record_status = 'MERGED')
);

CREATE INDEX IF NOT EXISTS idx_merge_operations_dataset
    ON merge_operations(dataset_id);

CREATE INDEX IF NOT EXISTS idx_merge_records_operation
    ON merge_operation_records(merge_operation_id);

CREATE INDEX IF NOT EXISTS idx_record_states_operation
    ON dataset_record_states(merge_operation_id);

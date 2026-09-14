CREATE TABLE transactions (
                              id BIGINT NOT NULL,
                              amount DOUBLE NOT NULL,
                              transaction_type VARCHAR(255) NOT NULL,
                              parent_id BIGINT NULL,

                              PRIMARY KEY (id),

                              INDEX idx_transactions_type (
        transaction_type
    ),

                              INDEX idx_transactions_parent_id (
        parent_id
    )
)
    ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;
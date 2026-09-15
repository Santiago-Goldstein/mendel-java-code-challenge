CREATE TABLE transaction_graph_lock (
                                        id BIGINT NOT NULL,

                                        PRIMARY KEY (id)
)
    ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


INSERT INTO transaction_graph_lock (id)
VALUES (1);
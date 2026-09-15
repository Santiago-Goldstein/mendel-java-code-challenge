package com.mendel.transactions.integration;

import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainerConfiguration.class)
class FlywaySchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyExpectedFlywayMigrations() {

        List<String> versions =
                jdbcTemplate.queryForList(
                        """
                        SELECT version
                        FROM flyway_schema_history
                        WHERE success = 1
                        ORDER BY installed_rank
                        """,
                        String.class
                );

        assertThat(versions)
                .containsExactly(
                        "1",
                        "2"
                );
    }

    @Test
    void shouldCreateExpectedTransactionColumns() {

        List<String> columns =
                jdbcTemplate.queryForList(
                        """
                        SELECT COLUMN_NAME
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'transactions'
                        ORDER BY ORDINAL_POSITION
                        """,
                        String.class
                );

        assertThat(columns)
                .containsExactly(
                        "id",
                        "amount",
                        "transaction_type",
                        "parent_id"
                );
    }

    @Test
    void shouldCreateExpectedTransactionIndexes() {

        List<String> indexes =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT INDEX_NAME
                        FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'transactions'
                        """,
                        String.class
                );

        assertThat(indexes)
                .contains(
                        "PRIMARY",
                        "idx_transactions_type",
                        "idx_transactions_parent_id"
                );
    }

    @Test
    void shouldNotCreateForeignKeyForParentId() {

        Long foreignKeys =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.KEY_COLUMN_USAGE
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'transactions'
                          AND COLUMN_NAME = 'parent_id'
                          AND REFERENCED_TABLE_NAME IS NOT NULL
                        """,
                        Long.class
                );

        assertThat(
                foreignKeys
        ).isZero();
    }

    @Test
    void shouldCreateGlobalGraphLockRow() {

        Long lockRows =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM transaction_graph_lock
                        WHERE id = 1
                        """,
                        Long.class
                );

        assertThat(
                lockRows
        ).isEqualTo(1L);
    }

    @Test
    void shouldUseInnoDbForTransactionalTables() {

        List<String> engines =
                jdbcTemplate.queryForList(
                        """
                        SELECT ENGINE
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME IN (
                              'transactions',
                              'transaction_graph_lock'
                          )
                        """,
                        String.class
                );

        assertThat(engines)
                .hasSize(2)
                .allSatisfy(
                        engine ->
                                assertThat(engine)
                                        .isEqualToIgnoringCase(
                                                "InnoDB"
                                        )
                );
    }
}
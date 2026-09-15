package com.mendel.transactions.integration;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.repository.TransactionRepository;
import com.mendel.transactions.service.TransactionService;
import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MySqlTestContainerConfiguration.class)
class TransactionAtomicityIntegrationTest {

    @Autowired
    private TransactionService service;

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {

        jdbcTemplate.update(
                "DELETE FROM transactions"
        );
    }

    @Test
    void shouldRollbackEntireBatchWhenPersistenceFails() {

        Transaction valid =
                new Transaction(
                        6001L,
                        1000.0,
                        "valid",
                        null
                );

        /*
         * transaction_type is VARCHAR(255).
         *
         * 300 characters deliberately violate
         * the real MySQL column constraint.
         */
        String invalidType =
                "x".repeat(300);

        Transaction invalid =
                new Transaction(
                        6002L,
                        2000.0,
                        invalidType,
                        null
                );

        assertThatThrownBy(
                () ->
                        service.saveTransactions(
                                List.of(
                                        valid,
                                        invalid
                                )
                        )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );

        /*
         * No partial insert.
         */
        assertThat(
                repository.findAll()
        ).isEmpty();
    }

    @Test
    void shouldRollbackReplacementAndNewRowsWhenBatchFails() {

        /*
         * Existing committed state.
         */
        service.saveTransaction(
                6101L,
                1000.0,
                "original",
                null
        );

        Transaction replacement =
                new Transaction(
                        6101L,
                        9999.0,
                        "replacement",
                        null
                );

        Transaction validNewTransaction =
                new Transaction(
                        6102L,
                        2000.0,
                        "new-valid",
                        null
                );

        String invalidType =
                "x".repeat(300);

        Transaction invalid =
                new Transaction(
                        6103L,
                        3000.0,
                        invalidType,
                        null
                );

        assertThatThrownBy(
                () ->
                        service.saveTransactions(
                                List.of(
                                        replacement,
                                        validNewTransaction,
                                        invalid
                                )
                        )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );

        /*
         * Existing transaction must retain
         * the ORIGINAL values.
         */
        Transaction persisted =
                repository
                        .findById(6101L)
                        .orElseThrow();

        assertThat(
                persisted.getAmount()
        ).isEqualTo(1000.0);

        assertThat(
                persisted.getType()
        ).isEqualTo("original");

        /*
         * New rows from the failed batch must
         * not exist either.
         */
        assertThat(
                repository.findById(6102L)
        ).isEmpty();

        assertThat(
                repository.findById(6103L)
        ).isEmpty();

        /*
         * Database should still contain exactly
         * the original committed transaction.
         */
        assertThat(
                repository.findAll()
        ).hasSize(1);
    }
}
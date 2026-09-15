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
         * This deliberately exceeds that database
         * constraint and forces MySQL to reject
         * the batch during flush.
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
         * Even though the first row was valid,
         * the transaction must have rolled back
         * the entire batch.
         */
        assertThat(
                repository.findAll()
        ).isEmpty();
    }
}
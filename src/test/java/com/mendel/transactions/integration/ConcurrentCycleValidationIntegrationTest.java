package com.mendel.transactions.integration;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.CyclicTransactionException;
import com.mendel.transactions.repository.TransactionRepository;
import com.mendel.transactions.service.TransactionService;
import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainerConfiguration.class)
class ConcurrentCycleValidationIntegrationTest {

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
    void shouldPreventCycleFromConcurrentWrites()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try {
            Future<WriteResult> first =
                    executor.submit(
                            () ->
                                    attemptSave(
                                            start,
                                            5001L,
                                            5002L
                                    )
                    );

            Future<WriteResult> second =
                    executor.submit(
                            () ->
                                    attemptSave(
                                            start,
                                            5002L,
                                            5001L
                                    )
                    );

            /*
             * Release both threads at essentially
             * the same time.
             */
            start.countDown();

            WriteResult firstResult =
                    first.get(
                            10,
                            TimeUnit.SECONDS
                    );

            WriteResult secondResult =
                    second.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<WriteResult> results =
                    List.of(
                            firstResult,
                            secondResult
                    );

            long successfulWrites =
                    results.stream()
                            .filter(
                                    WriteResult::success
                            )
                            .count();

            long rejectedWrites =
                    results.stream()
                            .filter(
                                    result ->
                                            !result.success()
                            )
                            .count();

            assertThat(successfulWrites)
                    .isEqualTo(1);

            assertThat(rejectedWrites)
                    .isEqualTo(1);

            WriteResult rejected =
                    results.stream()
                            .filter(
                                    result ->
                                            !result.success()
                            )
                            .findFirst()
                            .orElseThrow();

            assertThat(
                    rejected.error()
            ).isInstanceOf(
                    CyclicTransactionException.class
            );

            /*
             * Only the winner should have been persisted.
             */
            List<Transaction> stored =
                    repository.findAll();

            assertThat(stored)
                    .hasSize(1);

            Transaction onlyTransaction =
                    stored.get(0);

            assertThat(
                    onlyTransaction.getParentId()
            ).isNotNull();

            /*
             * Its parent does not exist because the
             * conflicting second write was rejected.
             *
             * Therefore there cannot be a persisted cycle.
             */
            assertThat(
                    repository.findById(
                            onlyTransaction.getParentId()
                    )
            ).isEmpty();

        } finally {
            executor.shutdownNow();
        }
    }

    private WriteResult attemptSave(
            CountDownLatch start,
            long id,
            long parentId
    ) throws InterruptedException {

        start.await();

        try {
            service.saveTransaction(
                    id,
                    1000.0,
                    "concurrent",
                    parentId
            );

            return new WriteResult(
                    true,
                    null
            );

        } catch (Throwable error) {

            return new WriteResult(
                    false,
                    error
            );
        }
    }

    private record WriteResult(
            boolean success,
            Throwable error
    ) {
    }
}
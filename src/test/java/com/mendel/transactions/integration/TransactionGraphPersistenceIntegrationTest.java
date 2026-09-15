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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainerConfiguration.class)
class TransactionGraphPersistenceIntegrationTest {

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
    void shouldAllowChildToBePersistedBeforeParentExists() {

        /*
         * Parent 7001 does not exist yet.
         */
        service.saveTransaction(
                7002L,
                2000.0,
                "child",
                7001L
        );

        Transaction child =
                repository
                        .findById(7002L)
                        .orElseThrow();

        assertThat(
                child.getParentId()
        ).isEqualTo(7001L);

        assertThat(
                repository.findById(7001L)
        ).isEmpty();

        /*
         * Parent is created afterwards.
         */
        service.saveTransaction(
                7001L,
                1000.0,
                "parent",
                null
        );

        /*
         * The graph must now become:
         *
         * 7001
         *   |
         * 7002
         *
         * 1000 + 2000 = 3000
         */
        assertThat(
                service.calculateSum(7001L)
        ).isEqualTo(3000.0);
    }

    @Test
    void shouldCalculateSumAcrossPersistedBranches() {

        service.saveTransactions(
                List.of(
                        new Transaction(
                                7101L,
                                1000.0,
                                "root",
                                null
                        ),
                        new Transaction(
                                7102L,
                                2000.0,
                                "branch",
                                7101L
                        ),
                        new Transaction(
                                7103L,
                                3000.0,
                                "branch",
                                7101L
                        ),
                        new Transaction(
                                7104L,
                                4000.0,
                                "leaf",
                                7102L
                        )
                )
        );

        /*
         *        7101 = 1000
         *        /   \
         *     7102   7103
         *     2000   3000
         *       |
         *     7104
         *     4000
         *
         * Total = 10000
         */
        assertThat(
                service.calculateSum(7101L)
        ).isEqualTo(10000.0);

        assertThat(
                service.calculateSum(7102L)
        ).isEqualTo(6000.0);

        assertThat(
                service.calculateSum(7103L)
        ).isEqualTo(3000.0);
    }

    @Test
    void shouldCalculateDeepPersistedChain() {

        List<Transaction> transactions =
                new ArrayList<>();

        long rootId = 7201L;

        int chainLength = 100;

        for (
                int index = 0;
                index < chainLength;
                index++
        ) {

            long id =
                    rootId + index;

            Long parentId =
                    index == 0
                            ? null
                            : id - 1;

            transactions.add(
                    new Transaction(
                            id,
                            1.0,
                            "deep-chain",
                            parentId
                    )
            );
        }

        service.saveTransactions(
                transactions
        );

        /*
         * 100 transactions of amount 1.
         */
        assertThat(
                service.calculateSum(rootId)
        ).isEqualTo(100.0);

        Long persistedRows =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM transactions
                        WHERE transaction_type = 'deep-chain'
                        """,
                        Long.class
                );

        assertThat(
                persistedRows
        ).isEqualTo(100L);
    }

    @Test
    void shouldCommitTransactionToMysqlAfterServiceCallCompletes() {

        service.saveTransaction(
                7301L,
                1234.56,
                "persisted",
                null
        );

        /*
         * Read directly through JDBC rather than
         * through JPA.
         *
         * This proves that the service transaction
         * was committed to MySQL.
         */
        Double amount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT amount
                        FROM transactions
                        WHERE id = 7301
                        """,
                        Double.class
                );

        String type =
                jdbcTemplate.queryForObject(
                        """
                        SELECT transaction_type
                        FROM transactions
                        WHERE id = 7301
                        """,
                        String.class
                );

        assertThat(amount)
                .isEqualTo(1234.56);

        assertThat(type)
                .isEqualTo("persisted");
    }
}
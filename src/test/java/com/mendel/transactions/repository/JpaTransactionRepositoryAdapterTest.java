package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(
        MySqlTestContainerConfiguration.class
)
class JpaTransactionRepositoryAdapterTest {

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
    void shouldSaveAndFindTransactionById() {

        Transaction transaction =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        null
                );

        repository.save(transaction);

        assertThat(
                repository.findById(10L)
        )
                .isPresent()
                .get()
                .satisfies(
                        stored -> {

                            assertThat(
                                    stored.getAmount()
                            )
                                    .isEqualTo(
                                            5000.0
                                    );

                            assertThat(
                                    stored.getType()
                            )
                                    .isEqualTo(
                                            "cars"
                                    );

                            assertThat(
                                    stored.getParentId()
                            )
                                    .isNull();
                        }
                );
    }

    @Test
    void shouldReplaceTransactionWithSameId() {

        repository.save(
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        null
                )
        );

        repository.save(
                new Transaction(
                        10L,
                        7500.0,
                        "shopping",
                        99L
                )
        );

        Transaction stored =
                repository
                        .findById(10L)
                        .orElseThrow();

        assertThat(
                stored.getAmount()
        )
                .isEqualTo(7500.0);

        assertThat(
                stored.getType()
        )
                .isEqualTo("shopping");

        assertThat(
                stored.getParentId()
        )
                .isEqualTo(99L);

        assertThat(
                repository.findAll()
        )
                .hasSize(1);
    }

    @Test
    void shouldUpdateTypeQueriesAfterReplacement() {

        repository.save(
                new Transaction(
                        20L,
                        5000.0,
                        "cars",
                        null
                )
        );

        assertThat(
                repository.findByType(
                        "cars"
                )
        )
                .extracting(
                        Transaction::getId
                )
                .containsExactly(20L);

        repository.save(
                new Transaction(
                        20L,
                        7500.0,
                        "shopping",
                        null
                )
        );

        assertThat(
                repository.findByType(
                        "cars"
                )
        ).isEmpty();

        assertThat(
                repository.findByType(
                        "shopping"
                )
        )
                .extracting(
                        Transaction::getId
                )
                .containsExactly(20L);
    }

    @Test
    void shouldAllowParentReferenceBeforeParentExists() {

        repository.save(
                new Transaction(
                        31L,
                        2000.0,
                        "child",
                        30L
                )
        );

        Transaction child =
                repository
                        .findById(31L)
                        .orElseThrow();

        assertThat(
                child.getParentId()
        ).isEqualTo(30L);

        assertThat(
                repository.findById(30L)
        ).isEmpty();

        repository.save(
                new Transaction(
                        30L,
                        1000.0,
                        "parent",
                        null
                )
        );

        assertThat(
                repository.findById(30L)
        ).isPresent();

        assertThat(
                repository
                        .findById(31L)
                        .orElseThrow()
                        .getParentId()
        ).isEqualTo(30L);
    }

    @Test
    void shouldPersistDecimalAmounts() {

        repository.save(
                new Transaction(
                        40L,
                        1234.56,
                        "decimal",
                        null
                )
        );

        Transaction stored =
                repository
                        .findById(40L)
                        .orElseThrow();

        assertThat(
                stored.getAmount()
        ).isEqualTo(1234.56);
    }

    @Test
    void shouldFindTransactionsByType() {

        repository.saveAll(
                List.of(
                        new Transaction(
                                10L,
                                5000.0,
                                "cars",
                                null
                        ),
                        new Transaction(
                                11L,
                                10000.0,
                                "shopping",
                                10L
                        ),
                        new Transaction(
                                12L,
                                5000.0,
                                "shopping",
                                11L
                        )
                )
        );

        assertThat(
                repository.findByType(
                        "shopping"
                )
        )
                .extracting(
                        Transaction::getId
                )
                .containsExactlyInAnyOrder(
                        11L,
                        12L
                );
    }

    @Test
    void shouldReturnAllPersistedTransactions() {

        repository.saveAll(
                List.of(
                        new Transaction(
                                10L,
                                5000.0,
                                "cars",
                                null
                        ),
                        new Transaction(
                                11L,
                                10000.0,
                                "shopping",
                                10L
                        )
                )
        );

        assertThat(
                repository.findAll()
        )
                .extracting(
                        Transaction::getId
                )
                .containsExactlyInAnyOrder(
                        10L,
                        11L
                );
    }

    @Test
    void shouldReturnEmptyRepositoryWhenNoTransactionsExist() {

        assertThat(
                repository.findAll()
        ).isEmpty();

        assertThat(
                repository.findById(999L)
        ).isEmpty();

        assertThat(
                repository.findByType(
                        "unknown"
                )
        ).isEmpty();
    }
}
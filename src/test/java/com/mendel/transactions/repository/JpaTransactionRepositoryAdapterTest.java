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
                        null
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
                repository.findAll()
        )
                .hasSize(1);
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
}
package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTransactionRepositoryTest {

    private InMemoryTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
    }

    @Test
    void shouldSaveAndFindTransactionById() {
        Transaction transaction =
                new Transaction(10L, 5000.0, "cars", null);

        repository.save(transaction);

        assertThat(repository.findById(10L))
                .hasValueSatisfying(storedTransaction -> {
                    assertThat(storedTransaction.getId()).isEqualTo(10L);
                    assertThat(storedTransaction.getAmount()).isEqualTo(5000.0);
                    assertThat(storedTransaction.getType()).isEqualTo("cars");
                    assertThat(storedTransaction.getParentId()).isNull();
                });
    }

    @Test
    void shouldReturnEmptyWhenTransactionDoesNotExist() {
        assertThat(repository.findById(999L))
                .isEmpty();
    }

    @Test
    void shouldFindTransactionsByType() {
        repository.save(
                new Transaction(10L, 5000.0, "cars", null)
        );

        repository.save(
                new Transaction(11L, 10000.0, "shopping", 10L)
        );

        repository.save(
                new Transaction(12L, 5000.0, "shopping", 11L)
        );

        List<Transaction> transactions =
                repository.findByType("shopping");

        assertThat(transactions)
                .extracting(Transaction::getId)
                .containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void shouldReturnEmptyListWhenTypeDoesNotExist() {
        repository.save(
                new Transaction(10L, 5000.0, "cars", null)
        );

        assertThat(repository.findByType("shopping"))
                .isEmpty();
    }

    @Test
    void shouldReplaceTransactionWhenSavingExistingId() {
        repository.save(
                new Transaction(10L, 5000.0, "cars", null)
        );

        repository.save(
                new Transaction(10L, 7500.0, "shopping", null)
        );

        assertThat(repository.findById(10L))
                .hasValueSatisfying(transaction -> {
                    assertThat(transaction.getAmount()).isEqualTo(7500.0);
                    assertThat(transaction.getType()).isEqualTo("shopping");
                });

        assertThat(repository.findAll())
                .hasSize(1);
    }

    @Test
    void shouldReturnAllTransactions() {
        repository.save(
                new Transaction(10L, 5000.0, "cars", null)
        );

        repository.save(
                new Transaction(11L, 10000.0, "shopping", 10L)
        );

        assertThat(repository.findAll())
                .extracting(Transaction::getId)
                .containsExactlyInAnyOrder(10L, 11L);
    }
}
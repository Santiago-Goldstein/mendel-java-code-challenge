package com.mendel.transactions.service;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionRepository repository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        service = new TransactionService(repository);
    }

    @Test
    void shouldSaveTransaction() {
        service.saveTransaction(
                10L,
                5000.0,
                "cars",
                null
        );

        ArgumentCaptor<Transaction> captor =
                ArgumentCaptor.forClass(Transaction.class);

        verify(repository).save(captor.capture());

        Transaction savedTransaction = captor.getValue();

        assertThat(savedTransaction.getId()).isEqualTo(10L);
        assertThat(savedTransaction.getAmount()).isEqualTo(5000.0);
        assertThat(savedTransaction.getType()).isEqualTo("cars");
        assertThat(savedTransaction.getParentId()).isNull();
    }

    @Test
    void shouldReturnTransactionIdsByType() {
        when(repository.findByType("shopping"))
                .thenReturn(List.of(
                        new Transaction(12L, 5000.0, "shopping", 11L),
                        new Transaction(11L, 10000.0, "shopping", 10L)
                ));

        List<Long> ids =
                service.findTransactionIdsByType("shopping");

        assertThat(ids)
                .containsExactly(11L, 12L);
    }

    @Test
    void shouldReturnEmptyListWhenTypeDoesNotExist() {
        when(repository.findByType("unknown"))
                .thenReturn(List.of());

        assertThat(
                service.findTransactionIdsByType("unknown")
        ).isEmpty();
    }

    @Test
    void shouldCalculateTransitiveTransactionSum() {
        Transaction transaction10 =
                new Transaction(10L, 5000.0, "cars", null);

        Transaction transaction11 =
                new Transaction(11L, 10000.0, "shopping", 10L);

        Transaction transaction12 =
                new Transaction(12L, 5000.0, "shopping", 11L);

        Transaction unrelatedTransaction =
                new Transaction(20L, 3000.0, "food", null);

        when(repository.findById(10L))
                .thenReturn(Optional.of(transaction10));

        when(repository.findAll())
                .thenReturn(List.of(
                        transaction10,
                        transaction11,
                        transaction12,
                        unrelatedTransaction
                ));

        double sum = service.calculateSum(10L);

        assertThat(sum).isEqualTo(20000.0);
    }

    @Test
    void shouldCalculateSumStartingFromIntermediateTransaction() {
        Transaction transaction10 =
                new Transaction(10L, 5000.0, "cars", null);

        Transaction transaction11 =
                new Transaction(11L, 10000.0, "shopping", 10L);

        Transaction transaction12 =
                new Transaction(12L, 5000.0, "shopping", 11L);

        when(repository.findById(11L))
                .thenReturn(Optional.of(transaction11));

        when(repository.findAll())
                .thenReturn(List.of(
                        transaction10,
                        transaction11,
                        transaction12
                ));

        double sum = service.calculateSum(11L);

        assertThat(sum).isEqualTo(15000.0);
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {
        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateSum(999L))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("999");

        verify(repository, never()).findAll();
    }

    @Test
    void shouldHandleCyclesWithoutCountingTransactionsTwice() {
        Transaction transaction10 =
                new Transaction(10L, 5000.0, "cars", 12L);

        Transaction transaction11 =
                new Transaction(11L, 10000.0, "shopping", 10L);

        Transaction transaction12 =
                new Transaction(12L, 5000.0, "shopping", 11L);

        when(repository.findById(10L))
                .thenReturn(Optional.of(transaction10));

        when(repository.findAll())
                .thenReturn(List.of(
                        transaction10,
                        transaction11,
                        transaction12
                ));

        double sum = service.calculateSum(10L);

        assertThat(sum).isEqualTo(20000.0);
    }
}
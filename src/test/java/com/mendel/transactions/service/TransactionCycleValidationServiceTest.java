package com.mendel.transactions.service;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.CyclicTransactionException;
import com.mendel.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransactionCycleValidationServiceTest {

    private TransactionRepository repository;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(TransactionRepository.class);

        service =
                new TransactionService(repository);
    }

    @Test
    void shouldRejectDirectSelfCycle() {

        when(repository.findAll())
                .thenReturn(List.of());

        assertThatThrownBy(
                () -> service.saveTransaction(
                        10L,
                        5000.0,
                        "cars",
                        10L
                )
        )
                .isInstanceOf(
                        CyclicTransactionException.class
                );

        verify(
                repository,
                never()
        ).save(any(Transaction.class));
    }

    @Test
    void shouldRejectIndirectCycleWhenUpdatingTransaction() {

        Transaction transaction10 =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        null
                );

        Transaction transaction11 =
                new Transaction(
                        11L,
                        10000.0,
                        "shopping",
                        10L
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                transaction10,
                                transaction11
                        )
                );

        assertThatThrownBy(
                () -> service.saveTransaction(
                        10L,
                        5000.0,
                        "cars",
                        11L
                )
        )
                .isInstanceOf(
                        CyclicTransactionException.class
                );

        verify(
                repository,
                never()
        ).save(any(Transaction.class));
    }

    @Test
    void shouldAllowReferenceToParentThatDoesNotExistYet() {

        when(repository.findAll())
                .thenReturn(List.of());

        service.saveTransaction(
                10L,
                5000.0,
                "cars",
                999L
        );

        verify(repository).save(
                argThat(
                        transaction ->
                                transaction.getId() == 10L
                                        && transaction
                                        .getParentId()
                                        .equals(999L)
                )
        );
    }

    @Test
    void shouldDetectCycleWhenMissingParentIsCreatedLater() {

        Transaction transaction10 =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        11L
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(transaction10)
                );

        assertThatThrownBy(
                () -> service.saveTransaction(
                        11L,
                        10000.0,
                        "shopping",
                        10L
                )
        )
                .isInstanceOf(
                        CyclicTransactionException.class
                );

        verify(
                repository,
                never()
        ).save(any(Transaction.class));
    }

    @Test
    void shouldRejectCycleInsideCsvBatchBeforeSavingAnything() {

        Transaction transaction10 =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        11L
                );

        Transaction transaction11 =
                new Transaction(
                        11L,
                        10000.0,
                        "shopping",
                        10L
                );

        when(repository.findAll())
                .thenReturn(List.of());

        assertThatThrownBy(
                () -> service.saveTransactions(
                        List.of(
                                transaction10,
                                transaction11
                        )
                )
        )
                .isInstanceOf(
                        CyclicTransactionException.class
                );

        verify(
                repository,
                never()
        ).save(any(Transaction.class));
    }

    @Test
    void shouldAllowValidTransactionChain() {

        Transaction transaction10 =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        null
                );

        Transaction transaction11 =
                new Transaction(
                        11L,
                        10000.0,
                        "shopping",
                        10L
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                transaction10,
                                transaction11
                        )
                );

        service.saveTransaction(
                12L,
                5000.0,
                "shopping",
                11L
        );

        verify(repository).save(
                argThat(
                        transaction ->
                                transaction.getId() == 12L
                                        && transaction
                                        .getParentId()
                                        .equals(11L)
                )
        );
    }
}
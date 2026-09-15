package com.mendel.transactions.service;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.lock.TransactionGraphLockManager;
import com.mendel.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class TransactionServiceTest {

    private TransactionRepository repository;
    private TransactionGraphLockManager graphLockManager;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(TransactionRepository.class);

        graphLockManager =
                mock(TransactionGraphLockManager.class);

        service =
                new TransactionService(
                        repository,
                        graphLockManager
                );
    }

    @Test
    void shouldSaveTransaction() {

        when(repository.findAll())
                .thenReturn(List.of());

        service.saveTransaction(
                10L,
                5000.0,
                "cars",
                null
        );

        verify(graphLockManager)
                .acquireWriteLock();

        ArgumentCaptor<Transaction> captor =
                ArgumentCaptor.forClass(
                        Transaction.class
                );

        verify(repository)
                .save(captor.capture());

        Transaction savedTransaction =
                captor.getValue();

        assertThat(
                savedTransaction.getId()
        ).isEqualTo(10L);

        assertThat(
                savedTransaction.getAmount()
        ).isEqualTo(5000.0);

        assertThat(
                savedTransaction.getType()
        ).isEqualTo("cars");

        assertThat(
                savedTransaction.getParentId()
        ).isNull();
    }

    @Test
    void shouldReturnTransactionIdsByType() {

        when(
                repository.findByType("shopping")
        ).thenReturn(
                List.of(
                        new Transaction(
                                12L,
                                5000.0,
                                "shopping",
                                11L
                        ),
                        new Transaction(
                                11L,
                                10000.0,
                                "shopping",
                                10L
                        )
                )
        );

        List<Long> ids =
                service.findTransactionIdsByType(
                        "shopping"
                );

        assertThat(ids)
                .containsExactly(
                        11L,
                        12L
                );

        verify(
                graphLockManager,
                never()
        ).acquireWriteLock();
    }

    @Test
    void shouldReturnEmptyListWhenTypeDoesNotExist() {

        when(
                repository.findByType("unknown")
        ).thenReturn(List.of());

        assertThat(
                service.findTransactionIdsByType(
                        "unknown"
                )
        ).isEmpty();
    }

    @Test
    void shouldCalculateTransitiveTransactionSum() {

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

        Transaction transaction12 =
                new Transaction(
                        12L,
                        5000.0,
                        "shopping",
                        11L
                );

        Transaction unrelated =
                new Transaction(
                        20L,
                        3000.0,
                        "food",
                        null
                );

        when(repository.findById(10L))
                .thenReturn(
                        Optional.of(
                                transaction10
                        )
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                transaction10,
                                transaction11,
                                transaction12,
                                unrelated
                        )
                );

        assertThat(
                service.calculateSum(10L)
        ).isEqualTo(20000.0);
    }

    @Test
    void shouldCalculateSumStartingFromIntermediateTransaction() {

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

        Transaction transaction12 =
                new Transaction(
                        12L,
                        5000.0,
                        "shopping",
                        11L
                );

        when(repository.findById(11L))
                .thenReturn(
                        Optional.of(
                                transaction11
                        )
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                transaction10,
                                transaction11,
                                transaction12
                        )
                );

        assertThat(
                service.calculateSum(11L)
        ).isEqualTo(15000.0);
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {

        when(repository.findById(999L))
                .thenReturn(
                        Optional.empty()
                );

        assertThatThrownBy(
                () ->
                        service.calculateSum(
                                999L
                        )
        )
                .isInstanceOf(
                        TransactionNotFoundException.class
                )
                .hasMessageContaining(
                        "999"
                );

        verify(
                repository,
                never()
        ).findAll();
    }

    @Test
    void shouldHandleCyclesWithoutCountingTransactionsTwice() {

        /*
         * Defensive read-side protection.
         * Write operations should prevent this state,
         * but calculateSum remains robust against
         * corrupted/legacy data.
         */

        Transaction transaction10 =
                new Transaction(
                        10L,
                        5000.0,
                        "cars",
                        12L
                );

        Transaction transaction11 =
                new Transaction(
                        11L,
                        10000.0,
                        "shopping",
                        10L
                );

        Transaction transaction12 =
                new Transaction(
                        12L,
                        5000.0,
                        "shopping",
                        11L
                );

        when(repository.findById(10L))
                .thenReturn(
                        Optional.of(
                                transaction10
                        )
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                transaction10,
                                transaction11,
                                transaction12
                        )
                );

        assertThat(
                service.calculateSum(10L)
        ).isEqualTo(20000.0);
    }

    @Test
    void shouldSaveMultipleTransactions() {

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

        List<Transaction> transactions =
                List.of(
                        transaction10,
                        transaction11
                );

        when(repository.findAll())
                .thenReturn(List.of());

        int saved =
                service.saveTransactions(
                        transactions
                );

        assertThat(saved)
                .isEqualTo(2);

        verify(graphLockManager)
                .acquireWriteLock();

        verify(repository)
                .saveAll(transactions);

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void shouldAcquireWriteLockBeforeReadingGraphAndSavingTransaction() {

        when(repository.findAll())
                .thenReturn(
                        List.of()
                );

        service.saveTransaction(
                30L,
                1000.0,
                "ordered-write",
                null
        );

        InOrder order =
                inOrder(
                        graphLockManager,
                        repository
                );

        order.verify(
                graphLockManager
        ).acquireWriteLock();

        order.verify(
                repository
        ).findAll();

        order.verify(
                repository
        ).save(
                any(Transaction.class)
        );
    }

    @Test
    void shouldAcquireWriteLockBeforeReadingGraphAndSavingBatch() {

        Transaction first =
                new Transaction(
                        40L,
                        1000.0,
                        "batch",
                        null
                );

        Transaction second =
                new Transaction(
                        41L,
                        2000.0,
                        "batch",
                        40L
                );

        List<Transaction> batch =
                List.of(
                        first,
                        second
                );

        when(repository.findAll())
                .thenReturn(
                        List.of()
                );

        service.saveTransactions(
                batch
        );

        InOrder order =
                inOrder(
                        graphLockManager,
                        repository
                );

        order.verify(
                graphLockManager
        ).acquireWriteLock();

        order.verify(
                repository
        ).findAll();

        order.verify(
                repository
        ).saveAll(
                batch
        );
    }

    @Test
    void shouldNotAcquireWriteLockWhenCalculatingSum() {

        Transaction transaction =
                new Transaction(
                        50L,
                        1000.0,
                        "read",
                        null
                );

        when(
                repository.findById(50L)
        ).thenReturn(
                Optional.of(transaction)
        );

        when(
                repository.findAll()
        ).thenReturn(
                List.of(transaction)
        );

        assertThat(
                service.calculateSum(50L)
        ).isEqualTo(1000.0);

        verify(
                graphLockManager,
                never()
        ).acquireWriteLock();
    }



    @Test
    void shouldCalculateSumAcrossMultipleBranches() {

        Transaction root =
                new Transaction(
                        10L,
                        1000.0,
                        "root",
                        null
                );

        Transaction child1 =
                new Transaction(
                        11L,
                        2000.0,
                        "child",
                        10L
                );

        Transaction child2 =
                new Transaction(
                        12L,
                        3000.0,
                        "child",
                        10L
                );

        Transaction grandchild =
                new Transaction(
                        13L,
                        4000.0,
                        "child",
                        11L
                );

        when(repository.findById(10L))
                .thenReturn(
                        Optional.of(root)
                );

        when(repository.findAll())
                .thenReturn(
                        List.of(
                                root,
                                child1,
                                child2,
                                grandchild
                        )
                );

        assertThat(
                service.calculateSum(10L)
        ).isEqualTo(10000.0);
    }
}
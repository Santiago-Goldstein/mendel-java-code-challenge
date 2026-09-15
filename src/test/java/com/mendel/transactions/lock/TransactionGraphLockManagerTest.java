package com.mendel.transactions.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionGraphLockManagerTest {

    private TransactionGraphLockRepository repository;

    private TransactionGraphLockManager manager;

    @BeforeEach
    void setUp() {

        repository =
                mock(
                        TransactionGraphLockRepository.class
                );

        manager =
                new TransactionGraphLockManager(
                        repository
                );
    }

    @Test
    void shouldAcquireReservedGlobalGraphLock() {

        TransactionGraphLock lock =
                new TransactionGraphLock(
                        1L
                );

        when(
                repository.findByIdForUpdate(
                        1L
                )
        ).thenReturn(
                Optional.of(lock)
        );

        manager.acquireWriteLock();

        verify(
                repository
        ).findByIdForUpdate(
                1L
        );
    }

    @Test
    void shouldFailFastWhenGlobalGraphLockRowIsMissing() {

        when(
                repository.findByIdForUpdate(
                        1L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        manager.acquireWriteLock()
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Transaction graph lock row is missing"
                );
    }
}
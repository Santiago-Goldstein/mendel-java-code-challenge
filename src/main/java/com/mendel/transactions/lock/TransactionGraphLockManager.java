package com.mendel.transactions.lock;

import org.springframework.stereotype.Component;

@Component
public class TransactionGraphLockManager {

    private static final long GLOBAL_GRAPH_LOCK_ID = 1L;

    private final TransactionGraphLockRepository repository;

    public TransactionGraphLockManager(
            TransactionGraphLockRepository repository
    ) {
        this.repository = repository;
    }

    public void acquireWriteLock() {

        repository.findByIdForUpdate(
                GLOBAL_GRAPH_LOCK_ID
        ).orElseThrow(
                () -> new IllegalStateException(
                        "Transaction graph lock row is missing"
                )
        );
    }
}
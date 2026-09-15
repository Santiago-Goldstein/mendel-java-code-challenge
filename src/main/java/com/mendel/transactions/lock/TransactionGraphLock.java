package com.mendel.transactions.lock;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_graph_lock")
public class TransactionGraphLock {

    @Id
    private long id;

    protected TransactionGraphLock() {
        // Required by JPA.
    }

    public TransactionGraphLock(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
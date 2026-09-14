package com.mendel.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private long id;

    @Column(
            nullable = false
    )
    private double amount;

    @Column(
            name = "transaction_type",
            nullable = false
    )
    private String type;

    @Column(
            name = "parent_id"
    )
    private Long parentId;

    protected Transaction() {
        /*
         * Required by JPA.
         */
    }

    public Transaction(
            long id,
            double amount,
            String type,
            Long parentId
    ) {
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.parentId = parentId;
    }

    public long getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public Long getParentId() {
        return parentId;
    }
}
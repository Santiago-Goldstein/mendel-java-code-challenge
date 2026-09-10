package com.mendel.transactions.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(long transactionId) {
        super("Transaction not found with id: " + transactionId);
    }
}
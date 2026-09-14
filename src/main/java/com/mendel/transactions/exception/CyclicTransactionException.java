package com.mendel.transactions.exception;

public class CyclicTransactionException extends RuntimeException {

    public CyclicTransactionException(long transactionId) {
        super(
                "Transaction relationship would create a cycle involving id: "
                        + transactionId
        );
    }
}
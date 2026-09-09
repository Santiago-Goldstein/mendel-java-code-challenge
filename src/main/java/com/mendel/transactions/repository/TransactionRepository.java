package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(long id);

    List<Transaction> findByType(String type);

    List<Transaction> findAll();
}
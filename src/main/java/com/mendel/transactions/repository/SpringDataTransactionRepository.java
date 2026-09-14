package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataTransactionRepository
        extends JpaRepository<Transaction, Long> {

    List<Transaction> findByType(String type);
}
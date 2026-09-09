package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<Long, Transaction> transactions =
            new ConcurrentHashMap<>();

    @Override
    public void save(Transaction transaction) {
        transactions.put(transaction.getId(), transaction);
    }

    @Override
    public Optional<Transaction> findById(long id) {
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public List<Transaction> findByType(String type) {
        return transactions.values()
                .stream()
                .filter(transaction ->
                        Objects.equals(transaction.getType(), type))
                .toList();
    }

    @Override
    public List<Transaction> findAll() {
        return List.copyOf(transactions.values());
    }
}
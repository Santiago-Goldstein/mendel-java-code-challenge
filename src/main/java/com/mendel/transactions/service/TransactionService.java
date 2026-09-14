package com.mendel.transactions.service;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.CyclicTransactionException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public void saveTransaction(
            long id,
            double amount,
            String type,
            Long parentId
    ) {
        Transaction transaction =
                new Transaction(
                        id,
                        amount,
                        type,
                        parentId
                );

        validateNoCycles(
                List.of(transaction)
        );

        repository.save(transaction);
    }

    public List<Long> findTransactionIdsByType(String type) {
        return repository.findByType(type)
                .stream()
                .map(Transaction::getId)
                .sorted()
                .toList();
    }

    public double calculateSum(long transactionId) {

        repository.findById(transactionId)
                .orElseThrow(
                        () -> new TransactionNotFoundException(
                                transactionId
                        )
                );

        List<Transaction> transactions =
                repository.findAll();

        Map<Long, Transaction> transactionsById =
                new HashMap<>();

        Map<Long, List<Long>> childrenByParent =
                new HashMap<>();

        for (Transaction transaction : transactions) {

            transactionsById.put(
                    transaction.getId(),
                    transaction
            );

            if (transaction.getParentId() != null) {
                childrenByParent
                        .computeIfAbsent(
                                transaction.getParentId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(transaction.getId());
            }
        }

        double sum = 0.0;

        Set<Long> visited =
                new HashSet<>();

        Deque<Long> pending =
                new ArrayDeque<>();

        pending.push(transactionId);

        while (!pending.isEmpty()) {

            long currentId =
                    pending.pop();

            if (!visited.add(currentId)) {
                continue;
            }

            Transaction transaction =
                    transactionsById.get(currentId);

            if (transaction == null) {
                continue;
            }

            sum += transaction.getAmount();

            List<Long> children =
                    childrenByParent.getOrDefault(
                            currentId,
                            List.of()
                    );

            for (Long childId : children) {
                pending.push(childId);
            }
        }

        return sum;
    }

    public int saveTransactions(
            List<Transaction> transactions
    ) {
        validateNoCycles(transactions);

        for (Transaction transaction : transactions) {
            repository.save(transaction);
        }

        return transactions.size();
    }

    private void validateNoCycles(
            List<Transaction> candidateTransactions
    ) {

        Map<Long, Long> parentByTransactionId =
                new HashMap<>();

        /*
         * Build the current persisted state.
         */
        for (Transaction transaction : repository.findAll()) {
            parentByTransactionId.put(
                    transaction.getId(),
                    transaction.getParentId()
            );
        }

        /*
         * Apply the proposed changes in memory first.
         *
         * If an ID already exists, this replaces its parent relationship,
         * matching the PUT semantics used by the API.
         */
        for (Transaction transaction : candidateTransactions) {
            parentByTransactionId.put(
                    transaction.getId(),
                    transaction.getParentId()
            );
        }

        validateGraphHasNoCycles(
                parentByTransactionId
        );
    }

    private void validateGraphHasNoCycles(
            Map<Long, Long> parentByTransactionId
    ) {

        Set<Long> completelyValidated =
                new HashSet<>();

        for (Long startingId :
                parentByTransactionId.keySet()) {

            if (completelyValidated.contains(startingId)) {
                continue;
            }

            Set<Long> currentPath =
                    new HashSet<>();

            Long currentId =
                    startingId;

            while (
                    currentId != null
                            && parentByTransactionId
                            .containsKey(currentId)
            ) {

                if (completelyValidated.contains(currentId)) {
                    break;
                }

                if (!currentPath.add(currentId)) {
                    throw new CyclicTransactionException(
                            currentId
                    );
                }

                currentId =
                        parentByTransactionId.get(currentId);
            }

            completelyValidated.addAll(
                    currentPath
            );
        }
    }
}
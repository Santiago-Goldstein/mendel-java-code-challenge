package com.mendel.transactions.service;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.CyclicTransactionException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.lock.TransactionGraphLockManager;
import com.mendel.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TransactionGraphLockManager graphLockManager;

    public TransactionService(
            TransactionRepository repository,
            TransactionGraphLockManager graphLockManager
    ) {
        this.repository = repository;
        this.graphLockManager = graphLockManager;
    }

    @Transactional
    public void saveTransaction(
            long id,
            double amount,
            String type,
            Long parentId
    ) {
        /*
         * All graph mutations are serialized through
         * the same database-backed pessimistic lock.
         */
        graphLockManager.acquireWriteLock();

        Transaction transaction =
                new Transaction(
                        id,
                        amount,
                        type,
                        parentId
                );

        /*
         * Validation and persistence happen inside
         * the same database transaction.
         */
        validateNoCycles(
                List.of(transaction)
        );

        repository.save(transaction);
    }

    @Transactional(readOnly = true)
    public List<Long> findTransactionIdsByType(
            String type
    ) {
        return repository
                .findByType(type)
                .stream()
                .map(Transaction::getId)
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public double calculateSum(
            long transactionId
    ) {
        repository
                .findById(transactionId)
                .orElseThrow(
                        () ->
                                new TransactionNotFoundException(
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
                                ignored ->
                                        new ArrayList<>()
                        )
                        .add(
                                transaction.getId()
                        );
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

    @Transactional
    public int saveTransactions(
            List<Transaction> transactions
    ) {
        graphLockManager.acquireWriteLock();

        /*
         * The complete batch is validated before
         * anything is persisted.
         */
        validateNoCycles(transactions);

        /*
         * The complete batch participates in this
         * same transaction. Any persistence failure
         * rolls the entire operation back.
         */
        repository.saveAll(transactions);

        return transactions.size();
    }

    private void validateNoCycles(
            List<Transaction> candidateTransactions
    ) {
        Map<Long, Long> parentByTransactionId =
                new HashMap<>();

        /*
         * Load the currently committed graph.
         *
         * Because the global write lock was acquired
         * first, no other graph mutation can commit
         * between this validation and our persistence.
         */
        for (
                Transaction transaction :
                repository.findAll()
        ) {
            parentByTransactionId.put(
                    transaction.getId(),
                    transaction.getParentId()
            );
        }

        /*
         * Apply proposed changes in memory first.
         * Existing transaction IDs are replaced,
         * preserving PUT semantics.
         */
        for (
                Transaction transaction :
                candidateTransactions
        ) {
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

        for (
                Long startingId :
                parentByTransactionId.keySet()
        ) {
            if (
                    completelyValidated
                            .contains(startingId)
            ) {
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
                if (
                        completelyValidated
                                .contains(currentId)
                ) {
                    break;
                }

                if (!currentPath.add(currentId)) {

                    throw new CyclicTransactionException(
                            currentId
                    );
                }

                currentId =
                        parentByTransactionId.get(
                                currentId
                        );
            }

            completelyValidated.addAll(
                    currentPath
            );
        }
    }
}
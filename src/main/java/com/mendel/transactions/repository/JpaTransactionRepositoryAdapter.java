package com.mendel.transactions.repository;

import com.mendel.transactions.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaTransactionRepositoryAdapter
        implements TransactionRepository {

    private final SpringDataTransactionRepository repository;

    public JpaTransactionRepositoryAdapter(
            SpringDataTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public void save(
            Transaction transaction
    ) {
        repository.save(transaction);
    }

    @Override
    public void saveAll(
            List<Transaction> transactions
    ) {
        /*
         * Flush while the service transaction is still
         * active so persistence errors are detected
         * within the use-case boundary.
         *
         * A failure still rolls the whole transaction
         * back.
         */
        repository.saveAllAndFlush(
                transactions
        );
    }

    @Override
    public Optional<Transaction> findById(
            long id
    ) {
        return repository.findById(id);
    }

    @Override
    public List<Transaction> findByType(
            String type
    ) {
        return repository.findByType(type);
    }

    @Override
    public List<Transaction> findAll() {
        return repository.findAll();
    }
}
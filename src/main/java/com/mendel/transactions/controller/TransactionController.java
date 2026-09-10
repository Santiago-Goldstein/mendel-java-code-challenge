package com.mendel.transactions.controller;

import com.mendel.transactions.csv.TransactionCsvParser;
import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.dto.ImportResponse;
import com.mendel.transactions.dto.StatusResponse;
import com.mendel.transactions.dto.SumResponse;
import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.exception.InvalidCsvException;
import com.mendel.transactions.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionCsvParser csvParser;

    public TransactionController(
            TransactionService transactionService,
            TransactionCsvParser csvParser
    ) {
        this.transactionService = transactionService;
        this.csvParser = csvParser;
    }

    @PutMapping("/{transactionId}")
    public StatusResponse saveTransaction(
            @PathVariable long transactionId,
            @Valid @RequestBody TransactionRequest request
    ) {
        transactionService.saveTransaction(
                transactionId,
                request.amount(),
                request.type(),
                request.parentId()
        );

        return new StatusResponse("ok");
    }

    @GetMapping("/types/{type}")
    public List<Long> findTransactionsByType(
            @PathVariable String type
    ) {
        return transactionService
                .findTransactionIdsByType(type);
    }

    @GetMapping("/sum/{transactionId}")
    public SumResponse calculateSum(
            @PathVariable long transactionId
    ) {
        double sum =
                transactionService.calculateSum(
                        transactionId
                );

        return new SumResponse(sum);
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ImportResponse importTransactions(
            @RequestPart("file") MultipartFile file
    ) {

        if (file.isEmpty()) {
            throw new InvalidCsvException(
                    "CSV file cannot be empty"
            );
        }

        try {

            List<Transaction> transactions =
                    csvParser.parse(
                            file.getInputStream()
                    );

            int imported =
                    transactionService.saveTransactions(
                            transactions
                    );

            return new ImportResponse(imported);

        } catch (IOException exception) {

            throw new InvalidCsvException(
                    "Could not read CSV file",
                    exception
            );
        }
    }
}
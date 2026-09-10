package com.mendel.transactions.controller;

import com.mendel.transactions.dto.StatusResponse;
import com.mendel.transactions.dto.SumResponse;
import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
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
        return transactionService.findTransactionIdsByType(type);
    }

    @GetMapping("/sum/{transactionId}")
    public SumResponse calculateSum(
            @PathVariable long transactionId
    ) {
        double sum =
                transactionService.calculateSum(transactionId);

        return new SumResponse(sum);
    }
}
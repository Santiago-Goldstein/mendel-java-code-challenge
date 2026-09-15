package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mendel.transactions.domain.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionRequest(

        @NotNull(message = "amount is required")
        Double amount,

        @NotBlank(message = "type is required")
        @Size(
                max = Transaction.MAX_TYPE_LENGTH,
                message = "type must be at most 255 characters"
        )
        String type,

        @JsonProperty("parent_id")
        Long parentId
) {
}
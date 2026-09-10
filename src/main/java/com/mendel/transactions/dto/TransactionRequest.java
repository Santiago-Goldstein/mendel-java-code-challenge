package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(

        @NotNull(message = "amount is required")
        Double amount,

        @NotBlank(message = "type is required")
        String type,

        @JsonProperty("parent_id")
        Long parentId
) {
}
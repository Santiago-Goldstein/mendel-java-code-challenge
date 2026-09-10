package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(

        @NotNull
        Double amount,

        @NotBlank
        String type,

        @JsonProperty("parent_id")
        Long parentId
) {
}
package com.mendel.transactions.dto;

public record ApiErrorResponse(
        int status,
        String error,
        String message
) {
}
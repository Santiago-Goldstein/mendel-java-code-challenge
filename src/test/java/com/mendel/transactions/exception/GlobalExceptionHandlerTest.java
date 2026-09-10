package com.mendel.transactions.exception;

import com.mendel.transactions.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturnNotFoundWhenTransactionDoesNotExist() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleTransactionNotFound(
                        new TransactionNotFoundException(999L)
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isEqualTo(
                        new ApiErrorResponse(
                                404,
                                "Not Found",
                                "Transaction not found with id: 999"
                        )
                );
    }

    @Test
    void shouldReturnBadRequestForInvalidCsv() {

        ResponseEntity<ApiErrorResponse> response =
                handler.handleInvalidCsv(
                        new InvalidCsvException(
                                "Invalid CSV file"
                        )
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(response.getBody())
                .isEqualTo(
                        new ApiErrorResponse(
                                400,
                                "Bad Request",
                                "Invalid CSV file"
                        )
                );
    }
}
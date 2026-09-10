package com.mendel.transactions.exception;

import com.mendel.transactions.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Comparator;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException exception
    ) {
        return buildError(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCsvException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCsv(
            InvalidCsvException exception
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {

        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(
                        Comparator.comparing(
                                fieldError -> fieldError.getField()
                        )
                )
                .map(fieldError -> fieldError.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Request validation failed";
        }

        return buildError(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException exception
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request"
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException exception
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Required multipart field '"
                        + exception.getRequestPartName()
                        + "' is missing"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Invalid value for '"
                        + exception.getName()
                        + "'"
        );
    }

    private ResponseEntity<ApiErrorResponse> buildError(
            HttpStatus status,
            String message
    ) {

        ApiErrorResponse response =
                new ApiErrorResponse(
                        status.value(),
                        status.getReasonPhrase(),
                        message
                );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}
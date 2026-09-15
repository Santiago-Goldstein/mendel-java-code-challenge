package com.mendel.transactions.csv;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.InvalidCsvException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCsvParserTest {

    private TransactionCsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new TransactionCsvParser();
    }

    @Test
    void shouldParseValidCsv() {
        String csv = """
                id,amount,type,parent_id
                10,5000,cars,
                11,10000,shopping,10
                12,5000,shopping,11
                """;

        List<Transaction> transactions = parser.parse(stream(csv));

        assertThat(transactions).hasSize(3);

        assertThat(transactions.get(0).getId()).isEqualTo(10L);
        assertThat(transactions.get(0).getAmount()).isEqualTo(5000.0);
        assertThat(transactions.get(0).getType()).isEqualTo("cars");
        assertThat(transactions.get(0).getParentId()).isNull();

        assertThat(transactions.get(1).getParentId()).isEqualTo(10L);
        assertThat(transactions.get(2).getParentId()).isEqualTo(11L);
    }

    @Test
    void shouldSupportQuotedValuesContainingCommas() {
        String csv = """
                id,amount,type,parent_id
                10,5000,"cars,classic",
                """;

        List<Transaction> transactions = parser.parse(stream(csv));

        assertThat(transactions)
                .extracting(Transaction::getType)
                .containsExactly("cars,classic");
    }

    @Test
    void shouldRejectCsvWithMissingRequiredHeader() {

        String csv = """
            id,amount,type
            10,5000,cars
            """;

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessage(
                        "CSV must contain headers: id, amount, type, parent_id"
                );
    }

    @Test
    void shouldRejectInvalidNumericValues() {
        String csv = """
                id,amount,type,parent_id
                invalid,5000,cars,
                """;

        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void shouldRejectDuplicateIdsInsideSameCsv() {
        String csv = """
                id,amount,type,parent_id
                10,5000,cars,
                10,7000,shopping,
                """;

        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void shouldRejectNullInput() {

        assertThatThrownBy(
                () -> parser.parse(null)
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessage(
                        "CSV input cannot be null"
                );
    }

    @Test
    void shouldRejectCsvWithoutTransactions() {

        String csv = """
            id,amount,type,parent_id
            """;

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessage(
                        "CSV does not contain any transactions"
                );
    }

    @Test
    void shouldRejectMissingRequiredValue() {

        String csv = """
            id,amount,type,parent_id
            10,,cars,
            """;

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessageContaining(
                        "Missing required value"
                );
    }

    @Test
    void shouldRejectNonFiniteAmount() {

        String csv = """
            id,amount,type,parent_id
            10,Infinity,cars,
            """;

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessageContaining(
                        "Amount must be a finite number"
                );
    }

    @Test
    void shouldRejectInvalidParentId() {

        String csv = """
            id,amount,type,parent_id
            10,5000,cars,invalid-parent
            """;

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessageContaining(
                        "Invalid numeric value"
                );
    }

    @Test
    void shouldRejectTypeLongerThanDatabaseLimit() {

        String longType =
                "x".repeat(
                        Transaction.MAX_TYPE_LENGTH + 1
                );

        String csv = """
            id,amount,type,parent_id
            10,5000,%s,
            """.formatted(longType);

        assertThatThrownBy(
                () -> parser.parse(
                        stream(csv)
                )
        )
                .isInstanceOf(
                        InvalidCsvException.class
                )
                .hasMessageContaining(
                        "Type must be at most 255 characters"
                );
    }



    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }
}
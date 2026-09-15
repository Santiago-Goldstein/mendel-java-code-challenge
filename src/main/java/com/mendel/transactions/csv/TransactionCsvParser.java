package com.mendel.transactions.csv;

import com.mendel.transactions.domain.Transaction;
import com.mendel.transactions.exception.InvalidCsvException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TransactionCsvParser {

    private static final List<String> REQUIRED_HEADERS =
            List.of(
                    "id",
                    "amount",
                    "type",
                    "parent_id"
            );

    public List<Transaction> parse(
            InputStream inputStream
    ) {

        if (inputStream == null) {
            throw new InvalidCsvException(
                    "CSV input cannot be null"
            );
        }

        CSVFormat format =
                CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .get();

        try (
                Reader reader =
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8
                        );

                CSVParser parser =
                        CSVParser.parse(
                                reader,
                                format
                        )
        ) {

            validateHeaders(parser);

            List<Transaction> transactions =
                    new ArrayList<>();

            Set<Long> ids =
                    new HashSet<>();

            for (CSVRecord record : parser) {

                Transaction transaction =
                        parseRecord(record);

                if (!ids.add(
                        transaction.getId()
                )) {

                    throw new InvalidCsvException(
                            "Duplicate transaction id in CSV: "
                                    + transaction.getId()
                    );
                }

                transactions.add(
                        transaction
                );
            }

            if (transactions.isEmpty()) {

                throw new InvalidCsvException(
                        "CSV does not contain any transactions"
                );
            }

            return List.copyOf(
                    transactions
            );

        } catch (InvalidCsvException exception) {

            throw exception;

        } catch (
                IOException
                | IllegalArgumentException exception
        ) {

            throw new InvalidCsvException(
                    "Invalid CSV file",
                    exception
            );
        }
    }

    private void validateHeaders(
            CSVParser parser
    ) {

        Set<String> headers =
                parser
                        .getHeaderMap()
                        .keySet();

        if (!headers.containsAll(
                REQUIRED_HEADERS
        )) {

            throw new InvalidCsvException(
                    "CSV must contain headers: "
                            + String.join(
                            ", ",
                            REQUIRED_HEADERS
                    )
            );
        }
    }

    private Transaction parseRecord(
            CSVRecord record
    ) {

        String idValue =
                record.get("id");

        String amountValue =
                record.get("amount");

        String type =
                record.get("type");

        String parentIdValue =
                record.get("parent_id");

        if (
                idValue.isBlank()
                        || amountValue.isBlank()
                        || type.isBlank()
        ) {

            throw new InvalidCsvException(
                    "Missing required value at CSV record "
                            + record.getRecordNumber()
            );
        }

        if (
                type.length()
                        > Transaction.MAX_TYPE_LENGTH
        ) {

            throw new InvalidCsvException(
                    "Type must be at most "
                            + Transaction.MAX_TYPE_LENGTH
                            + " characters at CSV record "
                            + record.getRecordNumber()
            );
        }

        try {

            long id =
                    Long.parseLong(
                            idValue
                    );

            double amount =
                    Double.parseDouble(
                            amountValue
                    );

            if (!Double.isFinite(amount)) {

                throw new InvalidCsvException(
                        "Amount must be a finite number at CSV record "
                                + record.getRecordNumber()
                );
            }

            Long parentId =
                    parentIdValue.isBlank()
                            ? null
                            : Long.parseLong(
                            parentIdValue
                    );

            return new Transaction(
                    id,
                    amount,
                    type,
                    parentId
            );

        } catch (
                NumberFormatException exception
        ) {

            throw new InvalidCsvException(
                    "Invalid numeric value at CSV record "
                            + record.getRecordNumber(),
                    exception
            );
        }
    }
}
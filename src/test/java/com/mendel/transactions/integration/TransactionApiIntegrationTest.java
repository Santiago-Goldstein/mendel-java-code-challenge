package com.mendel.transactions.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;

import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
)
class TransactionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateTransaction() throws Exception {

        String requestBody = """
            {
                "amount": 5000,
                "type": "cars"
            }
            """;

        mockMvc.perform(
                        put("/transactions/1001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void shouldFindTransactionsByTypeAfterCreation() throws Exception {

        String firstTransaction = """
            {
                "amount": 5000,
                "type": "integration-shopping"
            }
            """;

        String secondTransaction = """
            {
                "amount": 10000,
                "type": "integration-shopping"
            }
            """;

        mockMvc.perform(
                put("/transactions/1101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstTransaction)
        ).andExpect(status().isOk());

        mockMvc.perform(
                put("/transactions/1102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondTransaction)
        ).andExpect(status().isOk());

        mockMvc.perform(
                        get("/transactions/types/integration-shopping")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(1101))
                .andExpect(jsonPath("$[1]").value(1102));
    }

    @Test
    void shouldCalculateTransitiveTransactionSum() throws Exception {

        mockMvc.perform(
                put("/transactions/1201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 5000,
                                "type": "cars"
                            }
                            """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                put("/transactions/1202")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 10000,
                                "type": "shopping",
                                "parent_id": 1201
                            }
                            """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                put("/transactions/1203")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 5000,
                                "type": "shopping",
                                "parent_id": 1202
                            }
                            """)
        ).andExpect(status().isOk());

        mockMvc.perform(
                        get("/transactions/sum/1201")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(20000.0));

        mockMvc.perform(
                        get("/transactions/sum/1202")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(15000.0));
    }

    @Test
    void shouldReturnNotFoundWhenTransactionDoesNotExist()
            throws Exception {

        mockMvc.perform(
                        get("/transactions/sum/999999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Transaction not found with id: 999999"
                                )
                );
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsMissing()
            throws Exception {

        mockMvc.perform(
                        put("/transactions/1301")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "type": "cars"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("amount is required")
                );
    }

    @Test
    void shouldReturnBadRequestWhenTypeIsBlank()
            throws Exception {

        mockMvc.perform(
                        put("/transactions/1302")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "amount": 5000,
                                        "type": "   "
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("type is required")
                );
    }

    @Test
    void shouldReturnBadRequestForMalformedJson()
            throws Exception {

        String malformedJson = """
            {
                "amount": 5000,
                "type": "cars"
            """;

        mockMvc.perform(
                        put("/transactions/1303")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Malformed JSON request")
                );
    }

    @Test
    void shouldImportTransactionsFromCsv()
            throws Exception {

        String csv = """
            id,amount,type,parent_id
            1401,5000,csv-cars,
            1402,10000,csv-shopping,1401
            1403,5000,csv-shopping,1402
            """;

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "transactions.csv",
                        "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8)
                );

        mockMvc.perform(
                        multipart("/transactions/import")
                                .file(file)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3));

        mockMvc.perform(
                        get("/transactions/sum/1401")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(20000.0));
    }

    @Test
    void shouldReturnBadRequestForInvalidCsv()
            throws Exception {

        String csv = """
            id,amount,type,parent_id
            invalid,5000,cars,
            """;

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "transactions.csv",
                        "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8)
                );

        mockMvc.perform(
                        multipart("/transactions/import")
                                .file(file)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.error")
                                .value("Bad Request")
                );
    }

    @Test
    void shouldReturnBadRequestWhenCsvFileIsMissing()
            throws Exception {

        mockMvc.perform(
                        multipart("/transactions/import")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Required multipart field 'file' is missing"
                                )
                );
    }


}


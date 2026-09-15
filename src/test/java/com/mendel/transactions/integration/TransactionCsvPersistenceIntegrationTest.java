package com.mendel.transactions.integration;

import com.mendel.transactions.service.TransactionService;
import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainerConfiguration.class)
class TransactionCsvPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {

        jdbcTemplate.update(
                "DELETE FROM transactions"
        );
    }

    @Test
    void shouldReplaceExistingTransactionThroughCsv()
            throws Exception {

        /*
         * Initial graph:
         *
         * 8001 = 1000
         *   |
         * 8002 = 2000
         *
         * sum(8001) = 3000
         */
        service.saveTransaction(
                8001L,
                1000.0,
                "old-root",
                null
        );

        service.saveTransaction(
                8002L,
                2000.0,
                "csv-old",
                8001L
        );

        /*
         * The CSV replaces 8002 and creates
         * its new parent 8003.
         *
         * Notice that 8002 references 8003
         * before 8003 appears in the file.
         */
        String csv = """
                id,amount,type,parent_id
                8002,5000,csv-new,8003
                8003,3000,csv-new-root,
                """;

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "replacement.csv",
                        "text/csv",
                        csv.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        mockMvc.perform(
                        multipart(
                                "/transactions/import"
                        )
                                .file(file)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.imported")
                                .value(2)
                );

        /*
         * Old type must disappear.
         */
        mockMvc.perform(
                        get(
                                "/transactions/types/csv-old"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$")
                                .isEmpty()
                );

        /*
         * Replacement type must exist.
         */
        mockMvc.perform(
                        get(
                                "/transactions/types/csv-new"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$[0]")
                                .value(8002)
                );

        /*
         * 8002 no longer belongs to 8001.
         */
        mockMvc.perform(
                        get(
                                "/transactions/sum/8001"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sum")
                                .value(1000.0)
                );

        /*
         * New graph:
         *
         * 8003 = 3000
         *   |
         * 8002 = 5000
         *
         * total = 8000
         */
        mockMvc.perform(
                        get(
                                "/transactions/sum/8003"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sum")
                                .value(8000.0)
                );
    }
}
package com.mendel.transactions.integration;

import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(
        MySqlTestContainerConfiguration.class
)
class CycleValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {

        jdbcTemplate.update(
                "DELETE FROM transactions"
        );
    }

    @Test
    void shouldRejectDirectSelfCycle()
            throws Exception {

        mockMvc.perform(
                        put("/transactions/2001")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                            "amount": 5000,
                                            "type": "cycle",
                                            "parent_id": 2001
                                        }
                                        """)
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Conflict")
                );
    }

    @Test
    void shouldRejectIndirectCycleAndPreservePreviousState()
            throws Exception {

        mockMvc.perform(
                        put("/transactions/2101")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                {
                                    "amount": 1000,
                                    "type": "cycle"
                                }
                                """)
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        put("/transactions/2102")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                {
                                    "amount": 2000,
                                    "type": "cycle",
                                    "parent_id": 2101
                                }
                                """)
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        put("/transactions/2101")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                            "amount": 1000,
                                            "type": "cycle",
                                            "parent_id": 2102
                                        }
                                        """)
                )
                .andExpect(
                        status().isConflict()
                );

        mockMvc.perform(
                        get(
                                "/transactions/sum/2101"
                        )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sum")
                                .value(3000.0)
                );
    }

    @Test
    void shouldRejectCyclicCsvWithoutImportingAnyTransaction()
            throws Exception {

        String csv = """
                id,amount,type,parent_id
                2201,5000,cycle,2202
                2202,10000,cycle,2201
                """;

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "transactions.csv",
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
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                );

        mockMvc.perform(
                        get(
                                "/transactions/sum/2201"
                        )
                )
                .andExpect(
                        status().isNotFound()
                );
    }
}
package com.mendel.transactions;

import com.mendel.transactions.support.MySqlTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(
        MySqlTestContainerConfiguration.class
)
class TransactionsApplicationTests {

    @Test
    void contextLoads() {
    }
}
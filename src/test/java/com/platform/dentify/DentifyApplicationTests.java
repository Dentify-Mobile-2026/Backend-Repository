package com.platform.dentify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Necesita una base de datos real: solo corre si DB_URL está definida.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest
class DentifyApplicationTests {

    @Test
    void contextLoads() {
    }

}

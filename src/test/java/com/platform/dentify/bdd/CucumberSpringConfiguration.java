package com.platform.dentify.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Contexto de Spring para los escenarios BDD: la aplicación completa (seguridad JWT incluida)
 * sobre una base H2 en memoria, para que los escenarios corran igual en cualquier equipo y en el CI.
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dentify_bdd;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
}

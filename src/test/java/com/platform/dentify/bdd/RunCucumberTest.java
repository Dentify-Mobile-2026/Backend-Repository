package com.platform.dentify.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Ejecuta los escenarios BDD de {@code src/test/resources/features} con Cucumber dentro de
 * {@code ./mvnw test}. Cada archivo .feature corresponde a una épica del Anexo B y cada escenario
 * a un criterio de aceptación (Dado, Cuando, Entonces). El resumen queda en target/cucumber.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.platform.dentify.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "summary, html:target/cucumber/report.html, json:target/cucumber/report.json")
public class RunCucumberTest {
}

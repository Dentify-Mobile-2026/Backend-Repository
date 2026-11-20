package com.platform.dentify.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Elimina la base temporal declarada con {@link IsolatedTestDatabase} cuando termina la clase
 * de pruebas, después de que Spring haya cerrado su contexto.
 */
public final class IsolatedTestDatabaseExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        String database = AnnotationSupport.findAnnotation(testClass, IsolatedTestDatabase.class)
                .map(IsolatedTestDatabase::value)
                .orElseThrow(() -> new IllegalStateException(
                        testClass.getName() + " no declara @IsolatedTestDatabase"));
        IntegrationTestDatabase.drop(database);
    }
}

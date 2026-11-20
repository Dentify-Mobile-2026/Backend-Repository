package com.platform.dentify.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara la base temporal MySQL de una clase de pruebas de integración y garantiza que se
 * elimine al terminar la clase, aunque alguna prueba falle.
 * <p>
 * Orden al terminar la clase:
 * <ol>
 *   <li>{@code @DirtiesContext(AFTER_CLASS)}: Spring cierra el contexto de la clase y su pool
 *   de conexiones (si quedara abierto, el pool podría volver a crear la base con
 *   {@code createDatabaseIfNotExist});</li>
 *   <li>{@link IsolatedTestDatabaseExtension} espera a que no queden sesiones en la base, la
 *   borra con {@link IntegrationTestDatabase#drop} y verifica que ya no exista.</li>
 * </ol>
 * <b>Debe declararse antes que {@code @SpringBootTest}</b>: JUnit ejecuta los
 * {@code afterAll} en orden inverso al de registro, así que la extensión corre después de que
 * Spring cierre el contexto. Si el orden fuera otro, el borrado falla de forma visible en vez
 * de borrar una base en uso.
 * <p>
 * El nombre pasa por {@link IntegrationTestDatabase#requireTemporary}: solo se aceptan bases
 * {@code dentify_it_<sufijo>}; {@code dentify} y la base de {@code DB_URL} nunca se borran.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(IsolatedTestDatabaseExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface IsolatedTestDatabase {

    /** Nombre de la base temporal: {@code dentify_it_<a-z0-9_>}. */
    String value();
}

package com.platform.dentify.shared.infrastructure.time;

import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj de la aplicación. Los instantes ({@code Instant}) son siempre UTC; la zona del reloj
 * es la del negocio ({@link BusinessTime#ZONE}) y solo se usa para calcular fechas locales
 * como el mes contable. Se inyecta para poder fijarlo en las pruebas.
 */
@Configuration
public class BusinessClockConfiguration {

    @Bean
    public Clock businessClock() {
        return Clock.system(BusinessTime.ZONE);
    }
}

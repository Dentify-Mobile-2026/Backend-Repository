package com.platform.dentify.shared.domain.model.valueobjects;

import java.time.ZoneId;

/**
 * Zona horaria del negocio. Los cobros se almacenan como instantes UTC; los informes (mes
 * contable del dashboard) se calculan en esta zona. Ejemplo: un abono del 30/11 a las 23:30
 * en Lima es el 01/12 a las 04:30 UTC y pertenece a noviembre.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Lima");

    private BusinessTime() {
    }
}

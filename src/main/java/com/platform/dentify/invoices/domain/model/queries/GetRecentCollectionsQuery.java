package com.platform.dentify.invoices.domain.model.queries;

/** Últimos cobros (abonos o comprobantes históricos) del usuario, del más reciente al más antiguo. */
public record GetRecentCollectionsQuery(Long userId, int limit) {
    public GetRecentCollectionsQuery {
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }
}

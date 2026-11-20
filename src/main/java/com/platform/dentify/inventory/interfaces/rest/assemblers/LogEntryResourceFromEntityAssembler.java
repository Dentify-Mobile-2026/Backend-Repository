package com.platform.dentify.inventory.interfaces.rest.assemblers;

import com.platform.dentify.inventory.domain.model.aggregates.LogEntry;
import com.platform.dentify.inventory.interfaces.rest.dtos.LogEntryResource;

public class LogEntryResourceFromEntityAssembler {

    public static LogEntryResource toResourceFromEntity(LogEntry entity) {
        Long invoiceId = entity.getInvoice() != null ? entity.getInvoice().getId() : null;
        return new LogEntryResource(
                entity.getId(),
                entity.getItem().getId(),
                entity.getItem().getName(),
                entity.getPreviousStock(),
                entity.getNewStock(),
                entity.getConsumedQuantity(),
                entity.getReason(),
                invoiceId,
                entity.getCreatedAt()
        );
    }
}

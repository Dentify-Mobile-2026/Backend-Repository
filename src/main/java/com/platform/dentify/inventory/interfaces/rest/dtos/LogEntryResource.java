package com.platform.dentify.inventory.interfaces.rest.dtos;

import java.time.LocalDateTime;

public record LogEntryResource(
        Long id,
        Long itemId,
        String itemName,
        int previousStock,
        int newStock,
        int consumedQuantity,
        String reason,
        Long invoiceId,
        LocalDateTime createdAt
) {

}

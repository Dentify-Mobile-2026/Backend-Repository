package com.platform.dentify.dashboard.interfaces.dto;

public record FinancialSummaryDto(
        double totalCollected,
        double pendingBalance,
        double collectionRate
) {}

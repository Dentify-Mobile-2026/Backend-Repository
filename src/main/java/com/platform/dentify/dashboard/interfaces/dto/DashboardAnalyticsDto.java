package com.platform.dentify.dashboard.interfaces.dto;

import java.util.List;

public record DashboardAnalyticsDto(
        String month,
        List<SpecialtyRevenueDto> revenueBySpecialty,
        AttendanceMetricsDto attendance,
        FinancialSummaryDto financials
) {}

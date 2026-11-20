package com.platform.dentify.dashboard.interfaces.dto;

public record AttendanceMetricsDto(
        long totalAppointments,
        long completedCount,
        long cancelledCount,
        long noShowCount,
        double attendanceRate
) {}

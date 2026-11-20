package com.platform.dentify.dashboard;

import com.platform.dentify.dashboard.application.DashboardQueryServiceImpl;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.domain.services.ItemQueryService;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.domain.services.AppointmentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DashboardAnalyticsTest {

    private DashboardQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DashboardQueryServiceImpl(
                mock(ItemQueryService.class),
                mock(InvoiceQueryService.class),
                mock(AppointmentQueryService.class),
                mock(AuthenticatedUserProvider.class)
        );
    }

    private Appointment buildAppointment(
            LocalDateTime date,
            AppointmentStatus status,
            Treatment treatment
    ) {
        var appt = new Appointment(new CreateAppointmentCommand(date, "Consulta", LocalTime.of(0, 30), 1L));
        appt.setTreatment(treatment);
        appt.updateStatus(status, "");
        return appt;
    }

    private Invoice buildInvoice(double amount, LocalDateTime createdAt, Appointment appt) {
        var inv = new Invoice(new CreateInvoiceCommand(amount, 1L, 1L));
        ReflectionTestUtils.setField(inv, "createdAt", createdAt);
        inv.setAppointment(appt);
        if (appt != null) {
            appt.setInvoice(inv);
        }
        return inv;
    }

    @Test
    void computesMonthlyRevenueBySpecialtyAttendanceAndFinancials() {
        YearMonth targetMonth = YearMonth.of(2026, 11);

        var endo = new Treatment(new CreateTreatmentCommand("Endodoncia molar", "", "ENDODONTICS", 350.0, 60));
        var prev = new Treatment(new CreateTreatmentCommand("Profilaxis", "", "PREVENTION", 120.0, 30));

        var apptEndoPaid = buildAppointment(LocalDateTime.of(2026, 11, 5, 10, 0), AppointmentStatus.COMPLETED, endo);
        var apptPrevPaid = buildAppointment(LocalDateTime.of(2026, 11, 8, 11, 0), AppointmentStatus.COMPLETED, prev);
        var apptUnpaidCompleted = buildAppointment(LocalDateTime.of(2026, 11, 10, 12, 0), AppointmentStatus.COMPLETED, prev);
        var apptCancelled = buildAppointment(LocalDateTime.of(2026, 11, 12, 9, 0), AppointmentStatus.CANCELLED, endo);
        var apptNoShow = buildAppointment(LocalDateTime.of(2026, 11, 14, 15, 0), AppointmentStatus.NO_SHOW, prev);
        var apptOtherMonth = buildAppointment(LocalDateTime.of(2026, 10, 20, 9, 0), AppointmentStatus.COMPLETED, endo);

        var inv1 = buildInvoice(350.0, LocalDateTime.of(2026, 11, 5, 11, 0), apptEndoPaid);
        var inv2 = buildInvoice(120.0, LocalDateTime.of(2026, 11, 8, 12, 0), apptPrevPaid);
        var invGeneral = buildInvoice(80.0, LocalDateTime.of(2026, 11, 9, 16, 0), null);
        var invOldMonth = buildInvoice(500.0, LocalDateTime.of(2026, 10, 20, 10, 0), apptOtherMonth);

        var result = service.computeMonthlyAnalytics(
                targetMonth,
                List.of(inv1, inv2, invGeneral, invOldMonth),
                List.of(apptEndoPaid, apptPrevPaid, apptUnpaidCompleted, apptCancelled, apptNoShow, apptOtherMonth)
        );

        assertEquals("2026-11", result.month());
        assertEquals(3, result.revenueBySpecialty().size());
        assertEquals("ENDODONTICS", result.revenueBySpecialty().get(0).category());
        assertEquals(350.0, result.revenueBySpecialty().get(0).revenue());
        assertEquals("PREVENTION", result.revenueBySpecialty().get(1).category());
        assertEquals(120.0, result.revenueBySpecialty().get(1).revenue());
        assertEquals("GENERAL", result.revenueBySpecialty().get(2).category());
        assertEquals(80.0, result.revenueBySpecialty().get(2).revenue());

        // 3 completed, 1 cancelled, 1 noShow -> 3 / 5 = 60.0%
        assertEquals(5, result.attendance().totalAppointments());
        assertEquals(3, result.attendance().completedCount());
        assertEquals(1, result.attendance().cancelledCount());
        assertEquals(1, result.attendance().noShowCount());
        assertEquals(60.0, result.attendance().attendanceRate());

        // totalCollected = 350 + 120 + 80 = 550; pendingBalance = 120 (unpaid completed)
        assertEquals(550.0, result.financials().totalCollected());
        assertEquals(120.0, result.financials().pendingBalance());
        assertEquals(82.1, result.financials().collectionRate());
    }

    @Test
    void returnsZeroMetricsWhenMonthHasNoData() {
        var result = service.computeMonthlyAnalytics(YearMonth.of(2026, 11), List.of(), List.of());

        assertTrue(result.revenueBySpecialty().isEmpty());
        assertEquals(0, result.attendance().totalAppointments());
        assertEquals(0.0, result.attendance().attendanceRate());
        assertEquals(0.0, result.financials().totalCollected());
        assertEquals(0.0, result.financials().pendingBalance());
        assertEquals(0.0, result.financials().collectionRate());
    }
}

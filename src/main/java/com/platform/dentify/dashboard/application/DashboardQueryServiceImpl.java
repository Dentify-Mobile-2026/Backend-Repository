package com.platform.dentify.dashboard.application;

import com.platform.dentify.dashboard.domain.services.DashboardQueryService;
import com.platform.dentify.dashboard.interfaces.dto.AppointmentDto;
import com.platform.dentify.dashboard.interfaces.dto.AttendanceMetricsDto;
import com.platform.dentify.dashboard.interfaces.dto.DashboardAnalyticsDto;
import com.platform.dentify.dashboard.interfaces.dto.FinancialSummaryDto;
import com.platform.dentify.dashboard.interfaces.dto.InvoiceDto;
import com.platform.dentify.dashboard.interfaces.dto.ItemDto;
import com.platform.dentify.dashboard.interfaces.dto.SpecialtyRevenueDto;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.domain.model.queries.FindTop3LowStockItemsByCurrentUser;
import com.platform.dentify.inventory.domain.services.ItemQueryService;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.queries.GetAllInvoicesByUserIdQuery;
import com.platform.dentify.invoices.domain.model.queries.GetLast5Invoices;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.queries.GetAllAppointmentUserIdOrderByAppointmentDateDescQuery;
import com.platform.dentify.patientattention.domain.model.queries.GetTodayAppointmentsByUserIdQuery;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.domain.services.AppointmentQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardQueryServiceImpl implements DashboardQueryService {
    private final ItemQueryService inventoryQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final AppointmentQueryService appointmentQueryService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public DashboardQueryServiceImpl(ItemQueryService inventoryQueryService, InvoiceQueryService invoiceQueryService, AppointmentQueryService appointmentQueryService, AuthenticatedUserProvider authenticatedUserProvider) {
        this.inventoryQueryService = inventoryQueryService;
        this.invoiceQueryService = invoiceQueryService;
        this.appointmentQueryService = appointmentQueryService;
        this.authenticatedUserProvider = authenticatedUserProvider;

    }

    @Override
    public List<ItemDto> getLowStockItemsForDashboard() {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        return inventoryQueryService.handle(new FindTop3LowStockItemsByCurrentUser(userId))
                .stream()
                .map(item -> new ItemDto(item.getId(), item.getName(), item.getStockQuantity()))
                .toList();
    }

    @Override
    public List<InvoiceDto> getRecentPayments() {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        return invoiceQueryService.handle(new GetLast5Invoices(userId))
                .stream()
                .map(invoice -> new InvoiceDto(
                        invoice.getId(),
                        invoice.getAmount(),
                        invoice.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<AppointmentDto> getRecentAppointments() {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        return appointmentQueryService.handle(new GetTodayAppointmentsByUserIdQuery(userId))
                .stream()
                .map(appointment -> new AppointmentDto(
                        appointment.getId(),
                        appointment.getAppointmentDate(),
                        appointment.getReason(),
                        appointment.getDuration(),
                        appointment.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public DashboardAnalyticsDto getMonthlyAnalytics() {
        List<Invoice> invoices = invoiceQueryService.handle(new GetAllInvoicesByUserIdQuery());
        List<Appointment> appointments =
                appointmentQueryService.handle(new GetAllAppointmentUserIdOrderByAppointmentDateDescQuery());
        return computeMonthlyAnalytics(YearMonth.now(), invoices, appointments);
    }

    public DashboardAnalyticsDto computeMonthlyAnalytics(
            YearMonth month,
            List<Invoice> invoices,
            List<Appointment> appointments
    ) {
        LocalDateTime monthStart = month.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = month.atEndOfMonth().atTime(23, 59, 59, 999_999_999);

        List<Invoice> monthlyInvoices = invoices.stream()
                .filter(inv -> inv.getCreatedAt() != null
                        && !inv.getCreatedAt().isBefore(monthStart)
                        && !inv.getCreatedAt().isAfter(monthEnd))
                .toList();

        Map<String, double[]> specialtyAgg = new HashMap<>();
        double totalCollected = 0.0;
        for (Invoice inv : monthlyInvoices) {
            double amount = inv.getAmount() != null ? inv.getAmount() : 0.0;
            totalCollected += amount;

            String category = "GENERAL";
            if (inv.getAppointment() != null
                    && inv.getAppointment().getTreatment() != null
                    && inv.getAppointment().getTreatment().getCategory() != null) {
                category = inv.getAppointment().getTreatment().getCategory().name();
            }
            double[] bucket = specialtyAgg.computeIfAbsent(category, k -> new double[2]);
            bucket[0] += amount;
            bucket[1] += 1;
        }

        List<SpecialtyRevenueDto> revenueBySpecialty = specialtyAgg.entrySet().stream()
                .map(e -> new SpecialtyRevenueDto(
                        e.getKey(),
                        round2(e.getValue()[0]),
                        (long) e.getValue()[1]
                ))
                .sorted(Comparator.comparingDouble(SpecialtyRevenueDto::revenue).reversed())
                .toList();

        List<Appointment> monthlyAppointments = appointments.stream()
                .filter(a -> a.getAppointmentDate() != null
                        && !a.getAppointmentDate().isBefore(monthStart)
                        && !a.getAppointmentDate().isAfter(monthEnd))
                .toList();

        long completedCount = 0;
        long cancelledCount = 0;
        long noShowCount = 0;
        double pendingBalance = 0.0;

        for (Appointment appt : monthlyAppointments) {
            AppointmentStatus resolved = appt.resolveStatus();
            if (resolved == AppointmentStatus.COMPLETED) {
                completedCount++;
                // TODO(Integrante 2): sumar también invoice.getPendingBalance() cuando se integren pagos parciales
                if (appt.getInvoice() == null
                        && appt.getTreatment() != null
                        && appt.getTreatment().getPrice() != null) {
                    pendingBalance += appt.getTreatment().getPrice();
                }
            } else if (resolved == AppointmentStatus.CANCELLED) {
                cancelledCount++;
            } else if (resolved == AppointmentStatus.NO_SHOW) {
                noShowCount++;
            }
        }

        long closedTotal = completedCount + cancelledCount + noShowCount;
        double attendanceRate = closedTotal > 0
                ? Math.round((completedCount * 1000.0) / closedTotal) / 10.0
                : 0.0;

        AttendanceMetricsDto attendance = new AttendanceMetricsDto(
                monthlyAppointments.size(),
                completedCount,
                cancelledCount,
                noShowCount,
                attendanceRate
        );

        double totalExpected = totalCollected + pendingBalance;
        double collectionRate = totalExpected > 0
                ? Math.round((totalCollected * 1000.0) / totalExpected) / 10.0
                : 0.0;

        FinancialSummaryDto financials = new FinancialSummaryDto(
                round2(totalCollected),
                round2(pendingBalance),
                collectionRate
        );

        return new DashboardAnalyticsDto(
                month.toString(),
                revenueBySpecialty,
                attendance,
                financials
        );
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

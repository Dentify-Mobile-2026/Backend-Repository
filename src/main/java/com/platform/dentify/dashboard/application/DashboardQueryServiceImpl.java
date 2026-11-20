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
import com.platform.dentify.invoices.domain.model.queries.GetRecentCollectionsQuery;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceCollection;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.queries.GetAllAppointmentUserIdOrderByAppointmentDateDescQuery;
import com.platform.dentify.patientattention.domain.model.queries.GetTodayAppointmentsByUserIdQuery;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.domain.services.AppointmentQueryService;
import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardQueryServiceImpl implements DashboardQueryService {
    private static final int RECENT_PAYMENTS = 5;

    private final ItemQueryService inventoryQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final AppointmentQueryService appointmentQueryService;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final Clock clock;

    public DashboardQueryServiceImpl(ItemQueryService inventoryQueryService, InvoiceQueryService invoiceQueryService, AppointmentQueryService appointmentQueryService, AuthenticatedUserProvider authenticatedUserProvider) {
        this(inventoryQueryService, invoiceQueryService, appointmentQueryService, authenticatedUserProvider,
                Clock.system(BusinessTime.ZONE));
    }

    @Autowired
    public DashboardQueryServiceImpl(ItemQueryService inventoryQueryService, InvoiceQueryService invoiceQueryService, AppointmentQueryService appointmentQueryService, AuthenticatedUserProvider authenticatedUserProvider, Clock clock) {
        this.inventoryQueryService = inventoryQueryService;
        this.invoiceQueryService = invoiceQueryService;
        this.appointmentQueryService = appointmentQueryService;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.clock = clock;
    }

    @Override
    public List<ItemDto> getLowStockItemsForDashboard() {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        return inventoryQueryService.handle(new FindTop3LowStockItemsByCurrentUser(userId))
                .stream()
                .map(item -> new ItemDto(item.getId(), item.getName(), item.getStockQuantity()))
                .toList();
    }

    /**
     * Últimos cobros reales: cada abono por separado (o el comprobante histórico completo),
     * no el total de cada comprobante. {@code createdAt} es la fecha del cobro en hora de Lima.
     */
    @Override
    public List<InvoiceDto> getRecentPayments() {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        return invoiceQueryService.handle(new GetRecentCollectionsQuery(userId, RECENT_PAYMENTS))
                .stream()
                .map(collection -> new InvoiceDto(
                        collection.invoiceId(),
                        collection.amount().doubleValue(),
                        LocalDateTime.ofInstant(collection.collectedAt(), BusinessTime.ZONE)
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
        return computeMonthlyAnalytics(YearMonth.now(clock.withZone(BusinessTime.ZONE)), invoices, appointments);
    }

    /** Analítica del mes con corte en el instante actual o, si el mes ya terminó, al cierre del mes. */
    public DashboardAnalyticsDto computeMonthlyAnalytics(
            YearMonth month,
            List<Invoice> invoices,
            List<Appointment> appointments
    ) {
        Instant monthEndExclusive = month.plusMonths(1).atDay(1).atStartOfDay(BusinessTime.ZONE).toInstant();
        Instant now = clock.instant();
        Instant cutoff = now.isBefore(monthEndExclusive) ? now : monthEndExclusive.minusNanos(1);
        return computeMonthlyAnalytics(month, invoices, appointments, cutoff);
    }

    /**
     * Métricas del mes contable en America/Lima.
     * <ul>
     *   <li>{@code totalCollected}: caja del mes. Suma de los cobros cuya fecha real cae en el mes
     *   (cada abono por su {@code paidAt}; los comprobantes históricos por su fecha de emisión).</li>
     *   <li>{@code revenueBySpecialty}: esos mismos cobros por especialidad; {@code count} es el
     *   número de comprobantes distintos con cobros en el mes.</li>
     *   <li>{@code pendingBalance}: cuentas por cobrar al corte, de todos los meses. Saldo de los
     *   comprobantes emitidos hasta el corte, más el precio de las citas atendidas hasta el corte
     *   que aún no tenían comprobante. Las deudas de meses anteriores no desaparecen.</li>
     *   <li>{@code collectionRate}: cobranza de la cohorte del mes. Lo cobrado hasta el corte
     *   sobre lo generado en el mes (comprobantes emitidos en el mes + citas atendidas en el mes
     *   sin comprobante), para no mezclar la caja de un mes con deudas acumuladas.</li>
     * </ul>
     */
    public DashboardAnalyticsDto computeMonthlyAnalytics(
            YearMonth month,
            List<Invoice> invoices,
            List<Appointment> appointments,
            Instant cutoff
    ) {
        ZoneId zone = BusinessTime.ZONE;
        Instant monthStart = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        LocalDateTime localMonthStart = month.atDay(1).atStartOfDay();
        LocalDateTime localMonthEnd = month.atEndOfMonth().atTime(23, 59, 59, 999_999_999);

        // 1) Caja del mes, por fecha real de cobro.
        Map<String, BigDecimal> revenueByCategory = new HashMap<>();
        Map<String, Set<Invoice>> invoicesByCategory = new HashMap<>();
        BigDecimal totalCollected = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            String category = categoryOf(invoice);
            for (InvoiceCollection collection : invoice.getCollections()) {
                if (isWithin(collection.collectedAt(), monthStart, monthEnd)) {
                    totalCollected = totalCollected.add(collection.amount());
                    revenueByCategory.merge(category, collection.amount(), BigDecimal::add);
                    invoicesByCategory
                            .computeIfAbsent(category, key -> Collections.newSetFromMap(new IdentityHashMap<>()))
                            .add(invoice);
                }
            }
        }

        List<SpecialtyRevenueDto> revenueBySpecialty = revenueByCategory.entrySet().stream()
                .map(e -> new SpecialtyRevenueDto(
                        e.getKey(),
                        toDouble(e.getValue()),
                        invoicesByCategory.getOrDefault(e.getKey(), Set.of()).size()
                ))
                .sorted(Comparator.comparingDouble(SpecialtyRevenueDto::revenue).reversed())
                .toList();

        // 2) Asistencia del mes (hora local del consultorio).
        List<Appointment> monthlyAppointments = appointments.stream()
                .filter(a -> a.getAppointmentDate() != null
                        && !a.getAppointmentDate().isBefore(localMonthStart)
                        && !a.getAppointmentDate().isAfter(localMonthEnd))
                .toList();

        long completedCount = 0;
        long cancelledCount = 0;
        long noShowCount = 0;
        for (Appointment appt : monthlyAppointments) {
            AppointmentStatus resolved = appt.resolveStatus();
            if (resolved == AppointmentStatus.COMPLETED) {
                completedCount++;
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

        // 3) Cuentas por cobrar al corte (todos los meses).
        BigDecimal pendingBalance = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            if (issuedBy(invoice, cutoff)) {
                pendingBalance = pendingBalance.add(invoice.getPendingBalanceAsOf(cutoff).max(BigDecimal.ZERO));
            }
        }
        for (Appointment appt : appointments) {
            if (isUninvoicedAttention(appt, cutoff)) {
                pendingBalance = pendingBalance.add(BigDecimal.valueOf(appt.getTreatment().getPrice()));
            }
        }

        // 4) Cobranza de la cohorte del mes.
        BigDecimal generated = BigDecimal.ZERO;
        BigDecimal collectedFromCohort = BigDecimal.ZERO;
        for (Invoice invoice : invoices) {
            Instant issuedAt = invoice.getIssuedAt();
            if (issuedAt != null && isWithin(issuedAt, monthStart, monthEnd) && !issuedAt.isAfter(cutoff)) {
                generated = generated.add(invoice.getTotalAmount());
                collectedFromCohort = collectedFromCohort.add(invoice.getPaidAmountAsOf(cutoff));
            }
        }
        for (Appointment appt : monthlyAppointments) {
            if (isUninvoicedAttention(appt, cutoff)) {
                generated = generated.add(BigDecimal.valueOf(appt.getTreatment().getPrice()));
            }
        }
        double collectionRate = generated.signum() > 0
                ? collectedFromCohort.multiply(BigDecimal.valueOf(100)).divide(generated, 1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        FinancialSummaryDto financials = new FinancialSummaryDto(
                toDouble(totalCollected),
                toDouble(pendingBalance),
                collectionRate
        );

        return new DashboardAnalyticsDto(
                month.toString(),
                revenueBySpecialty,
                attendance,
                financials
        );
    }

    private static String categoryOf(Invoice invoice) {
        if (invoice.getAppointment() != null
                && invoice.getAppointment().getTreatment() != null
                && invoice.getAppointment().getTreatment().getCategory() != null) {
            return invoice.getAppointment().getTreatment().getCategory().name();
        }
        return "GENERAL";
    }

    private static boolean isWithin(Instant instant, Instant startInclusive, Instant endExclusive) {
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }

    private static boolean issuedBy(Invoice invoice, Instant cutoff) {
        Instant issuedAt = invoice.getIssuedAt();
        return issuedAt == null || !issuedAt.isAfter(cutoff);
    }

    /** Cita atendida hasta el corte, con precio, que en ese momento aún no tenía comprobante. */
    private static boolean isUninvoicedAttention(Appointment appt, Instant cutoff) {
        if (appt.resolveStatus() != AppointmentStatus.COMPLETED
                || appt.getAppointmentDate() == null
                || appt.getTreatment() == null
                || appt.getTreatment().getPrice() == null) {
            return false;
        }
        if (appt.getAppointmentDate().atZone(BusinessTime.ZONE).toInstant().isAfter(cutoff)) {
            return false;
        }
        return appt.getInvoice() == null || !issuedBy(appt.getInvoice(), cutoff);
    }

    private static double toDouble(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

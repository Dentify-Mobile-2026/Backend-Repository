package com.platform.dentify.dashboard;

import com.platform.dentify.dashboard.application.DashboardQueryServiceImpl;
import com.platform.dentify.dashboard.interfaces.dto.DashboardAnalyticsDto;
import com.platform.dentify.dashboard.interfaces.dto.InvoiceDto;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.domain.services.ItemQueryService;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.queries.GetRecentCollectionsQuery;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceCollection;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.domain.services.AppointmentQueryService;
import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dashboard con abonos (Sprint 2): la caja se cuenta por fecha real de pago en
 * America/Lima, el saldo por cobrar no pierde deudas de meses anteriores y la tasa de
 * cobranza se calcula sobre la cohorte del mes.
 */
class DashboardCollectionsByPaymentDateTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final Instant END_OF_NOVEMBER = Instant.parse("2026-12-01T04:59:59.999999999Z"); // 30/11 23:59:59 Lima
    private static final Instant MID_DECEMBER = Instant.parse("2026-12-15T17:00:00Z");

    private final InvoiceQueryService invoiceQueryService = mock(InvoiceQueryService.class);
    private final AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
    private final PaymentMethod cash = new PaymentMethod(new CreatePaymentMethodCommand("Efectivo", "Pago en efectivo"));
    private final Treatment endodontics = new Treatment(new CreateTreatmentCommand("Endodoncia molar", "", "ENDODONTICS", 350.0, 60));
    private final Treatment prevention = new Treatment(new CreateTreatmentCommand("Profilaxis", "", "PREVENTION", 120.0, 30));

    private DashboardQueryServiceImpl serviceAt(Instant now) {
        return new DashboardQueryServiceImpl(mock(ItemQueryService.class), invoiceQueryService,
                mock(AppointmentQueryService.class), userProvider, Clock.fixed(now, BusinessTime.ZONE));
    }

    private static Instant lima(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(BusinessTime.ZONE).toInstant();
    }

    private Appointment appointment(LocalDateTime localDate, Treatment treatment, AppointmentStatus status) {
        var appt = new Appointment(new CreateAppointmentCommand(localDate, "Tratamiento", LocalTime.of(1, 0), 1L));
        appt.setTreatment(treatment);
        appt.updateStatus(status, "");
        return appt;
    }

    /** Comprobante emitido en {@code issuedAt} sin abono inicial, vinculado a la cita. */
    private Invoice invoice(String total, Instant issuedAt, Appointment appt) {
        var invoice = new Invoice(new CreateInvoiceCommand(new BigDecimal(total), 1L, null, BigDecimal.ZERO));
        invoice.openBilling(BigDecimal.ZERO, null, issuedAt);
        ReflectionTestUtils.setField(invoice, "createdAt", LocalDateTime.ofInstant(issuedAt, ZoneId.systemDefault()));
        invoice.setAppointment(appt);
        if (appt != null) {
            appt.setInvoice(invoice);
        }
        return invoice;
    }

    private void pay(Invoice invoice, String amount, Instant paidAt) {
        invoice.registerPayment(new BigDecimal(amount), cash, paidAt, null, null);
    }

    @Test
    void installmentsAreCountedInTheMonthTheyWerePaid() {
        var appt = appointment(LocalDateTime.of(2026, 11, 10, 10, 0), endodontics, AppointmentStatus.COMPLETED);
        var invoice = invoice("1000.00", lima(2026, 11, 10, 11, 0), appt);
        pay(invoice, "200.00", lima(2026, 11, 15, 9, 0));
        pay(invoice, "800.00", lima(2026, 12, 5, 9, 0));
        var service = serviceAt(MID_DECEMBER);

        DashboardAnalyticsDto november = service.computeMonthlyAnalytics(NOVEMBER, List.of(invoice), List.of(appt), END_OF_NOVEMBER);
        assertEquals(200.0, november.financials().totalCollected());
        assertEquals(800.0, november.financials().pendingBalance());
        assertEquals(20.0, november.financials().collectionRate());
        assertEquals(200.0, november.revenueBySpecialty().get(0).revenue());
        assertEquals(1, november.revenueBySpecialty().get(0).count());

        DashboardAnalyticsDto december = service.computeMonthlyAnalytics(DECEMBER, List.of(invoice), List.of(appt), MID_DECEMBER);
        assertEquals(800.0, december.financials().totalCollected());
        assertEquals(0.0, december.financials().pendingBalance());
        assertEquals(0.0, december.financials().collectionRate()); // diciembre no generó comprobantes nuevos
        assertEquals("ENDODONTICS", december.revenueBySpecialty().get(0).category());
        assertEquals(800.0, december.revenueBySpecialty().get(0).revenue());
    }

    @Test
    void earlierDebtsDoNotDisappearInLaterMonths() {
        var appt = appointment(LocalDateTime.of(2026, 11, 3, 10, 0), endodontics, AppointmentStatus.COMPLETED);
        var invoice = invoice("950.00", lima(2026, 11, 3, 11, 0), appt);
        pay(invoice, "350.00", lima(2026, 11, 3, 11, 5));
        var service = serviceAt(MID_DECEMBER);

        var november = service.computeMonthlyAnalytics(NOVEMBER, List.of(invoice), List.of(appt), END_OF_NOVEMBER);
        assertEquals(350.0, november.financials().totalCollected());
        assertEquals(600.0, november.financials().pendingBalance());
        assertEquals(36.8, november.financials().collectionRate());

        var december = service.computeMonthlyAnalytics(DECEMBER, List.of(invoice), List.of(appt), MID_DECEMBER);
        assertEquals(0.0, december.financials().totalCollected());
        assertEquals(600.0, december.financials().pendingBalance());
    }

    @Test
    void paymentLateOnNovember30InLimaBelongsToNovember() {
        var invoice = invoice("100.00", lima(2026, 11, 30, 20, 0), null);
        Instant lateNight = Instant.parse("2026-12-01T04:30:00Z"); // 30/11 23:30 en Lima
        pay(invoice, "100.00", lateNight);
        var service = serviceAt(MID_DECEMBER);

        assertEquals(100.0, service.computeMonthlyAnalytics(NOVEMBER, List.of(invoice), List.of(), END_OF_NOVEMBER)
                .financials().totalCollected());
        assertEquals(0.0, service.computeMonthlyAnalytics(DECEMBER, List.of(invoice), List.of(), MID_DECEMBER)
                .financials().totalCollected());
    }

    @Test
    void attendedButUninvoicedAppointmentsFromEarlierMonthsStayReceivable() {
        var october = appointment(LocalDateTime.of(2026, 10, 20, 9, 0), prevention, AppointmentStatus.COMPLETED);
        var service = serviceAt(MID_DECEMBER);

        var november = service.computeMonthlyAnalytics(NOVEMBER, List.of(), List.of(october), END_OF_NOVEMBER);

        assertEquals(120.0, november.financials().pendingBalance());
        assertEquals(0.0, november.financials().collectionRate()); // no forma parte de la cohorte de noviembre
    }

    @Test
    void historicalInvoicesStillCountOnTheirIssueDate() {
        var legacy = new Invoice(new CreateInvoiceCommand(80.0, 1L, 1L));
        ReflectionTestUtils.setField(legacy, "createdAt",
                LocalDateTime.ofInstant(lima(2026, 11, 9, 16, 0), ZoneId.systemDefault()));
        var service = serviceAt(MID_DECEMBER);

        var november = service.computeMonthlyAnalytics(NOVEMBER, List.of(legacy), List.of(), END_OF_NOVEMBER);

        assertEquals(80.0, november.financials().totalCollected());
        assertEquals(0.0, november.financials().pendingBalance());
        assertEquals(100.0, november.financials().collectionRate());
    }

    @Test
    void defaultCutoffIsTheEndOfAPastMonth() {
        var appt = appointment(LocalDateTime.of(2026, 11, 10, 10, 0), endodontics, AppointmentStatus.COMPLETED);
        var invoice = invoice("1000.00", lima(2026, 11, 10, 11, 0), appt);
        pay(invoice, "200.00", lima(2026, 11, 15, 9, 0));
        pay(invoice, "800.00", lima(2026, 12, 5, 9, 0));

        // Consultado en diciembre, noviembre se corta el 30/11 a medianoche de Lima.
        var november = serviceAt(MID_DECEMBER).computeMonthlyAnalytics(NOVEMBER, List.of(invoice), List.of(appt));

        assertEquals(200.0, november.financials().totalCollected());
        assertEquals(800.0, november.financials().pendingBalance());
    }

    @Test
    void recentPaymentsAreIndividualCollectionsInLimaTime() {
        when(userProvider.getCurrentUserId()).thenReturn(7L);
        when(invoiceQueryService.handle(any(GetRecentCollectionsQuery.class))).thenReturn(List.of(
                new InvoiceCollection(30L, new BigDecimal("600.00"), Instant.parse("2026-12-01T04:30:00Z")),
                new InvoiceCollection(30L, new BigDecimal("350.00"), Instant.parse("2026-11-03T16:05:00Z"))));

        List<InvoiceDto> recent = serviceAt(MID_DECEMBER).getRecentPayments();

        assertEquals(2, recent.size());
        assertEquals(600.0, recent.get(0).amount());
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 30), recent.get(0).createdAt());
        assertEquals(30L, recent.get(1).id());
    }
}

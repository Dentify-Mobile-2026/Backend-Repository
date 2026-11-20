package com.platform.dentify.invoices.application.internal.commandservices;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.invoices.application.external.AppointmentACL;
import com.platform.dentify.invoices.domain.exceptions.AppointmentNotFoundException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceAlreadyExistsException;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.services.InvoiceCommandService;
import com.platform.dentify.invoices.infrastructure.repositories.InvoiceRepository;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

@Service
public class InvoiceCommandServiceImpl implements InvoiceCommandService {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final InvoiceRepository invoiceRepository;
    private final AppointmentACL appointmentACL;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public InvoiceCommandServiceImpl(AuthenticatedUserProvider authenticatedUserProvider,
                                     InvoiceRepository invoiceRepository,
                                     AppointmentACL appointmentACL,
                                     PaymentMethodRepository paymentMethodRepository,
                                     PlatformTransactionManager transactionManager) {
        this(authenticatedUserProvider, invoiceRepository, appointmentACL, paymentMethodRepository,
                transactionManager, Clock.systemUTC());
    }

    @Autowired
    public InvoiceCommandServiceImpl(AuthenticatedUserProvider authenticatedUserProvider,
                                     InvoiceRepository invoiceRepository,
                                     AppointmentACL appointmentACL,
                                     PaymentMethodRepository paymentMethodRepository,
                                     PlatformTransactionManager transactionManager,
                                     Clock clock) {
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.invoiceRepository = invoiceRepository;
        this.appointmentACL = appointmentACL;
        this.paymentMethodRepository = paymentMethodRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Crea el comprobante en una única transacción. No modifica el estado clínico de la cita.
     * <p>
     * Si dos solicitudes crean a la vez el comprobante de la misma cita, la segunda falla en
     * la restricción UNIQUE(appointment_id). Esa transacción ya está revertida cuando se
     * captura la excepción; entonces se consulta, fuera de ella, si el comprobante existe:
     * en ese caso se responde como duplicado (409) y cualquier otra violación se propaga.
     */
    @Override
    public Optional<Invoice> handle(CreateInvoiceCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        try {
            return Optional.ofNullable(transactionTemplate.execute(status -> create(command, userId)));
        } catch (DataIntegrityViolationException ex) {
            if (invoiceRepository.findInvoiceByAppointmentId(command.appointmentId()).isPresent()) {
                throw new InvoiceAlreadyExistsException(command.appointmentId());
            }
            throw ex;
        }
    }

    private Invoice create(CreateInvoiceCommand command, Long userId) {
        if (appointmentACL.findByIdAndUserId(command.appointmentId(), userId).isEmpty()) {
            throw new AppointmentNotFoundException(command.appointmentId());
        }

        // Con abono inicial 0 no hay cobro y el método de pago no se exige ni se registra.
        PaymentMethod paymentMethod = null;
        if (command.initialPayment().signum() > 0) {
            paymentMethod = paymentMethodRepository.findPaymentMethodById(command.paymentMethodId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El método de pago " + command.paymentMethodId() + " no existe"));
        }

        if (invoiceRepository.findInvoiceByAppointmentId(command.appointmentId()).isPresent()) {
            throw new InvoiceAlreadyExistsException(command.appointmentId());
        }

        Invoice invoice = new Invoice(command);
        invoice.setAppointment(entityManager.getReference(Appointment.class, command.appointmentId()));
        // El abono inicial se registra como InvoicePayment: es el que cuenta en caja y en saldos.
        invoice.openBilling(command.initialPayment(), paymentMethod, clock.instant());
        return invoiceRepository.saveAndFlush(invoice);
    }
}

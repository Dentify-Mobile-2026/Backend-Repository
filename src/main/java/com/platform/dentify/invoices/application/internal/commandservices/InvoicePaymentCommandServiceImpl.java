package com.platform.dentify.invoices.application.internal.commandservices;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.invoices.domain.model.commands.RegisterInvoicePaymentCommand;
import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import com.platform.dentify.invoices.domain.model.valueobjects.PaymentRegistrationResult;
import com.platform.dentify.invoices.domain.services.InvoicePaymentCommandService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Registro idempotente de abonos. No es transaccional a propósito: la transacción vive en
 * {@link InvoicePaymentTransactionalHandler}. Si dos solicitudes con el mismo requestId
 * llegan a insertar a la vez, la segunda falla en {@code uk_invoice_payment_request_id};
 * esa transacción ya está revertida cuando la excepción llega aquí y la recuperación se hace
 * en una transacción nueva. Cualquier otra violación de integridad se propaga sin
 * interpretarse como repetición.
 */
@Service
public class InvoicePaymentCommandServiceImpl implements InvoicePaymentCommandService {

    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final InvoicePaymentTransactionalHandler transactionalHandler;

    public InvoicePaymentCommandServiceImpl(AuthenticatedUserProvider authenticatedUserProvider,
                                            InvoicePaymentTransactionalHandler transactionalHandler) {
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.transactionalHandler = transactionalHandler;
    }

    @Override
    public PaymentRegistrationResult handle(RegisterInvoicePaymentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        try {
            return transactionalHandler.register(command, userId);
        } catch (DataIntegrityViolationException ex) {
            if (!violatesConstraint(ex, InvoicePayment.REQUEST_ID_CONSTRAINT)) {
                throw ex;
            }
            return transactionalHandler.replayAfterDuplicateRequest(command, userId);
        }
    }

    /** Busca en la cadena de causas la restricción violada informada por Hibernate. */
    static boolean violatesConstraint(Throwable error, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}

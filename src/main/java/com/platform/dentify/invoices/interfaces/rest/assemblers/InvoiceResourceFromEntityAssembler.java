package com.platform.dentify.invoices.interfaces.rest.assemblers;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoiceResource;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;

import java.util.List;

public class InvoiceResourceFromEntityAssembler {
    public static InvoiceResource toResourceFromEntity(Invoice invoice) {
        Appointment appointment = invoice.getAppointment();
        Patient patient = appointment != null ? appointment.getPatient() : null;
        List<PaymentMethod> methods = invoice.getPaymentMethodsUsed();
        // Un único método identifica el cobro; con varios, cada abono lleva el suyo.
        PaymentMethod singleMethod = methods.size() == 1 ? methods.get(0) : null;

        return new InvoiceResource(
                invoice.getId(),
                appointment != null ? appointment.getId() : null,
                patient != null && patient.getName() != null ? patient.getName().FullName() : null,
                patient != null && patient.getDni() != null ? patient.getDni().dni() : null,
                patient != null && patient.getEmail() != null ? patient.getEmail().address() : null,
                invoice.getAmount(),
                invoice.getCreatedAt(),
                invoice.getTotalAmount(),
                singleMethod != null ? singleMethod.getId() : null,
                singleMethod != null ? singleMethod.getName() : null,
                invoice.getPaidAmount(),
                invoice.getPendingBalance(),
                invoice.getStatus().name(),
                invoice.isLegacySettled(),
                invoice.getPayments().size(),
                methods.stream().map(PaymentMethod::getName).toList()
        );
    }
}

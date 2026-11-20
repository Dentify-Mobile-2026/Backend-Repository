package com.platform.dentify.invoices.interfaces.rest.assemblers;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoiceResource;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;

public class InvoiceResourceFromEntityAssembler {
    public static InvoiceResource toResourceFromEntity(Invoice invoice) {
        Appointment appointment = invoice.getAppointment();
        Patient patient = appointment != null ? appointment.getPatient() : null;
        var paymentMethod = invoice.getPaymentMethod();

        return new InvoiceResource(
                invoice.getId(),
                appointment != null ? appointment.getId() : null,
                patient != null && patient.getName() != null ? patient.getName().FullName() : null,
                patient != null && patient.getDni() != null ? patient.getDni().dni() : null,
                patient != null && patient.getEmail() != null ? patient.getEmail().address() : null,
                invoice.getAmount(),
                invoice.getCreatedAt(),
                invoice.getTotalAmount(),
                paymentMethod != null ? paymentMethod.getId() : null,
                paymentMethod != null ? paymentMethod.getName() : null
        );
    }
}

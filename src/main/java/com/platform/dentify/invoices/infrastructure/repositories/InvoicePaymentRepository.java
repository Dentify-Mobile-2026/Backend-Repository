package com.platform.dentify.invoices.infrastructure.repositories;

import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    Optional<InvoicePayment> findByRequestId(String requestId);

    List<InvoicePayment> findByInvoice_IdOrderByPaidAtAscIdAsc(Long invoiceId);

    List<InvoicePayment> findTop5ByInvoice_Appointment_Patient_User_IdOrderByPaidAtDesc(Long userId);
}

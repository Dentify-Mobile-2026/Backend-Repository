package com.platform.dentify.invoices.infrastructure.repositories;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findInvoiceByAppointmentId(Long appointmentId);

    List<Invoice> findTop5ByAppointment_Patient_User_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"payments", "payments.paymentMethod"})
    List<Invoice> findByAppointment_Patient_User_Id(Long userId);

    Optional<Invoice> findByIdAndAppointment_Patient_User_Id(Long invoiceId, Long userId);

    /** Comprobantes históricos (sin abonos) más recientes del usuario. */
    List<Invoice> findTop5ByStatusIsNullAndAppointment_Patient_User_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Carga el comprobante del usuario con bloqueo de escritura ({@code SELECT … FOR UPDATE}).
     * Serializa los abonos concurrentes sobre un mismo comprobante: el segundo espera a que
     * el primero confirme y ve su saldo actualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :invoiceId and i.appointment.patient.user.id = :userId")
    Optional<Invoice> findByIdAndOwnerForUpdate(@Param("invoiceId") Long invoiceId, @Param("userId") Long userId);
}

package com.platform.dentify.invoices.infrastructure.repositories;

import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    Optional<PaymentMethod> findPaymentMethodById(Long paymentMethodId);

    boolean existsByNameIgnoreCase(String name);

    List<PaymentMethod> findAllByOrderByIdAsc();
}

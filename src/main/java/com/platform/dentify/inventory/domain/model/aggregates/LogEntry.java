package com.platform.dentify.inventory.domain.model.aggregates;

import com.platform.dentify.inventory.domain.model.commands.CreateLogEntryCommand;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class LogEntry extends AuditableAbstractAggregateRoot<LogEntry> {

    @ManyToOne
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Min(0)
    @Column(nullable = false)
    private int consumedQuantity;

    @Min(0)
    @Column(nullable = false)
    private int previousStock;

    @Min(0)
    @Column(nullable = false)
    private int newStock;

    @Column(nullable = false, length = 255)
    private String reason = "Actualización de inventario";

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = true)
    private Invoice invoice;

    public LogEntry() {}

    public LogEntry(CreateLogEntryCommand command) {
        this.consumedQuantity = command.consumedQuantity();
        this.reason = "Consumo registrado";
    }

    public LogEntry(Item item, int previousStock, int newStock, String reason) {
        this.item = item;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.consumedQuantity = Math.max(0, previousStock - newStock);
        this.reason = (reason != null && !reason.isBlank()) ? reason.trim() : "Actualización de inventario";
    }
}

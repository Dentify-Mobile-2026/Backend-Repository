package com.platform.dentify.inventory.application.internal.commandservices;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.domain.model.aggregates.LogEntry;
import com.platform.dentify.inventory.domain.model.commands.CreateLogEntryCommand;
import com.platform.dentify.inventory.domain.services.LogEntryCommandService;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.ItemRepository;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.LogEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class LogEntryCommandServiceImpl implements LogEntryCommandService {

    private final LogEntryRepository logEntryRepository;
    private final ItemRepository itemRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public LogEntryCommandServiceImpl(LogEntryRepository logEntryRepository,
                                      ItemRepository itemRepository,
                                      AuthenticatedUserProvider authenticatedUserProvider) {
        this.logEntryRepository = logEntryRepository;
        this.itemRepository = itemRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    @Transactional
    public Long handle(CreateLogEntryCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var itemOptional = itemRepository.findByIdAndUser_Id(command.itemId(), userId);

        if(itemOptional.isEmpty()) {
            throw new IllegalArgumentException("Item not found");
        }

        var item = itemOptional.get();
        int previousStock = item.getStockQuantity();

        if(command.consumedQuantity() > previousStock) {
            throw new IllegalArgumentException("Quantity exceeded");
        }

        int newStock = previousStock - command.consumedQuantity();
        var logEntry = new LogEntry(command);
        logEntry.setItem(item);
        logEntry.setPreviousStock(previousStock);
        logEntry.setNewStock(newStock);
        logEntry.setReason("Consumo registrado");

        item.updateStockQuantity(command.consumedQuantity());
        itemRepository.save(item);

        try {
            logEntryRepository.save(logEntry);
        } catch(RuntimeException exception) {
            throw new IllegalArgumentException("An error occurred while saving log entry" + exception.getMessage());
        }

        return logEntry.getId();
    }

}

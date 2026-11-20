package com.platform.dentify.inventory.application.internal.commandservices;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.domain.model.aggregates.Item;
import com.platform.dentify.inventory.domain.model.aggregates.LogEntry;
import com.platform.dentify.inventory.domain.model.commands.CreateItemCommand;
import com.platform.dentify.inventory.domain.model.commands.DeleteItemCommand;
import com.platform.dentify.inventory.domain.model.commands.UpdateItemCommand;
import com.platform.dentify.inventory.domain.model.valueobjects.ItemCategory;
import com.platform.dentify.inventory.domain.services.ItemCommandService;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.ItemRepository;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.LogEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ItemCommandServiceImpl implements ItemCommandService {

    private final ItemRepository itemRepository;
    private final LogEntryRepository logEntryRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserRepository userRepository;

    public ItemCommandServiceImpl(ItemRepository itemRepository,
                                  LogEntryRepository logEntryRepository,
                                  AuthenticatedUserProvider authenticatedUserProvider,
                                  UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.logEntryRepository = logEntryRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Long handle(CreateItemCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        if(itemRepository.existsByNameAndUser_Id(command.name(), userId)) {
            throw new IllegalArgumentException("Item name already exists");
        }

        try {
            ItemCategory.valueOf(command.category().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid category value: " + command.category());
        }

        var item = new Item(command);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        item.setUser(user);

        try {
            Item savedItem = itemRepository.save(item);
            logEntryRepository.save(new LogEntry(savedItem, 0, savedItem.getStockQuantity(), "Stock inicial"));
            return savedItem.getId();
        } catch(RuntimeException exception) {
            throw new IllegalArgumentException("An error occurred while saving item" + exception.getMessage());
        }
    }

    @Override
    @Transactional
    public Optional<Item> handle(UpdateItemCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        var itemOptional = itemRepository.findByIdAndUser_Id(command.id(), userId);

        if(itemOptional.isEmpty()) {
            throw new IllegalArgumentException("Item not found");
        }

        Item existingItem = itemOptional.get();
        int previousStock = existingItem.getStockQuantity();
        existingItem.update(command);

        try {
            var updatedItem = itemRepository.save(existingItem);
            int newStock = updatedItem.getStockQuantity();
            if (previousStock != newStock) {
                String reason = (command.reason() != null && !command.reason().isBlank())
                        ? command.reason().trim()
                        : (newStock > previousStock ? "Reposición de stock" : "Ajuste / salida de stock");
                logEntryRepository.save(new LogEntry(updatedItem, previousStock, newStock, reason));
            }
            return Optional.of(updatedItem);
        } catch(RuntimeException e) {
            throw new IllegalArgumentException("An error occurred while updating item" + e.getMessage());
        }

    }

    @Override
    public void handle(DeleteItemCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var item = itemRepository.findByIdAndUser_Id(command.id(), userId );

        if(item.isEmpty()) {
            throw new IllegalArgumentException("Item not found");
        }

        try {
            itemRepository.deleteById(item.get().getId());
        } catch(RuntimeException e) {
            throw new IllegalArgumentException("An error occurred while deleting item" + e.getMessage());
        }
    }
}

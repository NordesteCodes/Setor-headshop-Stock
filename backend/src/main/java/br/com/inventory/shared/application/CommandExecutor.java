package br.com.inventory.shared.application;
import java.util.UUID;
import java.util.function.Supplier;
public interface CommandExecutor {
    UUID execute(String scope, UUID key, Object payload, UUID actor, Supplier<UUID> action);
}

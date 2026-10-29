package net.onelitefeather.skygridng.adapters.command.handler;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double for {@link CommandSource}: records every message it receives, in order, instead of
 * mocking a Bukkit {@code CommandSender}. All five migrated handler classes only ever depend on
 * this neutral interface, never on Bukkit, so no MockBukkit is needed for these unit tests.
 */
public final class RecordingCommandSource implements CommandSource {

    private final List<Component> messages = new ArrayList<>();
    private boolean granted = true;

    /** Defaults to a player sender; set to {@code Optional.empty()} to simulate console. */
    private Optional<UUID> playerId = Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Override
    public void sendMessage(Component message) {
        messages.add(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        // Permission enforcement is Cloud's @Permission metadata, read at dispatch time, not code
        // inside the handler methods; these unit tests exercise the methods directly, so this is
        // never consulted by the production code under test. Defaulting to true keeps this double
        // simple for any future handler that might read it directly.
        return granted;
    }

    /** Makes every permission check fail, to simulate a sender without {@code skygridng.admin}. */
    public void withoutPermissions() {
        this.granted = false;
    }

    @Override
    public Locale locale() {
        return Locale.US;
    }

    @Override
    public Optional<UUID> playerId() {
        return playerId;
    }

    @Override
    public Optional<UUID> resolvePlayerId(String name) {
        // Mirrors PaperCommandSource: a name always resolves to some id, deterministic per name so
        // tests can assert against it without needing a real Bukkit player registry.
        return Optional.of(UUID.nameUUIDFromBytes(name.getBytes()));
    }

    public void asConsole() {
        this.playerId = Optional.empty();
    }

    public UUID selfId() {
        return playerId.orElseThrow();
    }

    public List<Component> messages() {
        return messages;
    }
}

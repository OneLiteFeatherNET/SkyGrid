package net.onelitefeather.skygridng.adapters.command.handler;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for {@link CommandSource}: records every message it receives, in order, instead of
 * mocking a Bukkit {@code CommandSender}. All five migrated handler classes only ever depend on
 * this neutral interface, never on Bukkit, so no MockBukkit is needed for these unit tests.
 */
final class RecordingCommandSource implements CommandSource {

    private final List<Component> messages = new ArrayList<>();

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
        return true;
    }

    List<Component> messages() {
        return messages;
    }
}

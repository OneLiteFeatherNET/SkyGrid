package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written, in-memory {@link CommandSource} test double, in the same spirit as
 * {@code FakeRegionWriter} in {@code adapters.generator} and {@code FakeGroundReader} in
 * {@code adapters.spawn}: this module is framework-free by architecture rule (no Bukkit, no
 * MockBukkit), so a plain fake is easier to reason about than stubbing a mock's defaults in every
 * test.
 *
 * <p>Every {@link #sendMessage} call is recorded, in order, so a test can assert on message count
 * and content via {@link #sent()}. {@link #hasPermission} defaults to {@code true} and can be
 * flipped with {@link #permission(boolean)}; none of the current {@code SkyGridCommands} subcommand
 * methods call it themselves (Cloud's own permission gate handles that outside the method body), but
 * the fake still implements the full {@link CommandSource} contract.
 */
final class FakeCommandSource implements CommandSource {

    private final List<Component> sent = new ArrayList<>();
    private boolean permission = true;

    @Override
    public void sendMessage(Component message) {
        sent.add(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.permission;
    }

    /** Sets what {@link #hasPermission} reports from now on. */
    void permission(boolean permission) {
        this.permission = permission;
    }

    /** Every message sent so far, in call order. */
    List<Component> sent() {
        return List.copyOf(sent);
    }
}

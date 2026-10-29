package net.onelitefeather.skygridng.api.model;

/**
 * A config-defined, player-facing reward granted on completing a {@link Task}.
 *
 * A sealed interface, not an open one: the reward-granting adapter (built separately, outside
 * {@code api}/{@code ports}/{@code core}) is expected to consume this with an exhaustive
 * {@code switch} over the three permitted record shapes, no {@code default} branch, so adding a
 * new reward kind here is a compile error at every consumer until it is handled.
 */
public sealed interface Reward {

    /** Grants {@code quantity} of {@code item} to the player. */
    record ItemGrant(ItemId item, int quantity) implements Reward {
        public ItemGrant {
            if (item == null) {
                throw new IllegalArgumentException("ItemGrant.item must not be null");
            }
            if (quantity < 1) {
                throw new IllegalArgumentException("ItemGrant.quantity must be at least 1");
            }
        }
    }

    /**
     * Dispatches {@code command} as a server console command. {@code command} must contain the
     * literal placeholder {@code {player}}, substituted by the adapter with the receiving player's
     * name at grant time.
     */
    record CommandDispatch(String command) implements Reward {
        public CommandDispatch {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("CommandDispatch.command must not be blank");
            }
            if (!command.contains("{player}")) {
                throw new IllegalArgumentException("CommandDispatch.command must contain the {player} placeholder");
            }
        }
    }

    /**
     * Sends {@code text} to the player. {@code text} is admin-authored MiniMessage source text, not
     * a translation key, so it is rendered as-is by the adapter rather than looked up in the
     * i18n bundles.
     */
    record PlayerMessage(String text) implements Reward {
        public PlayerMessage {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("PlayerMessage.text must not be blank");
            }
        }
    }
}

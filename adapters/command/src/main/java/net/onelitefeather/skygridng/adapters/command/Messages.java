package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Builds translatable command messages. Keys resolve against the registered translation bundles,
 * so the viewer sees the text in their own locale. Arguments are passed as components and fill the
 * {0}, {1}, ... placeholders in the bundle entries.
 *
 * Public (class and every factory method): stage 2's Cloud annotated subcommand handlers live in
 * the sibling {@code net.onelitefeather.skygridng.adapters.command.handler} package, a different
 * package from this one, so package-private access would not compile there. Only the visibility
 * was widened; no key, argument, or message text changed.
 */
public final class Messages {

    private Messages() {
    }

    /** Every reply carries this brand prefix, followed by a space, ahead of the message body. */
    private static Component prefix() {
        return Component.translatable("skygridng.command.prefix");
    }

    private static Component key(String key) {
        return prefix().append(Component.space()).append(keyNoPrefix(key));
    }

    private static Component key(String key, Object... args) {
        return prefix().append(Component.space()).append(keyNoPrefix(key, args));
    }

    /** Unprefixed lookup, for continuation lines that are not a repeated brand line. */
    private static Component keyNoPrefix(String key) {
        return Component.translatable("skygridng.command." + key);
    }

    private static Component keyNoPrefix(String key, Object... args) {
        Component[] components = new Component[args.length];
        for (int i = 0; i < args.length; i++) {
            components[i] = Component.text(String.valueOf(args[i]));
        }
        return Component.translatable("skygridng.command." + key, components);
    }

    public static Component noPermission() {
        return key("no_permission");
    }

    /**
     * Sent as separate lines (one {@code sendMessage} call each), not one newline-joined block.
     * Only the header carries the brand prefix; the remaining lines are a visual continuation of
     * it, not a repeated brand line.
     */
    public static List<Component> help() {
        return List.of(
                key("help.header"),
                keyNoPrefix("help.subcommands"),
                keyNoPrefix("help.algorithm_skyweight"),
                keyNoPrefix("help.reload_save_undo_challenge"),
                keyNoPrefix("help.footer"));
    }

    public static Component usage(String usage) {
        return key("usage", usage);
    }

    public static Component notANumber(String raw) {
        return key("not_a_number", raw);
    }

    public static Component weightFail(String raw) {
        return key("weight.fail", raw);
    }

    public static Component invalidMaterial(String raw) {
        return key("skyweight.invalid_material", raw);
    }

    public static Component reloadConfirm() {
        return key("reload.confirm");
    }

    public static Component reloaded() {
        return key("reloaded");
    }

    public static Component saved() {
        return key("saved");
    }

    public static Component undoDone() {
        return key("undo.done");
    }

    public static Component undoEmpty() {
        return key("undo.empty");
    }

    public static Component algorithmCurrent(String id) {
        return key("algorithm.current", id);
    }

    public static Component algorithmSet(String id) {
        return key("algorithm.set", id);
    }

    public static Component algorithmUnknown(String id) {
        return key("algorithm.unknown", id);
    }

    public static Component skyWeightSet(String material, double value) {
        return key("skyweight.set", material, value);
    }

    public static Component presetList(String names, String active) {
        return key("preset.list", names, active);
    }

    public static Component presetSet(String name) {
        return key("preset.set", name);
    }

    public static Component presetUnknown(String name) {
        return key("preset.unknown", name);
    }

    public static Component worldList(String worlds) {
        return key("world.list", worlds);
    }

    public static Component challengeListEmpty() {
        return key("challenge.list_empty");
    }

    public static Component challengeListEntry(String id, boolean active) {
        return key("challenge.list_entry", id, active);
    }

    public static Component challengeUnknown(String id) {
        return key("challenge.unknown", id);
    }

    public static Component challengeToggled(String id, boolean active) {
        return key("challenge.toggled", id, active);
    }

    public static Component challengeTierSelf(int tier) {
        return key("challenge.tier.self", tier);
    }

    public static Component challengeTierOther(String player, int tier) {
        return key("challenge.tier.other", player, tier);
    }

    public static Component challengeTierConsole() {
        return key("challenge.tier.console");
    }

    public static Component challengeTierReset() {
        return key("challenge.tier.reset");
    }

    /** Shown instead of resetting when {@code /skygrid challenge tier reset} is missing {@code confirm}. */
    public static Component challengeTierResetConfirm() {
        return key("challenge.tier.reset.confirm");
    }
}

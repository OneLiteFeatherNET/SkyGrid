package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Builds translatable command messages. Keys resolve against the registered translation bundles,
 * so the viewer sees the text in their own locale. Arguments are passed as components and fill the
 * {0}, {1}, ... placeholders in the bundle entries.
 */
final class Messages {

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

    static Component noPermission() {
        return key("no_permission");
    }

    /**
     * Sent as separate lines (one {@code sendMessage} call each), not one newline-joined block.
     * Only the header carries the brand prefix; the remaining lines are a visual continuation of
     * it, not a repeated brand line.
     */
    static List<Component> help() {
        return List.of(
                key("help.header"),
                keyNoPrefix("help.subcommands"),
                keyNoPrefix("help.algorithm_skyweight"),
                keyNoPrefix("help.reload_save_undo_challenge"),
                keyNoPrefix("help.footer"));
    }

    static Component usage(String usage) {
        return key("usage", usage);
    }

    static Component notANumber(String raw) {
        return key("not_a_number", raw);
    }

    static Component weightFail(String raw) {
        return key("weight.fail", raw);
    }

    static Component invalidMaterial(String raw) {
        return key("skyweight.invalid_material", raw);
    }

    static Component reloadConfirm() {
        return key("reload.confirm");
    }

    static Component reloaded() {
        return key("reloaded");
    }

    static Component saved() {
        return key("saved");
    }

    static Component undoDone() {
        return key("undo.done");
    }

    static Component undoEmpty() {
        return key("undo.empty");
    }

    static Component algorithmCurrent(String id) {
        return key("algorithm.current", id);
    }

    static Component algorithmSet(String id) {
        return key("algorithm.set", id);
    }

    static Component algorithmUnknown(String id) {
        return key("algorithm.unknown", id);
    }

    static Component skyWeightSet(String material, double value) {
        return key("skyweight.set", material, value);
    }

    static Component presetList(String names, String active) {
        return key("preset.list", names, active);
    }

    static Component presetSet(String name) {
        return key("preset.set", name);
    }

    static Component presetUnknown(String name) {
        return key("preset.unknown", name);
    }

    static Component worldList(String worlds) {
        return key("world.list", worlds);
    }

    static Component challengeUnavailable() {
        return key("challenge.unavailable");
    }

    static Component challengeListEmpty() {
        return key("challenge.list_empty");
    }

    static Component challengeListEntry(String id, boolean active) {
        return key("challenge.list_entry", id, active);
    }

    static Component challengeUnknown(String id) {
        return key("challenge.unknown", id);
    }

    static Component challengeToggled(String id, boolean active) {
        return key("challenge.toggled", id, active);
    }
}

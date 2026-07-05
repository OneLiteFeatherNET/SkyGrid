package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;

/**
 * Builds translatable command messages. Keys resolve against the registered translation bundles,
 * so the viewer sees the text in their own locale. Arguments are passed as components and fill the
 * {0}, {1}, ... placeholders in the bundle entries.
 */
final class Messages {

    private Messages() {
    }

    private static Component key(String key) {
        return Component.translatable("skygridng.command." + key);
    }

    private static Component key(String key, Object... args) {
        Component[] components = new Component[args.length];
        for (int i = 0; i < args.length; i++) {
            components[i] = Component.text(String.valueOf(args[i]));
        }
        return Component.translatable("skygridng.command." + key, components);
    }

    static Component noPermission() {
        return key("no_permission");
    }

    static Component help() {
        return key("help");
    }

    static Component usage(String usage) {
        return key("usage", usage);
    }

    static Component notANumber() {
        return key("not_a_number");
    }

    static Component weightFail() {
        return key("weight.fail");
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

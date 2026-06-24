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

    static Component list(String ids) {
        return key("list", ids);
    }

    static Component info(String id, double weight, boolean neutral, String surface, boolean liquid, int features) {
        return key("info", id, weight, neutral, surface, liquid, features);
    }

    static Component unknownTile(String id) {
        return key("unknown_tile", id);
    }

    static Component usage(String usage) {
        return key("usage", usage);
    }

    static Component notANumber() {
        return key("not_a_number");
    }

    static Component weightSet(String tile, double value) {
        return key("weight.set", tile, value);
    }

    static Component weightFail() {
        return key("weight.fail");
    }

    static Component allowed(String a, String b) {
        return key("allowed", a, b);
    }

    static Component removed(String a, String b) {
        return key("removed", a, b);
    }

    static Component unknownTilePair() {
        return key("unknown_pair");
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
}

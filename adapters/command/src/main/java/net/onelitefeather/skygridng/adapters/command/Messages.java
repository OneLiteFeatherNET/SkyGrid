package net.onelitefeather.skygridng.adapters.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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

    /**
     * An absolute date and time in the viewer's locale, in UTC. The server runs on one clock, so the zone is fixed and
     * only the wording follows the viewer.
     */
    public static String seasonDate(Instant instant, Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(ZoneOffset.UTC).format(instant);
    }

    /**
     * What a season end deletes and keeps, sent as three lines: the deleted list, the kept list, and the viewer's own
     * pending mailbox count. The mailbox count is always 0 until the economy exists.
     */
    public static List<Component> seasonResetLines(int pendingMailbox) {
        return List.of(
                keyNoPrefix("season.reset.deleted"),
                keyNoPrefix("season.reset.kept"),
                keyNoPrefix("season.mailbox", pendingMailbox));
    }

    public static List<Component> seasonInfo(String id, String name, String end, int pendingMailbox) {
        List<Component> lines = new ArrayList<>();
        lines.add(prefix().append(Component.space()).append(Component.translatable("skygridng.command.season.info",
                Component.text(id), MINI_MESSAGE.deserialize(name), Component.text(end))));
        lines.addAll(seasonResetLines(pendingMailbox));
        return List.copyOf(lines);
    }

    /** The season end announcement for the viewer, with the reset lines, sent as separate lines. */
    public static List<Component> seasonAnnouncementUpcoming(String id, String endsOn, int pendingMailbox) {
        List<Component> lines = new ArrayList<>();
        lines.add(key("season.announce.upcoming", id, endsOn));
        lines.addAll(seasonResetLines(pendingMailbox));
        return List.copyOf(lines);
    }

    /** The season start announcement for the viewer, with the reset lines, sent as separate lines. */
    public static List<Component> seasonAnnouncementStarted(String id, int pendingMailbox) {
        List<Component> lines = new ArrayList<>();
        lines.add(key("season.announce.started", id));
        lines.addAll(seasonResetLines(pendingMailbox));
        return List.copyOf(lines);
    }

    public static Component seasonAnnouncementPostponed(String id, String endsOn) {
        return key("season.announce.postponed", id, endsOn);
    }

    /** The chat line for a player who joins in the last seven days, with the reset lines. */
    public static List<Component> seasonFinalWeekLine(String id, String endsOn, int pendingMailbox) {
        List<Component> lines = new ArrayList<>();
        lines.add(key("season.final_week.join", id, endsOn));
        lines.addAll(seasonResetLines(pendingMailbox));
        return List.copyOf(lines);
    }

    public static Component seasonNone() {
        return key("season.none");
    }

    /** The upcoming season while the legacy season runs: its display name (MiniMessage) and its start date. */
    public static Component seasonUpcoming(String name, String start) {
        Component displayName = MINI_MESSAGE.deserialize(name);
        return prefix().append(Component.space()).append(Component.translatable(
                "skygridng.command.season.upcoming", displayName, Component.text(start)));
    }

    public static Component seasonListEmpty() {
        return key("season.list.empty");
    }

    public static Component seasonListEntry(String id, String name, String start, String end) {
        return prefix().append(Component.space()).append(Component.translatable("skygridng.command.season.list.entry",
                Component.text(id), MINI_MESSAGE.deserialize(name),
                Component.text(start), Component.text(end)));
    }

    /** The active season and its phase; {@code next} is {@code null} when no season follows. */
    public static Component seasonStatus(String active, String phase, String next, String endsAt) {
        return next == null
                ? key("season.status.last", active, phase, endsAt)
                : key("season.status", active, phase, next, endsAt);
    }
    public static Component seasonRefused() {
        return key("season.refused");
    }

    public static Component seasonHistoryEntry(String id, int tier) {
        return key("season.history.entry", id, tier);
    }

    public static Component seasonHistoryEmpty() {
        return key("season.history.empty");
    }

    public static Component seasonRetried() {
        return key("season.retry.done");
    }

    public static Component seasonAdvanceWarn() {
        return key("season.advance.warn");
    }

    public static Component seasonAdvanced() {
        return key("season.advance.done");
    }

    public static Component seasonAdvanceNowWarn() {
        return key("season.advance.now.warn");
    }

    public static Component seasonAdvancedNow() {
        return key("season.advance.now.done");
    }

    public static Component seasonPostponeWarn(String duration) {
        return key("season.postpone.warn", duration, duration);
    }

    public static Component seasonPostponed(String duration) {
        return key("season.postpone.done", duration);
    }

    public static Component seasonDurationInvalid(String raw) {
        return key("season.duration.invalid", raw);
    }

    public static Component seasonReloadWarn() {
        return key("season.reload.warn");
    }

    public static Component seasonReloaded() {
        return key("season.reload.done");
    }
}

package net.onelitefeather.skygridng.platforms.common;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Registers the plugin's translations with Adventure's {@link GlobalTranslator}.
 *
 * Any component sent to a client is rendered in that client's locale, so {@code translatable}
 * components in command output are localized automatically (server side translation, no resource
 * pack needed). Bundle values are parsed as MiniMessage, so they may use tags (color, decoration,
 * click/hover events); {@code Component.translatable(key, args...)} arguments are exposed to the
 * template as {@code <arg:0>}, {@code <arg:1>}, etc. English and German bundles ship with the
 * plugin; more locales are added by dropping in another {@code Bundle_xx.properties} and
 * registering its locale here.
 */
final class I18n {

    private static final Key STORE_KEY = Key.key("skygridng", "messages");
    private static final String BUNDLE = "messages.Bundle";

    private TranslationStore.StringBased<String> store;

    void register() {
        store = MiniMessageTranslationStore.create(STORE_KEY);
        store.defaultLocale(Locale.ENGLISH);
        registerLocale(Locale.ENGLISH);
        registerLocale(Locale.GERMAN);
        GlobalTranslator.translator().addSource(store);
    }

    void unregister() {
        if (store != null) {
            GlobalTranslator.translator().removeSource(store);
            store = null;
        }
    }

    private void registerLocale(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale, UTF8ResourceBundleControl.get());
        store.registerAll(locale, bundle, false);
    }
}

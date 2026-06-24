package net.onelitefeather.skygridng.platforms.common;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Registers the plugin's translations with Adventure's {@link GlobalTranslator}.
 *
 * Any component sent to a client is rendered in that client's locale, so {@code translatable}
 * components in command output are localized automatically (server side translation, no resource
 * pack needed). English and German bundles ship with the plugin; more locales are added by
 * dropping in another {@code Bundle_xx.properties} and registering its locale here.
 */
final class I18n {

    private static final Key STORE_KEY = Key.key("skygridng", "messages");
    private static final String BUNDLE = "messages.Bundle";

    private TranslationStore.StringBased<MessageFormat> store;

    void register() {
        store = TranslationStore.messageFormat(STORE_KEY);
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

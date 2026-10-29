package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.SeasonId;

/**
 * Out port: notified once the season switch is committed and before players are moved into the new season.
 * Other changes (prestige, economy) bind their own implementation through a Guice multibinder, so the season
 * core never names them. A listener that throws is logged and alerted; the other listeners still run.
 */
public interface SeasonTransitionListener {

    /** The switch from season {@code from} to season {@code to} is committed. */
    void onSwitched(SeasonId from, SeasonId to);
}

package net.onelitefeather.skygridng.ports.out;

import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.api.model.SeasonSettings;

/**
 * Out port: where the core reads the season definitions and the season settings from. The plugin never
 * writes season config, so the port is read only. The adapter returns the seasons it could parse; the
 * core validates them against the known presets and challenges.
 */
public interface SeasonRepository {

    /** Loads every season definition under {@code seasons/}, unvalidated. An absent directory yields no seasons. */
    SeasonCatalog load();

    /** Reads the optional {@code season} block of {@code config}, or the defaults when it is absent. */
    SeasonSettings settings();
}

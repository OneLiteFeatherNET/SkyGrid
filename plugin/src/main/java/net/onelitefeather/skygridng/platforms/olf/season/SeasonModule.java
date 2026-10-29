package net.onelitefeather.skygridng.platforms.olf.season;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.api.model.ChallengeRule;
import net.onelitefeather.skygridng.api.model.SeasonCatalog;
import net.onelitefeather.skygridng.core.service.SeasonService;
import net.onelitefeather.skygridng.core.service.SeasonTimeline;
import net.onelitefeather.skygridng.generator.WorldGenerators;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.CurrentSeason;
import net.onelitefeather.skygridng.generator.Presets;
import net.onelitefeather.skygridng.ports.in.SeasonAdminUseCase;
import net.onelitefeather.skygridng.ports.out.PlayerSeasonReset;
import net.onelitefeather.skygridng.ports.out.SeasonAnnouncer;
import net.onelitefeather.skygridng.ports.out.SeasonRepository;
import net.onelitefeather.skygridng.ports.out.SeasonRunRepository;
import net.onelitefeather.skygridng.ports.out.SeasonTransitionListener;
import net.onelitefeather.skygridng.ports.out.SeasonWorlds;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Wires the season lifecycle. The {@link SeasonTransitionListener} set is a multibinding: a later change binds its own
 * listener in its own module and the season service picks it up without an edit here. The season service is the
 * {@link CurrentSeason} and the {@link SeasonAdminUseCase}; the configured seasons are validated against the known
 * presets and challenges when the service is built, and each rejection is logged.
 */
public final class SeasonModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), SeasonTransitionListener.class);
    }

    @Provides
    @Singleton
    SeasonService provideSeasonService(JavaPlugin plugin, SeasonRepository repository, SeasonRunRepository runs,
                                       SeasonWorlds worlds, ChallengeAdminUseCase challenges,
                                       SeasonAnnouncer announcer, PlayerSeasonReset resets,
                                       Set<SeasonTransitionListener> listeners, Presets presets,
                                       LongSupplier seeds) {
        // The same validation runs at startup and on every reload, so a reload cannot skip the checks.
        Supplier<SeasonCatalog> loader = () -> {
            Set<String> presetIds = Set.copyOf(presets.names());
            Set<String> challengeIds = challenges.list().stream()
                    .map(ChallengeRule::id)
                    .collect(Collectors.toUnmodifiableSet());
            SeasonTimeline.Validation validation = SeasonTimeline.validate(repository.load(), presetIds, challengeIds);
            validation.rejections().forEach(plugin.getLogger()::warning);
            return validation.accepted();
        };
        return new SeasonService(loader.get(), repository.settings(), runs, worlds, challenges,
                announcer, resets, listeners, seeds, loader);
    }

    @Provides
    @Singleton
    CurrentSeason provideCurrentSeason(SeasonService service) {
        return service;
    }

    @Provides
    @Singleton
    SeasonAdminUseCase provideSeasonAdminUseCase(SeasonService service) {
        return service;
    }

    @Provides
    @Singleton
    SeasonWorlds provideSeasonWorlds(JavaPlugin plugin, WorldGenerators generators, SeasonRepository repository) {
        // Both paths are resolved on use, not here: the server is not ready for them while the graph is built.
        return new BukkitSeasonWorlds(plugin, generators,
                () -> plugin.getServer().getWorlds().get(0).getWorldFolder().toPath(),
                () -> plugin.getServer().getWorldContainer().toPath()
                        .resolve(repository.settings().archiveDirectory()));
    }

    @Provides
    @Singleton
    PlayerSeasonReset providePlayerSeasonReset(JavaPlugin plugin) {
        return new BukkitPlayerSeasonReset(plugin);
    }

    @Provides
    @Singleton
    SeasonAnnouncer provideSeasonAnnouncer(JavaPlugin plugin, Provider<SeasonAdminUseCase> seasons) {
        // Provider, not the injected use case: the service itself depends on this announcer.
        return new BukkitSeasonAnnouncer(plugin, () -> seasons.get().list());
    }
}

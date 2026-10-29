package net.onelitefeather.skygridng.command;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Singleton;
import net.onelitefeather.skygridng.generator.AlgorithmCommands;
import net.onelitefeather.skygridng.challenge.ChallengeCommands;
import net.onelitefeather.skygridng.generator.MaintenanceCommands;
import net.onelitefeather.skygridng.generator.PresetCommands;
import net.onelitefeather.skygridng.season.SeasonCommands;
import net.onelitefeather.skygridng.challenge.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.challenge.ChallengeProgressUseCase;
import net.onelitefeather.skygridng.season.CurrentSeason;
import net.onelitefeather.skygridng.generator.Presets;
import net.onelitefeather.skygridng.generator.RuleAdminUseCase;
import net.onelitefeather.skygridng.season.SeasonAdminUseCase;
import net.onelitefeather.skygridng.platforms.common.injection.RepositoryModule;

import java.time.Clock;

/**
 * Binds every Cloud subcommand handler. {@code CloudCommandRegistrar} parses the whole set; a new
 * subcommand group is one new {@link CloudCommandHandler} class plus one binding line here, not a
 * new constructor parameter on the registrar.
 *
 * {@code adapters.command} is framework-free (no Guice, no jakarta.inject), so its handler classes
 * take their dependencies through a plain public constructor with no {@code @Inject}. Guice cannot
 * JIT-bind a class without either a no-arg constructor or an {@code @Inject}-annotated one, so each
 * handler with constructor arguments gets an explicit {@link Provides} method here instead, the same
 * pattern already used for the config-backed repositories in {@code RepositoryModule}.
 */
public final class CommandModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder<CloudCommandHandler> handlers = Multibinder.newSetBinder(binder(), CloudCommandHandler.class);
        handlers.addBinding().to(PresetCommands.class);
        handlers.addBinding().to(AlgorithmCommands.class);
        handlers.addBinding().to(MaintenanceCommands.class);
        handlers.addBinding().to(ChallengeCommands.class);
        handlers.addBinding().to(HelpCommands.class);
        handlers.addBinding().to(SeasonCommands.class);
        // Future subcommand groups add their binding here.
    }

    @Provides
    @Singleton
    PresetCommands providePresetCommands(Presets presets) {
        return new PresetCommands(presets);
    }

    @Provides
    @Singleton
    AlgorithmCommands provideAlgorithmCommands(RuleAdminUseCase admin) {
        return new AlgorithmCommands(admin);
    }

    @Provides
    @Singleton
    MaintenanceCommands provideMaintenanceCommands(RuleAdminUseCase admin) {
        return new MaintenanceCommands(admin);
    }

    @Provides
    @Singleton
    ChallengeCommands provideChallengeCommands(ChallengeAdminUseCase challenges, ChallengeProgressUseCase progress) {
        return new ChallengeCommands(challenges, progress);
    }

    @Provides
    @Singleton
    SeasonCommands provideSeasonCommands(SeasonAdminUseCase seasons, CurrentSeason current,
                                         ChallengeProgressUseCase progress, Clock clock) {
        return new SeasonCommands(seasons, current, progress, clock);
    }
}

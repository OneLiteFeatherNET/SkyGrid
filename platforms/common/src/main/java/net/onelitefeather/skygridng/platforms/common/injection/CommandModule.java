package net.onelitefeather.skygridng.platforms.common.injection;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import net.onelitefeather.skygridng.adapters.command.handler.AlgorithmCommands;
import net.onelitefeather.skygridng.adapters.command.handler.ChallengeCommands;
import net.onelitefeather.skygridng.adapters.command.handler.CloudCommandHandler;
import net.onelitefeather.skygridng.adapters.command.handler.HelpCommands;
import net.onelitefeather.skygridng.adapters.command.handler.MaintenanceCommands;
import net.onelitefeather.skygridng.adapters.command.handler.PresetCommands;

/**
 * Binds every Cloud subcommand handler. {@code CloudCommandRegistrar} parses the whole set; a new
 * subcommand group is one new {@link CloudCommandHandler} class plus one binding line here, not a
 * new constructor parameter on the registrar.
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
        // Future subcommand groups add their binding here.
    }
}

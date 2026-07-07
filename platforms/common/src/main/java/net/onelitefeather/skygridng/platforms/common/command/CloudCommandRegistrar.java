package net.onelitefeather.skygridng.platforms.common.command;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.command.SkyGridCommands;
import net.onelitefeather.skygridng.adapters.command.handler.CommandSource;
import net.onelitefeather.skygridng.adapters.command.parser.SkyGridArgumentParsers;
import net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase;
import net.onelitefeather.skygridng.ports.in.Presets;
import net.onelitefeather.skygridng.ports.in.RuleAdminUseCase;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.paper.PaperCommandManager;

/**
 * Migration stage 2: parses the Cloud-annotated {@link SkyGridCommands} (and its sibling
 * {@link SkyGridArgumentParsers} suggestion providers) onto the sender-mapped
 * {@link PaperCommandManager} that {@link CloudCommandManagerFactory} built.
 *
 * Guice-constructed like the rest of the composition root: it takes the same three in ports
 * {@code SkyGridCommands} needs, then builds {@code SkyGridCommands} and
 * {@code SkyGridArgumentParsers} itself with a plain constructor, no {@code @Inject} on either,
 * exactly the way {@code SkyGridDecorationPopulator} does {@code new DecorationEngine(...)}.
 *
 * Cloud's own {@code @ExceptionHandler} discovery (run inside {@link AnnotationParser#parse}) does
 * pick up {@code SkyGridCommands}'s permission-denied and invalid-syntax handlers automatically from
 * the same instance and register them on {@code manager.exceptionController()}, so there is nothing
 * extra to call here beyond the single parse call for that wiring to exist; unlike a naive design,
 * {@code Messages} (package-private to {@code adapters.command}) never needs to leave its package or
 * become part of this class's API.
 *
 * That wiring existing is not the same as it firing for every case it was written for, though.
 * {@link PaperCommandManager} always registers its commands through its built in
 * {@code ModernPaperBrigadier} registration handler (there is no builder option on
 * {@link PaperCommandManager#builder()} or {@code CoordinatedBuilder} to opt out of it), which
 * mirrors {@code SkyGridCommands}'s whole Cloud command tree into a real Mojang Brigadier tree and
 * registers that directly with Paper's own {@code Commands}/{@code CommandDispatcher}. An
 * unrecognized first token to {@code /skygrid} never reaches Cloud's exception pipeline at all in
 * that setup: Brigadier's own dispatcher rejects it with its generic syntax error before the
 * {@code Command<S>} bridge that hands off to Cloud is ever invoked. That is why
 * {@code SkyGridCommands} registers an explicit {@code unmatched} fallback subcommand for that case
 * (a real tree node Brigadier itself routes unmatched input into) rather than relying solely on its
 * {@code handleInvalidSyntax} exception handler; see the javadoc on both of those methods for the
 * full explanation.
 */
public final class CloudCommandRegistrar {

    private final RuleAdminUseCase admin;
    private final Presets presets;
    private final ChallengeAdminUseCase challenges;

    @Inject
    public CloudCommandRegistrar(RuleAdminUseCase admin, Presets presets, ChallengeAdminUseCase challenges) {
        this.admin = admin;
        this.presets = presets;
        this.challenges = challenges;
    }

    public void registerOn(PaperCommandManager<CommandSource> manager) {
        AnnotationParser<CommandSource> parser = new AnnotationParser<>(manager, CommandSource.class);
        SkyGridCommands commands = new SkyGridCommands(admin, presets, challenges);
        SkyGridArgumentParsers argumentParsers = new SkyGridArgumentParsers(admin, presets, challenges);
        parser.parse(commands, argumentParsers);
    }
}

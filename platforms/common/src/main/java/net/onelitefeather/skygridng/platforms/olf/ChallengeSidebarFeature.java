package net.onelitefeather.skygridng.platforms.olf;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.adapters.scoreboard.ChallengeSidebarListener;

/**
 * OLF-only feature: bridges {@code adapters.scoreboard}'s {@link ChallengeSidebarListener}, a plain
 * {@link net.onelitefeather.skygridng.ports.in.ChallengeAdminUseCase}/
 * {@link net.onelitefeather.skygridng.ports.in.TaskProgressUseCase} consumer with no {@link
 * OlfFeature} awareness of its own, into the {@code platforms.olf} feature set. This class carries
 * no logic beyond the wiring: it exists only because {@link ChallengeSidebarListener} lives in a
 * general purpose adapter module and cannot depend on {@code platforms.olf}.
 */
final class ChallengeSidebarFeature implements OlfFeature {

    private final ChallengeSidebarListener listener;

    @Inject
    ChallengeSidebarFeature(ChallengeSidebarListener listener) {
        this.listener = listener;
    }

    @Override
    public String id() {
        return "challenge-sidebar";
    }

    @Override
    public void enable() {
        listener.enable();
    }

    @Override
    public void disable() {
        listener.disable();
    }
}

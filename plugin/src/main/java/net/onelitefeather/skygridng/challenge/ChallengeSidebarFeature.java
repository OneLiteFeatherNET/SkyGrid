package net.onelitefeather.skygridng.challenge;

import jakarta.inject.Inject;
import net.onelitefeather.skygridng.platforms.olf.OlfFeature;

/**
 * OLF-only feature: bridges {@code platforms.olf.scoreboard}'s {@link ChallengeSidebarListener}, a
 * plain {@link net.onelitefeather.skygridng.challenge.ChallengeAdminUseCase}/
 * {@link net.onelitefeather.skygridng.challenge.TaskProgressUseCase} consumer with no {@link
 * OlfFeature} awareness of its own, into the {@code platforms.olf} feature set. This class carries
 * no logic beyond the wiring: it exists only because {@link ChallengeSidebarListener} lives in a
 * general purpose adapter module and cannot depend on {@code platforms.olf}.
 */
public final class ChallengeSidebarFeature implements OlfFeature {

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

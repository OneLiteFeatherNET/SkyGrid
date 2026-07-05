package net.onelitefeather.skygridng.ports.in;

import net.onelitefeather.skygridng.api.model.ChallengeRule;

import java.util.List;

/**
 * In port: in game administration of challenges (called by the command adapter).
 *
 * Deliberately just activate/deactivate, no toggle: toggle logic (read {@link #isActive}, call the
 * opposite verb) stays in the command layer, so a future season scheduler can call {@link #activate}
 * and {@link #deactivate} independently.
 */
public interface ChallengeAdminUseCase {

    /** All known challenges, in their stored order. */
    List<ChallengeRule> list();

    /** Whether the challenge {@code id} is currently active. False for an unknown id. */
    boolean isActive(String id);

    /** Activates the challenge {@code id} and persists the catalog. Returns false for an unknown id. */
    boolean activate(String id);

    /** Deactivates the challenge {@code id} and persists the catalog. Returns false for an unknown id. */
    boolean deactivate(String id);
}

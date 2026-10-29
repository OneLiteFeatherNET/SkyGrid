package net.onelitefeather.skygridng.generator;

/**
 * How a block placed by a rule reacts to block updates. This is platform-neutral intent; the adapter
 * maps it to the native placement (a scheduled block or fluid update, or none at all).
 *
 * <p>The default is {@link #NONE}, which keeps generated blocks stable: no fluid flows, leaves do not
 * decay, sand does not fall and redstone does not recompute just because the generator placed a block.
 * The other modes opt a single block back into vanilla behavior where that is wanted.
 *
 * <p>{@link #BLOCK_UPDATE} and {@link #FLUID_UPDATE} take effect when a decoration is placed, where the
 * adapter can schedule a single targeted update. A plain palette block is written into the raw chunk
 * during generation, which has no update to schedule, so there {@link #NONE} is the effective mode.
 * That is deliberate: scheduling updates across every grid block would risk the update storms the
 * stable default exists to prevent. Set a non-{@code NONE} mode on a decoration rule, not a palette one.
 */
public enum PhysicsMode {

    /** Place the block without any block or fluid update. Stable, deterministic, the safe default. */
    NONE,

    /** Schedule a block update after placement (support, gravity and redstone checks run). */
    BLOCK_UPDATE,

    /** Schedule a fluid update after placement, so the fluid starts to flow from this block. */
    FLUID_UPDATE
}

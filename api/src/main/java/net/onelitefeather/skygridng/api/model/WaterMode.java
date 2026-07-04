package net.onelitefeather.skygridng.api.model;

/**
 * How a placement rule provides the water a plant needs (for example sugar cane next to its support).
 * Platform-neutral intent; the adapter realizes it as a contained source or a waterlogged block.
 */
public enum WaterMode {

    /** Provide no water. The plant is placed only where its other conditions already hold. */
    NONE,

    /** Place a contained water source block next to the support, boxed in so it cannot flow. */
    SOURCE,

    /** Waterlog the support block itself, so the water is present but never flows out. */
    WATERLOGGED
}

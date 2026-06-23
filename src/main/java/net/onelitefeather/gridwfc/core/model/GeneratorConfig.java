package net.onelitefeather.gridwfc.core.model;

/**
 * Top level, immutable configuration aggregate.
 *
 * Holds the selected algorithm id plus the configuration for each algorithm. Acts as a Memento
 * for in game edits and undo. The {@code with...} methods return new instances.
 *
 * @param algorithm one of "wfc", "skygrid", "noise_skygrid"
 * @param wfc       Wave Function Collapse configuration (tiles, adjacency, settings)
 * @param skygrid   SkyGrid configuration (spacing, block table)
 */
public record GeneratorConfig(
        String algorithm,
        RuleSet wfc,
        SkyGridConfig skygrid
) {
    public GeneratorConfig {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (wfc == null) {
            throw new IllegalArgumentException("wfc config is required");
        }
        if (skygrid == null) {
            throw new IllegalArgumentException("skygrid config is required");
        }
    }

    public GeneratorConfig withAlgorithm(String newAlgorithm) {
        return new GeneratorConfig(newAlgorithm, wfc, skygrid);
    }

    public GeneratorConfig withWfc(RuleSet newWfc) {
        return new GeneratorConfig(algorithm, newWfc, skygrid);
    }

    public GeneratorConfig withSkygrid(SkyGridConfig newSkygrid) {
        return new GeneratorConfig(algorithm, wfc, newSkygrid);
    }
}

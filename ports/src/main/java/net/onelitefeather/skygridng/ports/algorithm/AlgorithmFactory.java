package net.onelitefeather.skygridng.ports.algorithm;

import net.onelitefeather.skygridng.api.model.GeneratorConfig;
import net.onelitefeather.skygridng.ports.in.GenerateRegionUseCase;

/**
 * Builds a {@link TerrainAlgorithm} from the current configuration (a Factory Method).
 *
 * Algorithms are registered by id in the composition root through a Guice MapBinder, one entry per
 * algorithm. Because this is a public port, a distribution or a future platform can contribute its
 * own algorithm by adding one binding - the core catalog ({@code DefaultTerrainAlgorithms}) does not
 * change. The factory is called again whenever the configuration changes, so each algorithm is rebuilt
 * from the live config.
 *
 * The {@code region} is the region service bound to the same config, passed in (not captured) so the
 * same factory can build an algorithm for any world's preset - this is what makes per-world
 * generators possible.
 */
@FunctionalInterface
public interface AlgorithmFactory {

    TerrainAlgorithm create(GeneratorConfig config, GenerateRegionUseCase region);
}

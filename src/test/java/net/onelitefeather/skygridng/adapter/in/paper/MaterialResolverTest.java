package net.onelitefeather.skygridng.adapter.in.paper;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the material resolver. It lives in the same package because the resolver is package
 * private on purpose (it is an adapter internal). MockBukkit gives us a real Material registry.
 */
class MaterialResolverTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a known material name resolves to the matching Material")
    void resolvesKnown() {
        MaterialResolver resolver = new MaterialResolver(Material.STONE);
        assertThat(resolver.resolve("DIRT")).isEqualTo(Material.DIRT);
        assertThat(resolver.resolve("GRASS_BLOCK")).isEqualTo(Material.GRASS_BLOCK);
    }

    @Test
    @DisplayName("an unknown material name falls back instead of crashing")
    void unknownFallsBack() {
        MaterialResolver resolver = new MaterialResolver(Material.STONE);
        assertThat(resolver.resolve("NOT_A_REAL_BLOCK_XYZ")).isEqualTo(Material.STONE);
    }
}

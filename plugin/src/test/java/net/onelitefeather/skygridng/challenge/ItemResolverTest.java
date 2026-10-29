package net.onelitefeather.skygridng.challenge;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the item resolver, mirroring {@code adapters.generator.MaterialResolverTest}'s shape: it
 * lives in the same package because the resolver is package private on purpose (it is an adapter
 * internal), and MockBukkit gives us a real {@code Registry.MATERIAL} to resolve against.
 */
class ItemResolverTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a known namespaced id resolves to the matching Material")
    void resolvesKnownNamespacedId() {
        ItemResolver resolver = new ItemResolver(Material.BARRIER);
        assertThat(resolver.resolve(ItemId.of("minecraft:diamond"))).isEqualTo(Material.DIAMOND);
    }

    @Test
    @DisplayName("a bare id defaults to the minecraft namespace")
    void resolvesBareIdDefaultingToTheMinecraftNamespace() {
        ItemResolver resolver = new ItemResolver(Material.BARRIER);
        assertThat(resolver.resolve(ItemId.of("diamond"))).isEqualTo(Material.DIAMOND);
    }

    @Test
    @DisplayName("an unknown id falls back instead of crashing")
    void unknownIdFallsBack() {
        ItemResolver resolver = new ItemResolver(Material.BARRIER);
        assertThat(resolver.resolve(ItemId.of("minecraft:this_item_does_not_exist"))).isEqualTo(Material.BARRIER);
    }
}

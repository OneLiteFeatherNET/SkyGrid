package net.onelitefeather.skygridng.adapters.generator;

import net.onelitefeather.skygridng.api.model.BlockId;
import net.onelitefeather.skygridng.api.model.PhysicsMode;
import net.onelitefeather.skygridng.api.model.Rule;
import net.onelitefeather.skygridng.api.model.RuleCatalog;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the rule resolver: a per-block rule's {@code state} map becomes a native block state, an
 * invalid property falls back to the plain block, and the physics mode is read from the rule. Package
 * private because the resolver is an adapter internal; MockBukkit gives a real block-data factory.
 */
class RuleResolverTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RuleResolver resolver(Rule... rules) {
        Map<BlockId, Rule> map = new LinkedHashMap<>();
        for (Rule rule : rules) {
            map.put(rule.block(), rule);
        }
        return new RuleResolver(new RuleCatalog(map), Material.STONE);
    }

    @Test
    @DisplayName("a rule's state becomes a native block state")
    void appliesState() {
        RuleResolver resolver = resolver(
                new Rule(BlockId.of("minecraft:oak_leaves"), Map.of("persistent", "true"), PhysicsMode.NONE, null),
                new Rule(BlockId.of("minecraft:water"), Map.of("level", "0"), PhysicsMode.NONE, null));

        BlockData leaves = resolver.blockData(BlockId.of("minecraft:oak_leaves"));
        assertThat(leaves.getMaterial()).isEqualTo(Material.OAK_LEAVES);
        assertThat(((Leaves) leaves).isPersistent()).isTrue();

        BlockData water = resolver.blockData(BlockId.of("minecraft:water"));
        assertThat(((Levelled) water).getLevel()).isZero();
    }

    @Test
    @DisplayName("a block with no rule resolves to its default state")
    void noRuleGivesDefault() {
        BlockData dirt = resolver().blockData(BlockId.of("minecraft:dirt"));
        assertThat(dirt.getMaterial()).isEqualTo(Material.DIRT);
    }

    @Test
    @DisplayName("an invalid property for the block falls back to the plain block")
    void invalidStateFallsBack() {
        RuleResolver resolver = resolver(
                new Rule(BlockId.of("minecraft:stone"), Map.of("persistent", "true"), PhysicsMode.NONE, null));

        BlockData stone = resolver.blockData(BlockId.of("minecraft:stone"));
        assertThat(stone.getMaterial()).isEqualTo(Material.STONE);
    }

    @Test
    @DisplayName("the physics mode is read from the rule, none by default")
    void physicsFromRule() {
        RuleResolver resolver = resolver(
                new Rule(BlockId.of("minecraft:sand"), Map.of(), PhysicsMode.BLOCK_UPDATE, null));

        assertThat(resolver.physics(BlockId.of("minecraft:sand"))).isEqualTo(PhysicsMode.BLOCK_UPDATE);
        assertThat(resolver.physics(BlockId.of("minecraft:dirt"))).isEqualTo(PhysicsMode.NONE);
    }
}

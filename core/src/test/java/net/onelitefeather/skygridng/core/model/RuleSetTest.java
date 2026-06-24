package net.onelitefeather.skygridng.api.model;

import net.onelitefeather.skygridng.testutil.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * White-box tests for the WFC aggregate. Tile order: water(0), sand(1), grass(2), cactus(3);
 * cactus is the only non neutral tile.
 */
class RuleSetTest {

    @Test
    @DisplayName("masks and indices reflect the tile list")
    void masksAndIndices() {
        RuleSet rules = Fixtures.ruleSet();
        assertThat(rules.fullMask()).isEqualTo(0b1111L);
        assertThat(rules.neutralMask()).isEqualTo(0b0111L); // cactus not neutral
        assertThat(rules.tileIndex("water")).isZero();
        assertThat(rules.tileIndex("cactus")).isEqualTo(3);
        assertThat(rules.buildAdjacency().tileCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("lookups: present tile returns it, unknown tile is empty or throws")
    void lookups() {
        RuleSet rules = Fixtures.ruleSet();
        assertThat(rules.tileById("grass")).get().extracting(TileType::weight).isEqualTo(10.0);
        assertThat(rules.tileById("nope")).isEmpty();
        assertThatThrownBy(() -> rules.tileIndex("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("withTileWeight returns a new rule set and leaves the original untouched")
    void withTileWeightIsImmutable() {
        RuleSet rules = Fixtures.ruleSet();
        RuleSet changed = rules.withTileWeight("water", 99.0);
        assertThat(changed).isNotSameAs(rules);
        assertThat(changed.tileById("water")).get().extracting(TileType::weight).isEqualTo(99.0);
        assertThat(rules.tileById("water")).get().extracting(TileType::weight).isEqualTo(6.0);
    }

    @Test
    @DisplayName("withAllowed and withoutAllowed add and remove a neighbour pair")
    void allowedMutations() {
        RuleSet rules = Fixtures.ruleSet(); // 4 pairs
        assertThat(rules.withAllowed("water", "grass").allowed()).hasSize(5);
        assertThat(rules.withoutAllowed("water", "sand").allowed()).hasSize(3);
    }

    @Test
    @DisplayName("the constructor rejects an empty tile list and an unknown fallback tile")
    void constructorValidation() {
        assertThatThrownBy(() -> new RuleSet(List.of(), Set.of(), Fixtures.settings()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleSet(Fixtures.tiles(), Fixtures.pairs(),
                new GenerationSettings(4, 8, 64, 8, "does_not_exist", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.MatchStateDto.Player;
import br.com.manager.utils.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FormationPositionTests {

    @Test
    void everyFormationHasElevenDistinctSlotsAndKeeps433SpecialistsInTheirSectors() {
        assertThat(FormationRules.ROLES).hasSize(12);
        for (var formation : FormationRules.ROLES.entrySet()) {
            assertThat(formation.getValue()).hasSize(11);
            assertThat(formation.getValue().stream().filter("GOL"::equals).count()).isOne();
            assertThat(FormationRules.points(formation.getKey())).hasSize(11).doesNotHaveDuplicates();
        }
        assertThat(FormationRules.ROLES.get("4-3-3")).contains("VOL", "PE", "PD", "CA", "LE", "LD");
        var points = FormationRules.points("4-3-3");
        assertThat(points.get(5).x()).isEqualTo(50);
        assertThat(points.get(5).y()).isGreaterThan(points.get(6).y());
    }

    @Test
    void anOutfieldPlayerCanKeepGoalButHasSeverelyReducedMatchSkills() {
        var p = new Player();
        p.position = "ATA";
        p.ability = 85;
        p.attributes.put("finishing", 90);
        p.attributes.put("reflexes", 30);
        PlayerPositionRules.assign(p, "CA");
        double natural = MatchEngine.skill(p, "finishing");
        PlayerPositionRules.assign(p, "GOL");
        assertThat(p.position).isEqualTo("GOL");
        assertThat(p.naturalPosition).isEqualTo("ATA");
        assertThat(MatchEngine.skill(p, "finishing")).isLessThan(natural * .25);
        assertThat(MatchEngine.skill(p, "reflexes")).isLessThan(8);
        assertThat(p.attributes.get("finishing")).isEqualTo(90);
        assertThat(p.ability).isEqualTo(85);
        PlayerPositionRules.assign(p, "CA");
        assertThat(MatchEngine.skill(p, "finishing")).isEqualTo(natural);
    }

    @Test
    void defensiveAdaptationSupportsDetailedPositionsAndKeepsSecondaryPositionsAtFullAbility() {
        for (String natural : List.of("DEF", "ZAG")) {
            assertThat(PlayerPositionRules.fit(natural, List.of("ZAG"), "LE")).isEqualTo(.90);
            assertThat(PlayerPositionRules.fit(natural, List.of("ZAG"), "LD")).isEqualTo(.90);
            assertThat(PlayerPositionRules.fit(natural, List.of("ZAG", "LD"), "LD")).isOne();
        }
        assertThat(PlayerPositionRules.fit("LD", List.of("LD"), "ALD")).isEqualTo(.96);
        assertThat(PlayerPositionRules.fit("LE", List.of("LE"), "ZAG")).isEqualTo(.90);
        assertThat(PlayerPositionRules.fit("ZAG", List.of("ZAG"), "CA")).isEqualTo(.42);
        assertThat(PlayerPositionRules.fit("ZAG", List.of("ZAG"), "GOL")).isEqualTo(.18);
        var player = new Player();
        player.position = "ZAG";
        player.ability = 80;
        player.preferredPositions = List.of("ZAG");
        PlayerPositionRules.assign(player, "ZAG");
        double defending = MatchEngine.skill(player, "tackling");
        PlayerPositionRules.assign(player, "LD");
        assertThat(MatchEngine.skill(player, "tackling")).isCloseTo(defending * .90, within(.0001));
        assertThat(player.ability).isEqualTo(80);
    }

    @Test
    void attackersKeepRelatedSkillsWhenMovingBetweenWingAndCentreForward() {
        for (String natural : List.of("ATA", "PE", "PD", "CA", "SA")) {
            var preferred = natural.equals("ATA") ? List.of("CA") : List.of(natural);
            for (String assigned : List.of("PE", "PD", "CA", "SA"))
                assertThat(PlayerPositionRules.fit(natural, preferred, assigned)).isBetween(.90, 1.0);
        }
        assertThat(PlayerPositionRules.fit("PE", List.of("PE"), "PD")).isEqualTo(.96);
        assertThat(PlayerPositionRules.fit("CA", List.of("CA"), "SA")).isEqualTo(.96);
        assertThat(PlayerPositionRules.fit("PE", List.of("PE", "CA"), "CA")).isOne();
        assertThat(PlayerPositionRules.fit("CA", List.of("CA"), "PE")).isEqualTo(.90);
        assertThat(PlayerPositionRules.fit("PE", List.of("PE"), "ZAG")).isEqualTo(.42);
        assertThat(PlayerPositionRules.fit("CA", List.of("CA"), "GOL")).isEqualTo(.18);
        var player = new Player();
        player.position = "PE";
        player.ability = 80;
        player.attributes.put("finishing", 80);
        player.preferredPositions = List.of("PE");
        PlayerPositionRules.assign(player, "PE");
        double finishing = MatchEngine.skill(player, "finishing");
        PlayerPositionRules.assign(player, "CA");
        assertThat(MatchEngine.skill(player, "finishing")).isCloseTo(finishing * .90, within(.0001));
        assertThat(player.ability).isEqualTo(80);
    }

    @Test
    void broadCategoryDoesNotMakeEverySpecialistInterchangeable() {
        assertThat(PlayerPositionRules.fit("DEF", List.of("ZAG"), "LD")).isEqualTo(.90);
        assertThat(PlayerPositionRules.fit("ATA", List.of("CA"), "VOL")).isLessThan(.5);
        assertThat(PlayerPositionRules.fit("GOL", List.of("GOL"), "CA")).isLessThan(.25);
    }
}

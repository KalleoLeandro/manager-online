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
    void broadCategoryDoesNotMakeEverySpecialistInterchangeable() {
        assertThat(PlayerPositionRules.fit("DEF", List.of("ZAG"), "LD")).isLessThan(.8);
        assertThat(PlayerPositionRules.fit("ATA", List.of("CA"), "VOL")).isLessThan(.5);
        assertThat(PlayerPositionRules.fit("GOL", List.of("GOL"), "CA")).isLessThan(.25);
    }
}

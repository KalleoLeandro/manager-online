package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.MatchStateDto.Event;
import br.com.manager.utils.MatchNarration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchNarrationTests {

    @Test
    void readingTimeHasABaseAndABoundedExtension() {
        assertThat(MatchNarration.readingDelay(6000, List.of())).isEqualTo(6000);
        var event = new Event(1, 1, 1, "PASS_SEQUENCE", 1L, null, null, "a".repeat(400));
        assertThat(MatchNarration.readingDelay(6000, List.of(event))).isEqualTo(10000);
        assertThat(MatchNarration.readingDelay(6000, List.of(event, event))).isEqualTo(16000);
        assertThat(MatchNarration.readingDelay(20000, List.of(event))).isEqualTo(20000);
    }

    @Test
    void narratedParticipantsAndAssistsBelongToTheActualTeam() {
        int assists = 0;
        for (int seed = 0; seed < 40; seed++) {
            var m = MatchEngineTests.match(seed * 971L, 78, 78);
            MatchEngineTests.run(m);
            assertThat(m.events).anyMatch(e -> e.type().equals("PASS_SEQUENCE"));
            assertThat(m.events).anyMatch(e -> e.type().equals("SHOT_BUILDUP"));
            for (var e : m.events) {
                if (!List.of("PASS_SEQUENCE", "SHOT_BUILDUP", "GOAL").contains(e.type())) continue;
                var team = e.teamId() == m.home.id ? m.home : m.away;
                var actor = team.players
                    .stream()
                    .filter(p -> p.id == e.playerId())
                    .findFirst()
                    .orElseThrow();
                assertThat(e.text()).contains(actor.name);
                if (e.relatedPlayerId() != null) {
                    var provider = team.players
                        .stream()
                        .filter(p -> p.id == e.relatedPlayerId())
                        .findFirst()
                        .orElseThrow();
                    assertThat(e.text()).contains(provider.name);
                    if (e.type().equals("GOAL")) {
                        assists++;
                        assertThat(provider.id).isNotEqualTo(actor.id);
                        assertThat(m.events).anyMatch(
                            build ->
                                build.type().equals("SHOT_BUILDUP") &&
                                build.minute() == e.minute() &&
                                build.playerId().equals(e.playerId()) &&
                                e.relatedPlayerId().equals(build.relatedPlayerId())
                        );
                    }
                }
            }
        }
        assertThat(assists).isPositive();
    }
}

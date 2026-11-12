package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.PlayerAwardDtos.*;
import br.com.manager.utils.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlayerAwardRulesTests {

    private Candidate player(long id, double rating, int goals, int assists, int matches) {
        return new Candidate(
            id,
            "Atleta " + id,
            1,
            "Clube",
            "ATA",
            85,
            matches,
            matches,
            matches * 90,
            goals,
            assists,
            matches * 40,
            0,
            0,
            0,
            rating,
            0
        );
    }

    @Test
    void goldenBallUsesTheSeasonAndCannotBeWonWithOneExcellentMatch() {
        var ranked = PlayerAwardRules.goldenBall(
            List.of(
                player(1, 9.8, 12, 0, 12),
                player(2, 8, 24, 0, 12),
                player(3, 9.1, 0, 24, 12),
                player(4, 10, 50, 50, 1)
            )
        );
        assertThat(ranked).hasSize(3);
        assertThat(ranked.getFirst().player().playerId()).isEqualTo(1);
        assertThat(ranked).noneMatch(p -> p.player().playerId() == 4);
    }

    @Test
    void goldenBootRanksGoalsBeforeAssistsThenAverage() {
        var ranked = PlayerAwardRules.scorers(
            List.of(player(1, 10, 9, 10, 12), player(2, 7, 10, 1, 12), player(3, 6, 10, 2, 12))
        );
        assertThat(ranked.stream().map(p -> p.player().playerId())).containsExactly(3L, 2L, 1L);
        assertThat(PlayerAwardRules.scorers(List.of(player(5, 9, 0, 0, 12)))).isEmpty();
    }

    @Test
    void backgroundPlayerStatsPreserveTheResolvedScoreAndAreDeterministic() {
        var home = new ArrayList<RosterPlayer>();
        var away = new ArrayList<RosterPlayer>();
        for (int i = 0; i < 22; i++) {
            var p = new RosterPlayer(
                i + 1,
                i < 11 ? 1 : 2,
                "Atleta " + i,
                i % 11 == 0 ? "GOL" : i % 11 < 5 ? "DEF" : i % 11 < 8 ? "MEI" : "ATA",
                70 + (i % 9)
            );
            (i < 11 ? home : away).add(p);
        }
        var game = new Fixture(99, LocalDate.of(2026, 4, 1), 1, 2, "Mandante", "Visitante", 3, 1);
        var result = BackgroundPlayerPerformance.play("league", game, home, away);
        assertThat(result)
            .hasSize(22)
            .isEqualTo(BackgroundPlayerPerformance.play("league", game, home, away));
        assertThat(
            result
                .stream()
                .filter(p -> p.teamId() == 1)
                .mapToInt(Performance::goals)
                .sum()
        ).isEqualTo(3);
        assertThat(
            result
                .stream()
                .filter(p -> p.teamId() == 2)
                .mapToInt(Performance::goals)
                .sum()
        ).isEqualTo(1);
        assertThat(result).allMatch(p -> p.rating() >= 3 && p.rating() <= 10 && p.estimated());
    }
}

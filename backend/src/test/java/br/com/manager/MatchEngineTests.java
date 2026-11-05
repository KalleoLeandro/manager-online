package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.*;
import br.com.manager.models.enums.PlayerAttribute;
import br.com.manager.utils.MatchEngine;
import java.util.*;
import org.junit.jupiter.api.Test;

class MatchEngineTests {

    @Test
    void cardsStayWithinCalibratedRatesAcrossHumanAndComputerTeams() {
        int games = 1500,
            withRed = 0,
            threeReds = 0;
        long yellows = 0,
            reds = 0,
            fouls = 0;
        long goalIncidents = 0,
            reviews = 0;
        for (int i = 0; i < games; i++) {
            var m = match(58019L + i * 997L, 70 + (i % 3) * 5, 70 + (i % 3) * 5);
            m.home.human = i % 2 == 0;
            m.away.human = i % 2 == 0;
            run(m);
            int matchReds = m.home.stats.reds + m.away.stats.reds;
            yellows += m.home.stats.yellows + m.away.stats.yellows;
            reds += matchReds;
            fouls += m.home.stats.fouls + m.away.stats.fouls;
            goalIncidents += m.events
                .stream()
                .filter(e -> e.type().equals("GOAL") || e.type().equals("GOAL_DISALLOWED"))
                .count();
            reviews += m.events
                .stream()
                .filter(e -> e.type().equals("VAR_REVIEW"))
                .count();
            if (matchReds > 0) withRed++;
            if (matchReds >= 3) threeReds++;
            for (var t : List.of(m.home, m.away))
                for (var p : t.players) {
                    assertThat(p.yellow).isBetween(0, 2);
                    if (p.yellow == 2) assertThat(p.sentOff).isTrue();
                }
        }
        assertThat(yellows / (double) games).isBetween(3.5, 5.5);
        assertThat(reds / (double) games).isBetween(.06, .25);
        assertThat(withRed / (double) games).isLessThan(.22);
        assertThat(threeReds / (double) games).isLessThan(.005);
        assertThat(fouls / (double) games).isBetween(18., 27.);
        assertThat(reviews / (double) goalIncidents).isBetween(.06, .11);
    }

    static MatchStateDto match(long seed, int homeAbility, int awayAbility) {
        var m = new MatchStateDto();
        m.engineVersion = MatchEngine.CURRENT_VERSION;
        m.seed = seed;
        m.home = team(1, homeAbility);
        m.away = team(2, awayAbility);
        return m;
    }

    static Team team(long id, int ability) {
        var t = new Team();
        t.id = id;
        t.name = "Teste " + id;
        for (int i = 0; i < 23; i++) {
            var p = new Player();
            p.id = id * 100 + i;
            p.name = "Fictício " + p.id;
            p.ability = ability;
            p.position =
                i == 0 || i == 11
                    ? "GOL"
                    : i < 5 || (i >= 12 && i < 16)
                      ? "DEF"
                      : i < 8 || (i >= 16 && i < 20)
                        ? "MEI"
                        : "ATA";
            p.onPitch = i < 11;
            p.entered = p.onPitch;
            for (var attr : PlayerAttribute.values()) p.attributes.put(attr.key(), ability);
            t.players.add(p);
        }
        return t;
    }

    static void run(MatchStateDto m) {
        for (int tick = 0; tick < 160 && (!m.status.equals("FINISHED") || m.varReview != null); tick++) {
            if (m.varReview != null) {
                br.com.manager.utils.VarRules.decide(m);
                m.varReview = null;
            }
            MatchEngine.step(m);
        }
    }

    @Test
    void simulationIsReproducibleAndProducesCoherentFootballStatistics() {
        var first = match(546, 75, 75);
        var same = match(546, 75, 75);
        run(first);
        run(same);
        assertThat(first.events).containsExactlyElementsOf(same.events);
        assertThat(first.status).isEqualTo("FINISHED");
        assertThat(first.minute).isBetween(93, 110);
        assertThat(first.home.stats.passesCompleted + first.away.stats.passesCompleted).isGreaterThan(500);
        for (var team : List.of(first.home, first.away)) {
            assertThat(team.stats.shots).isGreaterThanOrEqualTo(team.stats.shotsOnTarget);
            assertThat(team.stats.shotsOnTarget).isEqualTo(
                team.goals + (team == first.home ? first.away : first.home).stats.saves
            );
            assertThat(team.stats.expectedGoals).isPositive();
            assertThat(team.stats.passesMissed).isPositive();
            assertThat(team.stats.throwIns).isPositive();
            assertThat(team.substitutions).isLessThanOrEqualTo(5);
            assertThat(MatchEngine.active(team)).hasSizeLessThanOrEqualTo(11);
            assertThat(team.players).allSatisfy(p -> assertThat(p.fitness).isBetween(1, 100));
        }
        int count = first.events.size();
        MatchEngine.step(first);
        assertThat(first.events).hasSize(count);
    }

    @Test
    void betterTechnicalSkillsImproveResultsWithoutGuaranteeingWinsAndAllIncidentsAreReachable() {
        int wins = 0,
            notWins = 0;
        Set<String> types = new HashSet<>();
        long shots = 0,
            goals = 0;
        for (int seed = 1; seed <= 300; seed++) {
            var m = match(seed, 90, 50);
            run(m);
            if (m.home.goals > m.away.goals) wins++;
            else notWins++;
            m.events.forEach(e -> types.add(e.type()));
            shots += m.home.stats.shots + m.away.stats.shots;
            goals += m.home.goals + m.away.goals;
        }
        assertThat(wins).isGreaterThan(180);
        assertThat(notWins).isPositive();
        assertThat(shots / 300.0).isBetween(10., 40.);
        assertThat(goals / 300.0).isBetween(1., 6.);
        assertThat(types).contains(
            "GOAL",
            "SAVE",
            "YELLOW",
            "RED",
            "INJURY",
            "CORNER",
            "THROW_IN",
            "FOUL",
            "OFFSIDE",
            "PENALTY_AWARDED",
            "PENALTY_MISSED",
            "PENALTY_SAVED",
            "BLOCKED_SHOT",
            "SUBSTITUTION"
        );
    }

    @Test
    void substitutionsRejectReentryExpelledPlayersAndRepeatedInjuryReplacement() {
        var m = match(1, 70, 70);
        var t = m.home;
        t.human = true;
        var injured = t.players.get(2);
        injured.injured = true;
        injured.onPitch = false;
        assertThat(MatchEngine.substitute(m, t, injured.id, t.players.get(12).id)).isTrue();
        assertThat(MatchEngine.substitute(m, t, injured.id, t.players.get(13).id)).isFalse();
        assertThat(MatchEngine.substitute(m, t, t.players.get(3).id, injured.id)).isFalse();
        t.players.get(3).sentOff = true;
        t.players.get(3).onPitch = false;
        assertThat(MatchEngine.substitute(m, t, t.players.get(3).id, t.players.get(13).id)).isFalse();
        assertThat(MatchEngine.active(t)).hasSize(10);
        m.period = 2;
        for (int i = 0; i < 3; i++) {
            m.minute = 60 + i;
            assertThat(MatchEngine.substitute(m, t, t.players.get(4 + i).id, t.players.get(14 + i).id)).isTrue();
        }
        m.minute = 70;
        assertThat(MatchEngine.substitute(m, t, t.players.get(7).id, t.players.get(17).id)).isFalse();
    }

    @Test
    void pausedMatchesDoNotAdvanceAndTeamsWithFewerThanSevenLoseAdministratively() {
        var m = match(2, 70, 70);
        m.status = "PAUSED";
        MatchEngine.step(m);
        assertThat(m.minute).isZero();
        m.status = "LIVE";
        m.home.players.forEach(p -> p.onPitch = false);
        MatchEngine.step(m);
        assertThat(m.status).isEqualTo("FINISHED");
        assertThat(m.away.goals).isEqualTo(3);
        assertThat(m.events).anyMatch(e -> e.type().equals("ABANDONED"));
    }
}

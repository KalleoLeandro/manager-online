package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.*;
import org.junit.jupiter.api.Test;

class MatchPerformanceTests {

    @Test
    void alreadyRunningVersionTwoMatchesKeepTheirExactOutcome() {
        var m = MatchEngineTests.match(546, 75, 75);
        m.engineVersion = 2;
        MatchEngineTests.run(m);
        assertThat(m.home.goals).isEqualTo(2);
        assertThat(m.away.goals).isZero();
        assertThat(m.home.stats.shots).isEqualTo(14);
        assertThat(m.away.stats.shots).isEqualTo(9);
        assertThat(m.home.stats.passesCompleted).isEqualTo(405);
        assertThat(m.away.stats.passesCompleted).isEqualTo(382);
        assertThat(m.events).hasSize(200);
        assertThat(m.minute).isEqualTo(93);
    }

    @Test
    void persistedLegacyMatchesKeepTheirPreviousRulesAndExactOutcome() {
        var legacy = tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .readValue("{\"seed\":42}", br.com.manager.models.dto.MatchStateDto.class);
        assertThat(legacy.engineVersion).isEqualTo(1);
        var m = MatchEngineTests.match(546, 75, 75);
        m.engineVersion = 1;
        MatchEngineTests.run(m);
        assertThat(m.home.goals).isEqualTo(3);
        assertThat(m.away.goals).isEqualTo(2);
        assertThat(m.home.stats.shots).isEqualTo(10);
        assertThat(m.away.stats.shots).isEqualTo(15);
        assertThat(m.home.stats.passesCompleted).isEqualTo(378);
        assertThat(m.away.stats.passesCompleted).isEqualTo(396);
        assertThat(m.events).hasSize(205);
        assertThat(m.minute).isEqualTo(93);
    }

    @Test
    void strongerStartingElevensProduceMoreChancesAndWinMoreAcrossBothVenues() {
        int games = 360,
            winsEqual = 0,
            winsFive = 0,
            winsTen = 0,
            lossesTen = 0;
        double shotsFor = 0,
            shotsAgainst = 0;
        for (int i = 0; i < games; i++) {
            boolean home = i % 2 == 0;
            for (int gap : new int[] { 0, 5, 10 }) {
                var m = MatchEngineTests.match(91827L + i * 997L, home ? 75 + gap : 75, home ? 75 : 75 + gap);
                MatchEngineTests.run(m);
                var strong = home ? m.home : m.away;
                var weak = home ? m.away : m.home;
                if (strong.goals > weak.goals) {
                    if (gap == 0) winsEqual++;
                    else if (gap == 5) winsFive++;
                    else winsTen++;
                }
                if (gap == 10) {
                    if (strong.goals < weak.goals) lossesTen++;
                    shotsFor += strong.stats.shots;
                    shotsAgainst += weak.stats.shots;
                }
            }
        }
        assertThat(winsEqual / (double) games).isBetween(.25, .49);
        assertThat(winsFive).isGreaterThan(winsEqual);
        assertThat(winsTen).isGreaterThan(winsFive);
        assertThat(winsTen / (double) games).isBetween(.58, .90);
        assertThat(lossesTen).isPositive();
        assertThat(shotsFor).isGreaterThan(shotsAgainst * 1.4);
    }

    @Test
    void benchReputationDoesNotBoostTheStartingElevenAndFatigueAndDismissalsMatter() {
        var m = MatchEngineTests.match(1, 80, 70);
        var before = MatchPerformance.sectors(m.home);
        m.home.players
            .stream()
            .filter(p -> !p.onPitch)
            .forEach(p -> {
                p.ability = 99;
                p.attributes.replaceAll((k, v) -> 99);
            });
        assertThat(MatchPerformance.sectors(m.home)).isEqualTo(before);
        m.home.players
            .stream()
            .filter(p -> p.onPitch)
            .forEach(p -> p.fitness = 40);
        var tired = MatchPerformance.sectors(m.home);
        assertThat(tired.attack()).isLessThan(before.attack());
        assertThat(tired.defence()).isLessThan(before.defence());
        m.home.players.get(1).onPitch = false;
        m.home.players.get(1).sentOff = true;
        assertThat(MatchPerformance.sectors(m.home).defence()).isLessThan(tired.defence());
    }

    @Test
    void sectorAttributesAndShapeHaveConsequencesWithoutAClubOrHumanBonus() {
        var m = MatchEngineTests.match(1, 75, 75);
        var base = MatchPerformance.sectors(m.home);
        m.home.name = "Outro clube";
        m.home.human = true;
        assertThat(MatchPerformance.sectors(m.home)).isEqualTo(base);
        m.home.players
            .stream()
            .filter(p -> p.onPitch && p.position.equals("DEF"))
            .forEach(p -> {
                for (var key : new String[] { "marking", "tackling", "anticipation", "concentration" })
                    p.attributes.put(key, 40);
            });
        assertThat(MatchPerformance.sectors(m.home).defence()).isLessThan(base.defence());
        var formation = MatchFormationEffects.of("3-5-2");
        assertThat(formation.control()).isGreaterThan(MatchFormationEffects.of("4-3-3").control());
        assertThat(formation.defence()).isLessThan(MatchFormationEffects.of("4-3-3").defence());
    }
}

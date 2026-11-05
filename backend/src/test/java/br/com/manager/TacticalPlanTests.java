package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.*;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
import br.com.manager.utils.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TacticalPlanTests {

    @Test
    void newFormationAllowsAnyAvailablePlayerInGoalWithoutChangingHisNaturalPosition() {
        var access = saves.create(
            new CreateLeagueRequest("Improvised keeper", 1, "Coach", catalog.teams(1L).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var before = management.state(id, token);
        var lineup = new ArrayList<>(before.lineup());
        Collections.swap(lineup, 0, 10);
        var result = management.update(
            id,
            token,
            new UpdateTeamRequest("4-2-3-1", "BALANCED", lineup, before.version(), new TacticalPlanDto())
        );
        assertThat(result.formation()).isEqualTo("4-2-3-1");
        assertThat(result.lineup()).isEqualTo(lineup);
        assertThat(
            result
                .players()
                .stream()
                .filter(p -> p.id().equals(lineup.getFirst()))
                .findFirst()
                .orElseThrow()
                .position()
        ).isEqualTo("ATA");
    }

    @Test
    void movingOnlyOneCentreBackSidewaysChangesCoverageWithoutChangingOverallWidth() {
        var m = MatchEngineTests.match(1, 75, 75);
        var before = MatchPerformance.sectors(m.home);
        m.home.tacticalPlan.positions.put(102L, new PitchPositionDto(50.0, 70.0));
        assertThat(MatchPerformance.sectors(m.home).defence()).isLessThan(before.defence());
        assertThat(MatchPerformance.sectors(m.home).exposure()).isGreaterThan(before.exposure());
    }

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    @Test
    void persistsPlayerCoordinatesAndStrategyWithoutAllowingForeignOrBenchPlayers() {
        var access = saves.create(
            new CreateLeagueRequest("Plano tático", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var before = management.state(id, token);
        var plan = new TacticalPlanDto();
        plan.pressing = 80;
        plan.counterAttack = true;
        long defender = before.lineup().get(1);
        plan.positions.put(defender, new PitchPositionDto(18.0, 62.0));
        var after = management.update(
            id,
            token,
            new UpdateTeamRequest(before.formation(), before.mentality(), before.lineup(), before.version(), plan)
        );
        assertThat(management.state(id, token).tacticalPlan().positions.get(defender)).isEqualTo(
            new PitchPositionDto(18.0, 62.0)
        );
        assertThat(after.tacticalPlan().pressing).isEqualTo(80);
        assertThat(after.tacticalPlan().counterAttack).isTrue();
        var invalid = new TacticalPlanDto();
        invalid.positions.put(Long.MAX_VALUE, new PitchPositionDto(50.0, 50.0));
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(after.formation(), after.mentality(), after.lineup(), after.version(), invalid)
            )
        ).isInstanceOf(DefaultErrorException.class);
        invalid.positions.clear();
        invalid.positions.put(before.lineup().get(0), new PitchPositionDto(5.0, 20.0));
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(after.formation(), after.mentality(), after.lineup(), after.version(), invalid)
            )
        ).isInstanceOf(DefaultErrorException.class);
        var bench = before
            .players()
            .stream()
            .filter(p -> !before.lineup().contains(p.id()))
            .findFirst()
            .orElseThrow();
        invalid.positions.clear();
        invalid.positions.put(bench.id(), new PitchPositionDto(50.0, 50.0));
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(after.formation(), after.mentality(), after.lineup(), after.version(), invalid)
            )
        ).isInstanceOf(DefaultErrorException.class);
        assertThat(management.state(id, token).version()).isEqualTo(after.version());
    }

    @Test
    void invalidNumbersAreRejectedAndReadinessStillLocksChanges() {
        var m = MatchEngineTests.match(1, 75, 75);
        var starters = new LinkedHashMap<Long, String>();
        MatchEngine.active(m.home).forEach(p -> starters.put(p.id, p.position));
        var plan = new TacticalPlanDto();
        plan.positions.put(101L, new PitchPositionDto(Double.NaN, 60.0));
        assertThatThrownBy(() -> TacticalPlanRules.validate(plan, starters)).isInstanceOf(DefaultErrorException.class);
        plan.positions.clear();
        plan.tempo = 101;
        assertThatThrownBy(() -> TacticalPlanRules.validate(plan, starters)).isInstanceOf(DefaultErrorException.class);
        var access = saves.create(
            new CreateLeagueRequest("Plano bloqueado", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var before = management.state(id, token);
        saves.ready(id, token, true);
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(
                    before.formation(),
                    before.mentality(),
                    before.lineup(),
                    before.version(),
                    new TacticalPlanDto()
                )
            )
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void movingDefendersUpThePitchTradesCoverageForAttackingSupport() {
        var m = MatchEngineTests.match(1, 75, 75);
        var before = MatchPerformance.sectors(m.home);
        var roles = new LinkedHashMap<Long, String>();
        MatchEngine.active(m.home).forEach(p -> roles.put(p.id, p.position));
        for (var p : MatchEngine.active(m.home))
            if (p.position.equals("DEF")) m.home.tacticalPlan.positions.put(
                p.id,
                new PitchPositionDto(TacticalPlanRules.defaultPosition(p.id, roles).x(), 30.0)
            );
        var forward = MatchPerformance.sectors(m.home);
        assertThat(forward.attack()).isGreaterThan(before.attack());
        assertThat(forward.defence()).isLessThan(before.defence());
        assertThat(forward.exposure()).isGreaterThan(before.exposure());
        for (var p : MatchEngine.active(m.home))
            if (!p.position.equals("GOL")) m.home.tacticalPlan.positions.put(
                p.id,
                new PitchPositionDto(50.0, p.position.equals("DEF") ? 70.0 : p.position.equals("MEI") ? 46.0 : 22.0)
            );
        assertThat(MatchPerformance.sectors(m.home).defence()).isLessThan(before.defence());
    }

    @Test
    void strategiesHaveCostsAndSubstitutesInheritTheirPredecessorsLocation() {
        var m = MatchEngineTests.match(2, 75, 75);
        var ordinary = MatchPerformance.sectors(m.home);
        m.home.tacticalPlan.pressing = 100;
        m.home.tacticalPlan.tempo = 100;
        assertThat(MatchPerformance.sectors(m.home).pressing()).isGreaterThan(ordinary.pressing());
        assertThat(MatchTacticalEffects.drainMultiplier(m.home)).isGreaterThan(1);
        m.home.tacticalPlan.passingRisk = 100;
        assertThat(MatchTacticalEffects.passAdjustment(m.home, m.home.players.get(1))).isNegative();
        m.home.tacticalPlan.positions.put(101L, new PitchPositionDto(20.0, 60.0));
        assertThat(MatchEngine.substitute(m, m.home, 101, 112)).isTrue();
        assertThat(m.home.tacticalPlan.positions).doesNotContainKey(101L);
        assertThat(m.home.tacticalPlan.positions.get(112L)).isEqualTo(new PitchPositionDto(20.0, 60.0));
    }

    @Test
    void neutralPlanIsExactlyNeutralAndAnExposedOpponentMakesCountersMoreUseful() {
        var m = MatchEngineTests.match(1, 75, 75);
        assertThat(MatchTacticalEffects.evaluate(m.home)).isEqualTo(new MatchTacticalEffects.Effects(1, 1, 1, 1, 1, 0));
        m.home.tacticalPlan.counterAttack = true;
        double normal = MatchTacticalEffects.counterMultiplier(m.home, m.away);
        m.away.tacticalPlan.defensiveLine = 100;
        assertThat(MatchTacticalEffects.counterMultiplier(m.home, m.away)).isGreaterThan(normal);
    }
}

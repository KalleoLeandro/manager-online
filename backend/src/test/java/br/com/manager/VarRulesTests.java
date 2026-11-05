package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.utils.*;
import org.junit.jupiter.api.Test;

class VarRulesTests {

    @Test
    void reviewFrequencyIsOneInTwelveAndBothOutcomesRemainPossible() {
        int count = 0,
            confirmed = 0,
            total = 120000;
        for (long seed = 1; seed <= total; seed++) if (VarRules.needsReview(seed * 7927, 17)) {
            count++;
            if (VarRules.confirms(seed * 7927, 17)) confirmed++;
        }
        assertThat(count / (double) total).isBetween(.08, .087);
        assertThat(confirmed / (double) count).isBetween(.72, .78);
    }

    static MatchStateDto reviewed(boolean confirmed, boolean penalty) {
        var m = MatchEngineTests.match(0, 75, 75);
        m.home.goals = 1;
        var scorer = m.home.players.get(8);
        var assist = m.home.players.get(5);
        scorer.goals = 1;
        scorer.shots = 1;
        if (!penalty) assist.assists = 1;
        m.home.stats.shots = 1;
        m.home.stats.shotsOnTarget = 1;
        m.home.stats.expectedGoals = .25;
        m.home.stats.penaltiesScored = penalty ? 1 : 0;
        MatchEngine.event(m, "GOAL_PENDING", m.home, scorer, penalty ? null : assist, "Gol em revisão");
        MatchEngine.event(m, "VAR_REVIEW", m.home, scorer, null, "VAR");
        var v = new MatchStateDto.VarReview();
        v.goalSequence = 1;
        v.teamId = m.home.id;
        v.playerId = scorer.id;
        v.assistId = penalty ? null : assist.id;
        v.penalty = penalty;
        v.expectedGoals = .25;
        m.varReview = v;
        for (long seed = 1; seed < 10000; seed++) if (VarRules.confirms(seed, 1) == confirmed) {
            m.seed = seed;
            break;
        }
        return m;
    }

    @Test
    void confirmedGoalKeepsScorerAndAssistAndIsNotCountedTwice() {
        var m = reviewed(true, false);
        VarRules.decide(m);
        VarRules.decide(m);
        assertThat(m.home.goals).isEqualTo(1);
        assertThat(m.home.players.get(8).goals).isEqualTo(1);
        assertThat(m.home.players.get(5).assists).isEqualTo(1);
        assertThat(m.events)
            .filteredOn(e -> e.type().equals("GOAL"))
            .hasSize(1);
        assertThat(m.varReview.phase).isEqualTo("DECISION");
    }

    @Test
    void disallowedGoalRevertsScorePlayerAssistAndInvalidShotStatistics() {
        var m = reviewed(false, false);
        MatchEngine.event(m, "FULL_TIME", m.home, null, null, "Fim: 1 × 0");
        VarRules.decide(m);
        VarRules.decide(m);
        assertThat(m.home.goals).isZero();
        assertThat(m.home.players.get(8).goals).isZero();
        assertThat(m.home.players.get(5).assists).isZero();
        assertThat(m.home.stats.shots).isZero();
        assertThat(m.home.stats.shotsOnTarget).isZero();
        assertThat(m.home.stats.expectedGoals).isZero();
        assertThat(m.events).noneMatch(e -> e.type().equals("GOAL"));
        assertThat(m.events).anyMatch(e -> e.type().equals("GOAL_DISALLOWED"));
        assertThat(m.events)
            .filteredOn(e -> e.type().equals("FULL_TIME"))
            .allMatch(e -> e.text().contains("0 × 0"));
        assertThat(m.varReview.reason).isNotBlank();
    }

    @Test
    void disallowedPenaltyUsesAnApplicableReasonAndRevertsPenaltyGoal() {
        var m = reviewed(false, true);
        VarRules.decide(m);
        assertThat(m.home.stats.penaltiesScored).isZero();
        assertThat(m.varReview.reason).contains("cobrador");
    }
}

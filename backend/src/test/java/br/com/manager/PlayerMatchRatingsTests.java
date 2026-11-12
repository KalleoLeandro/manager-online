package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.Player;
import br.com.manager.utils.*;
import org.junit.jupiter.api.Test;

class PlayerMatchRatingsTests {

    @Test
    void ratingsRequireFiveActualMinutesAndRewardContributionWithoutChangingAbility() {
        var p = new Player();
        p.minutes = 4;
        p.goals = 2;
        p.ability = 80;
        assertThat(PlayerMatchRatings.calculate(p)).isNull();
        p.minutes = 5;
        assertThat(PlayerMatchRatings.calculate(p)).isGreaterThan(8.0);
        p.goals = 0;
        p.sentOff = true;
        p.yellow = 2;
        p.passesMissed = 20;
        assertThat(PlayerMatchRatings.calculate(p)).isLessThan(5.0);
        assertThat(p.ability).isEqualTo(80);
    }

    @Test
    void pendingVarGoalDoesNotLeakThroughRatingAndRefreshNeverChangesCounters() {
        var m = MatchEngineTests.match(10, 80, 80);
        var p = m.home.players.get(8);
        p.minutes = 15;
        p.goals = 1;
        m.varReview = new MatchStateDto.VarReview();
        m.varReview.playerId = p.id;
        m.varReview.teamId = m.home.id;
        PlayerMatchRatings.refresh(m);
        assertThat(p.rating).isEqualTo(6.0);
        assertThat(p.goals).isEqualTo(1);
        m.varReview = null;
        PlayerMatchRatings.refresh(m);
        assertThat(p.rating).isEqualTo(7.2);
    }

    @Test
    void rewardsDefendingAndGoalkeepingWithoutNeedingGoalsOrAssists() {
        var defender = new Player();
        defender.position = "ZAG";
        defender.minutes = 90;
        defender.passesCompleted = 32;
        defender.passesMissed = 4;
        defender.tackles = 4;
        defender.interceptions = 5;
        defender.blockedShots = 2;
        assertThat(PlayerMatchRatings.calculate(defender)).isGreaterThan(7.5);
        var keeper = new Player();
        keeper.position = "GOL";
        keeper.minutes = 90;
        keeper.saves = 7;
        double shutout = PlayerMatchRatings.calculate(keeper);
        assertThat(shutout).isGreaterThan(8);
        keeper.goalsAgainst = 3;
        assertThat(PlayerMatchRatings.calculate(keeper)).isLessThan(shutout - 1.5);
    }

    @Test
    void passingAccuracyAndAttackingInvolvementSeparateGoodAndPoorPerformances() {
        var p = new Player();
        p.position = "MEI";
        p.minutes = 90;
        p.passesCompleted = 40;
        p.passesMissed = 3;
        double efficient = PlayerMatchRatings.calculate(p);
        p.passesMissed = 20;
        assertThat(PlayerMatchRatings.calculate(p)).isLessThan(efficient - 1.5);
        p.position = "CA";
        p.passesCompleted = 0;
        p.passesMissed = 0;
        double anonymous = PlayerMatchRatings.calculate(p);
        p.shots = 3;
        p.shotsOnTarget = 3;
        assertThat(PlayerMatchRatings.calculate(p)).isGreaterThan(anonymous + .5);
        p.minutes = 4;
        assertThat(PlayerMatchRatings.calculate(p)).isNull();
    }

    @Test
    void individualHalfTimeTalkIsBoundedCannotBeRepeatedAndNeverHealsFitness() {
        var m = MatchEngineTests.match(11, 75, 75);
        var p = m.home.players.get(1);
        p.morale = 60;
        p.sharpness = 60;
        p.fitness = 40;
        assertThatThrownBy(() -> HalfTimeTalkRules.talk(m, m.home, p.id, "TALK_ENCOURAGE")).isInstanceOf(
            DefaultErrorException.class
        );
        m.status = "PAUSED";
        m.halfTimeBreak = true;
        m.awaitingCoachDecision = true;
        m.period = 2;
        m.pendingCoachTeams.add(m.home.id);
        HalfTimeTalkRules.talk(m, m.home, p.id, "TALK_ENCOURAGE");
        assertThat(p.morale).isBetween(55, 66);
        assertThat(p.morale).isNotEqualTo(60);
        assertThat(p.fitness).isEqualTo(40);
        assertThat(p.talkReaction).isNotBlank();
        assertThat(p.lastTalkPeriod).isEqualTo(2);
        assertThatThrownBy(() -> HalfTimeTalkRules.talk(m, m.home, p.id, "TALK_PRAISE")).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() ->
            HalfTimeTalkRules.talk(m, m.home, m.away.players.getFirst().id, "TALK_PRAISE")
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void talksCanHaveBothPositiveAndNegativeReactions() {
        boolean positive = false,
            negative = false;
        for (int i = 0; i < 100; i++) {
            var m = MatchEngineTests.match(i, 70, 70);
            m.status = "PAUSED";
            m.halfTimeBreak = true;
            m.awaitingCoachDecision = true;
            m.period = 2;
            m.pendingCoachTeams.add(m.home.id);
            var p = m.home.players.get(1);
            p.morale = 60;
            p.sharpness = 60;
            HalfTimeTalkRules.talk(m, m.home, p.id, "TALK_CRITICIZE");
            positive |= p.morale > 60;
            negative |= p.morale < 60;
        }
        assertThat(positive && negative).isTrue();
    }
}

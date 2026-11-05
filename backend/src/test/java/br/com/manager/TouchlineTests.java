package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.utils.*;
import org.junit.jupiter.api.Test;

class TouchlineTests {

    private MatchStateDto match() {
        var m = MatchEngineTests.match(17, 75, 75);
        m.home.human = true;
        return m;
    }

    @Test
    void persistentComplaintsProduceNoticesWarningAndDismissalWithoutSendingOffPlayers() {
        var m = match();
        for (int minute : new int[] { 0, 3, 6 }) {
            m.minute = minute;
            TouchlineRules.command(m, m.home, "COMPLAIN", 0);
        }
        assertThat(m.home.touchline.dismissed).isTrue();
        assertThat(m.events)
            .extracting(e -> e.type())
            .contains("REFEREE_NOTICE", "COACH_WARNING", "COACH_RED");
        assertThat(m.home.stats.reds).isZero();
        assertThat(MatchEngine.active(m.home)).hasSize(11);
        assertThatThrownBy(() -> TouchlineRules.command(m, m.home, "ENCOURAGE", 0)).isInstanceOf(
            DefaultErrorException.class
        );
    }

    @Test
    void cooldownAndPausesBlockSpamAndQuietGameMinutesRestoreConfidence() {
        var m = match();
        TouchlineRules.command(m, m.home, "COMPLAIN", 0);
        assertThatThrownBy(() -> TouchlineRules.command(m, m.home, "COMPLAIN", 0)).isInstanceOf(
            DefaultErrorException.class
        );
        m.status = "PAUSED";
        m.minute = 3;
        assertThatThrownBy(() -> TouchlineRules.command(m, m.home, "ENCOURAGE", 0)).isInstanceOf(
            DefaultErrorException.class
        );
        m.status = "LIVE";
        for (int i = 4; i <= 33; i++) {
            m.minute = i;
            TouchlineRules.tick(m, m.home);
        }
        assertThat(m.home.touchline.refereeConfidence).isEqualTo(100);
        assertThat(m.home.touchline.dismissed).isFalse();
    }

    @Test
    void instructionsChangeSectorsTemporarilyAndNeverStackOrChangeAttributes() {
        var m = match();
        var before = MatchPerformance.sectors(m.home);
        TouchlineRules.command(m, m.home, "ENCOURAGE", 0);
        assertThat(m.home.touchline.effectUntil).isEqualTo(5);
        assertThat(MatchPerformance.sectors(m.home).attack()).isCloseTo(before.attack() * 1.03, within(.00001));
        m.minute = 3;
        TouchlineRules.command(m, m.home, "DEMAND", 0);
        assertThat(m.home.touchline.refereeConfidence).isEqualTo(92);
        assertThat(m.home.touchline.effectUntil).isEqualTo(8);
        assertThat(MatchPerformance.sectors(m.home).attack()).isEqualTo(before.attack());
        assertThat(MatchPerformance.sectors(m.home).pressing()).isGreaterThan(before.pressing());
        assertThat(TouchlineRules.drain(m.home)).isGreaterThan(1);
        m.minute = 7;
        TouchlineRules.tick(m, m.home);
        assertThat(MatchPerformance.sectors(m.home).pressing()).isGreaterThan(before.pressing());
        m.minute = 8;
        TouchlineRules.tick(m, m.home);
        assertThat(MatchPerformance.sectors(m.home)).isEqualTo(before);
        assertThat(m.home.players.getFirst().ability).isEqualTo(75);
    }

    @Test
    void expiredInstructionCanBeRenewedWithoutReplacementPenalty() {
        var m = match();
        TouchlineRules.command(m, m.home, "ENCOURAGE", 0);
        m.minute = 5;
        TouchlineRules.command(m, m.home, "ENCOURAGE", 0);
        assertThat(m.home.touchline.refereeConfidence).isEqualTo(100);
        assertThat(m.home.touchline.effectUntil).isEqualTo(10);
    }

    @Test
    void complaintReplacesActiveEffectAndHelpsTeamWithHigherExertionForFiveMinutes() {
        var m = match();
        var before = MatchPerformance.sectors(m.home);
        TouchlineRules.command(m, m.home, "CALM", 0);
        m.minute = 3;
        TouchlineRules.command(m, m.home, "COMPLAIN", 0);
        var after = MatchPerformance.sectors(m.home);
        assertThat(after.attack()).isCloseTo(before.attack() * 1.02, within(.00001));
        assertThat(after.pressing()).isCloseTo(before.pressing() * 1.02, within(.00001));
        assertThat(after.control()).isEqualTo(before.control());
        assertThat(TouchlineRules.drain(m.home)).isEqualTo(1.05);
        assertThat(m.home.touchline.refereeConfidence).isEqualTo(70);
        assertThat(m.home.touchline.effectUntil).isEqualTo(8);
        m.minute = 8;
        TouchlineRules.tick(m, m.home);
        assertThat(MatchPerformance.sectors(m.home)).isEqualTo(before);
    }

    @Test
    void repeatedEarlyChangesCanTriggerWarningAndDismissalAndStopTheEffect() {
        var m = match();
        m.home.touchline.refereeConfidence = 50;
        TouchlineRules.command(m, m.home, "ENCOURAGE", 0);
        m.minute = 3;
        TouchlineRules.command(m, m.home, "CALM", 0);
        assertThat(m.home.touchline.refereeConfidence).isEqualTo(42);
        assertThat(m.home.touchline.warnings).isEqualTo(1);
        m.home.touchline.refereeConfidence = 20;
        m.minute = 6;
        TouchlineRules.command(m, m.home, "DEMAND", 0);
        assertThat(m.home.touchline.dismissed).isTrue();
        assertThat(m.home.touchline.command).isEqualTo("NONE");
        assertThat(TouchlineRules.drain(m.home)).isEqualTo(1);
        assertThat(m.home.stats.reds).isZero();
    }

    @Test
    void previousEightMinuteInstructionIsShortenedOnceWithoutRepeatedlyMovingTheDeadline() {
        var m = match();
        m.home.touchline.command = "DEMAND";
        m.home.touchline.effectUntil = 18;
        m.minute = 11;
        TouchlineRules.tick(m, m.home);
        assertThat(m.home.touchline.effectUntil).isEqualTo(15);
        m.minute = 14;
        TouchlineRules.tick(m, m.home);
        assertThat(m.home.touchline.effectUntil).isEqualTo(15);
        assertThat(m.home.touchline.command).isEqualTo("DEMAND");
        m.minute = 15;
        TouchlineRules.tick(m, m.home);
        assertThat(m.home.touchline.command).isEqualTo("NONE");
    }

    @Test
    void halftimeAddedTimeDoesNotResetCooldown() {
        var m = match();
        m.minute = 46;
        m.addedFirst = 2;
        TouchlineRules.command(m, m.home, "ENCOURAGE", 0);
        m.period = 2;
        m.minute = 45;
        assertThatThrownBy(() -> TouchlineRules.command(m, m.home, "DEMAND", 0)).isInstanceOf(
            DefaultErrorException.class
        );
        m.minute = 47;
        TouchlineRules.command(m, m.home, "CALM", 0);
        assertThat(MatchPerformance.sectors(m.home).control()).isGreaterThan(70);
    }
}

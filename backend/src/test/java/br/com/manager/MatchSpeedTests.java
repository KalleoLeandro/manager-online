package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.MatchSpeed;
import org.junit.jupiter.api.Test;

class MatchSpeedTests {

    @Test
    void startsPromptlyAtEverySpeedWithoutChangingSubsequentCadence() {
        for (int speed : new int[] {50, 100, 200, 400}) {
            assertThat(MatchSpeed.kickoffDelay(6000, speed)).isEqualTo(1000);
        }
        assertThat(MatchSpeed.kickoffDelay(1500, 400)).isEqualTo(375);
        assertThat(MatchSpeed.kickoffDelay(250, 400)).isEqualTo(250);
        assertThatThrownBy(() -> MatchSpeed.kickoffDelay(6000, 300)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changesOnlyTheWallClockCadenceAndPreservesMinimumDelay() {
        assertThat(MatchSpeed.delay(6000, 50)).isEqualTo(12000);
        assertThat(MatchSpeed.delay(6000, 100)).isEqualTo(6000);
        assertThat(MatchSpeed.delay(6000, 200)).isEqualTo(3000);
        assertThat(MatchSpeed.delay(6000, 400)).isEqualTo(1500);
        assertThat(MatchSpeed.delay(16000, 400)).isEqualTo(4000);
        assertThat(MatchSpeed.delay(250, 400)).isEqualTo(250);
        assertThatThrownBy(() -> MatchSpeed.delay(6000, 300)).isInstanceOf(IllegalArgumentException.class);
    }
}

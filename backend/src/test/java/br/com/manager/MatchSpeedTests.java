package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.MatchSpeed;
import org.junit.jupiter.api.Test;

class MatchSpeedTests {

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

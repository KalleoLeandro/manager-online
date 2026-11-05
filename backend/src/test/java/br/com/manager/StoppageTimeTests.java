package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.MatchEngine;
import org.junit.jupiter.api.Test;

class StoppageTimeTests {

    @Test
    void firstHalfAnnouncesAndPlaysAddedTimeBeforeTheInterval() {
        var match = MatchEngineTests.match(17, 75, 75);
        match.minute = 44;
        match.addedFirst = 3;
        MatchEngine.step(match);
        assertThat(match.period).isEqualTo(1);
        assertThat(match.minute).isEqualTo(45);
        assertThat(match.events)
            .filteredOn(e -> e.type().equals("ADDED_TIME"))
            .hasSize(1);
        for (int minute = 46; minute <= 47; minute++) {
            match.varReview = null;
            MatchEngine.step(match);
            assertThat(match.minute).isEqualTo(minute);
            assertThat(match.period).isEqualTo(1);
        }
        match.varReview = null;
        MatchEngine.step(match);
        assertThat(match.period).isEqualTo(2);
        assertThat(match.halfTimeBreak).isTrue();
        assertThat(match.events).anyMatch(e -> e.type().equals("HALF_TIME") && e.period() == 1 && e.minute() == 48);
    }

    @Test
    void secondHalfAlsoAnnouncesAddedTimeAndDoesNotEndAtNinety() {
        var match = MatchEngineTests.match(17, 75, 75);
        match.period = 2;
        match.minute = 89;
        match.addedSecond = 4;
        MatchEngine.step(match);
        assertThat(match.status).isEqualTo("LIVE");
        assertThat(match.events).anyMatch(e -> e.type().equals("ADDED_TIME") && e.period() == 2 && e.minute() == 90);
        for (int minute = 91; minute <= 94; minute++) {
            match.varReview = null;
            MatchEngine.step(match);
        }
        assertThat(match.status).isEqualTo("FINISHED");
        assertThat(match.minute).isEqualTo(94);
    }
}

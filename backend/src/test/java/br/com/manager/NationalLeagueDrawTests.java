package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.NationalLeagueDraw;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;

class NationalLeagueDrawTests {

    @Test
    void rejectsDuplicateClubsAndImpossiblePeriods() {
        var first = LocalDate.of(2026, 1, 1);
        var last = LocalDate.of(2026, 12, 1);
        assertThatThrownBy(() -> NationalLeagueDraw.draw(List.of(1L, 1L), first, last, 1)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> NationalLeagueDraw.draw(List.of(1L), first, last, 1)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() ->
            NationalLeagueDraw.draw(List.of(1L, 2L, 3L, 4L), first, first.plusDays(5), 1)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThat(NationalLeagueDraw.draw(List.of(1L, 2L), first, last, 1)).hasSize(2);
    }

    @Test
    void drawsEveryPairTwiceWithBalancedVenuesAndNoDoubleBooking() {
        for (int count : new int[] { 5, 18, 20, 24 }) {
            var teams = LongStream.rangeClosed(1, count).boxed().toList();
            var fixtures = NationalLeagueDraw.draw(teams, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 30), 42);
            assertThat(fixtures).hasSize(count * (count - 1));
            assertThat(
                fixtures
                    .stream()
                    .map(f -> f.homeTeamId() + ":" + f.awayTeamId())
                    .toList()
            ).doesNotHaveDuplicates();
            for (var team : teams) {
                assertThat(
                    fixtures
                        .stream()
                        .filter(f -> f.homeTeamId().equals(team))
                        .count()
                ).isEqualTo(count - 1);
                if (count % 2 == 0) {
                    Boolean previous = null;
                    int consecutive = 0;
                    for (var fixture : fixtures) {
                        if (!fixture.homeTeamId().equals(team) && !fixture.awayTeamId().equals(team)) continue;
                        boolean home = fixture.homeTeamId().equals(team);
                        consecutive = previous != null && previous == home ? consecutive + 1 : 1;
                        assertThat(consecutive).isLessThanOrEqualTo(3);
                        previous = home;
                    }
                }
                assertThat(
                    fixtures
                        .stream()
                        .filter(f -> f.awayTeamId().equals(team))
                        .count()
                ).isEqualTo(count - 1);
            }
            for (var round : fixtures
                .stream()
                .collect(Collectors.groupingBy(NationalLeagueDraw.Fixture::round))
                .values()) {
                assertThat(
                    round
                        .stream()
                        .flatMap(f -> Stream.of(f.homeTeamId(), f.awayTeamId()))
                        .toList()
                ).doesNotHaveDuplicates();
                assertThat(
                    round
                        .stream()
                        .map(f -> f.date())
                        .distinct()
                        .count()
                ).isEqualTo(1);
            }
        }
    }

    @Test
    void seedIsReproducibleButDifferentYearsCanHaveDifferentDraws() {
        var teams = LongStream.rangeClosed(1, 20).boxed().toList();
        var start = LocalDate.of(2026, 2, 1);
        var end = LocalDate.of(2026, 12, 6);
        assertThat(NationalLeagueDraw.draw(teams, start, end, 123))
            .isEqualTo(NationalLeagueDraw.draw(teams, start, end, 123))
            .isNotEqualTo(NationalLeagueDraw.draw(teams, start, end, 124));
        assertThat(NationalLeagueDraw.draw(teams, start, end, 123)).allMatch(
            f -> !f.date().isBefore(start) && !f.date().isAfter(end)
        );
    }
}

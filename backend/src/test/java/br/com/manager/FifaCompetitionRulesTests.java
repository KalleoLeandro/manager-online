package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.manager.models.dto.MatchStateDto.*;
import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.repository.TournamentCalendarRepository;
import br.com.manager.repository.TournamentCalendarRepository.Booking;
import br.com.manager.service.TournamentCalendarService;
import br.com.manager.utils.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class FifaCompetitionRulesTests {

    Entrant club(int id, int rank, String stage, int division) {
        return new Entrant(id, "Clube " + id, stage, rank, "Associação " + id, null, division, 75);
    }

    Game game(int id, int home, int away, int hg, int ag) {
        return new Game(id, 1, 0, id, 1, LocalDate.of(2029, 6, 15), home, away, 75, 75, hg, ag, "PLAYED");
    }

    @Test
    void fifaUsesHeadToHeadBeforeOverallGoalDifference() {
        var clubs = List.of(
            club(1, 1, "GROUPS", 1),
            club(2, 2, "GROUPS", 1),
            club(3, 3, "GROUPS", 1),
            club(4, 4, "GROUPS", 1)
        );
        var games = List.of(
            game(1, 1, 2, 1, 0),
            game(2, 1, 3, 0, 9),
            game(3, 1, 4, 1, 0),
            game(4, 2, 3, 5, 0),
            game(5, 2, 4, 5, 0),
            game(6, 3, 4, 0, 1)
        );
        assertThat(
            FifaGroupStandings.rank(clubs, games, Map.of(), 17)
                .stream()
                .map(r -> r.team().id())
        ).containsExactly(1L, 2L, 4L, 3L);
    }

    @Test
    void conductDoesNotCountSecondYellowAsADirectRed() {
        var team = new Team();
        for (int i = 0; i < 4; i++) team.players.add(new Player());
        team.players.get(0).yellow = 2;
        team.players.get(0).sentOff = true;
        team.players.get(1).yellow = 1;
        team.players.get(1).sentOff = true;
        team.players.get(2).sentOff = true;
        team.players.get(3).yellow = 1;
        assertThat(TournamentFairPlay.points(team)).isEqualTo(13);
    }

    @Test
    void germanFirstRoundHasThirtyTwoClubsInEachPotAndAmateurPotHosts() {
        var teams = new ArrayList<Entrant>();
        for (int id = 1; id <= 64; id++) teams.add(club(id, id, "R64", id <= 18 ? 1 : id <= 36 ? 2 : 3));
        var ties = KnockoutDraw.draw(1, 0, TournamentRulebooks.dfbPokal(2026).stages().getFirst(), teams, 37);
        assertThat(ties)
            .hasSize(32)
            .allMatch(t -> t.home() > 32 && t.away() <= 32);
    }

    @Test
    void uefaTopTwoSeedsAreInOppositeHalvesAndPlayoffWinnersHostFirst() {
        var teams = new ArrayList<Entrant>();
        for (int rank = 1; rank <= 16; rank++) teams.add(club(rank, rank, "LEAGUE", 1));
        var round = TournamentRulebooks.championsLeague(2026).stages().get(2);
        var ties = KnockoutDraw.draw(1, 2, round, teams, 37);
        assertThat(ties).allMatch(t -> t.home() >= 9 && t.away() <= 8);
        assertThat(
            ties
                .stream()
                .filter(t -> t.away() == 1)
                .findFirst()
                .orElseThrow()
                .number()
        ).isLessThanOrEqualTo(4);
        assertThat(
            ties
                .stream()
                .filter(t -> t.away() == 2)
                .findFirst()
                .orElseThrow()
                .number()
        ).isGreaterThan(4);
    }

    @Test
    void lateCupEntryDoesNotCreatePastHumanGamesAndPendingNationalsMoveWithoutChangingPlayedGames() {
        var repository = mock(TournamentCalendarRepository.class);
        var service = new TournamentCalendarService(repository);
        var day = LocalDate.of(2026, 7, 2);
        var played = new Booking(1, 1, 3, day.minusDays(3), false, "PLAYED");
        when(repository.start("league")).thenReturn(LocalDate.of(2026, 1, 1));
        when(repository.humanDates("league")).thenReturn(Map.of(1L, day));
        when(repository.bookings(eq("league"), anyCollection())).thenReturn(List.of(played));
        var plan = service.plan("league", List.of(new NationalLeagueDraw.Fixture(1, LocalDate.of(2026, 4, 22), 1L, 2L)));
        assertThat(plan.getFirst().date()).isEqualTo(day);
        when(repository.bookings(eq("league"), anyCollection())).thenReturn(
            List.of(played, new Booking(2, 1, 4, day, false, "SCHEDULED"), new Booking(3, 1, 2, day, true, "SCHEDULED"))
        );
        service.protectNationals("league", plan);
        verify(repository).move("league", 2, day.plusDays(3));
        verify(repository, never()).move(eq("league"), eq(1L), any());
    }
}

package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.repository.*;
import br.com.manager.service.*;
import br.com.manager.utils.ClubEventRules;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClubEventTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    @Autowired
    FinanceService finances;

    @Autowired
    FinanceRepository money;

    @Autowired
    ClubEventService events;

    @Autowired
    ClubEventRepository bookings;

    @Autowired
    DayProgressRepository days;

    @Autowired
    MatchdayService matches;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    TournamentService tournaments;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create() {
        var a = saves.create(
            new CreateLeagueRequest("Eventos do clube", 1, "Teste Eventos", catalog.teams(null).getFirst().id())
        );
        management.state(a.room().id(), token(a));
        finances.initialize(a.room().id(), team(a));
        db.update("UPDATE save_teams SET budget=100000000 WHERE save_id=?", a.room().id());
        return a;
    }

    private String token(LeagueAccessResponse a) {
        return "Bearer " + a.accessToken();
    }

    private long team(LeagueAccessResponse a) {
        return a.room().members().getFirst().teamId();
    }

    private ClubEventRequest request(String type, LocalDate date) {
        return new ClubEventRequest(type, date, 1, null, null, null);
    }

    @Test
    void marketingChargesOnceImprovesFansAndCannotBeRepeatedToFarmTrust() {
        var a = create();
        String id = a.room().id();
        long team = team(a);
        LocalDate date = days.date(id, team);
        int fans = bookings.fanTrust(id, team);
        long cash = money.cash(id, team);
        var e = events.create(id, token(a), request("MARKETING", date));
        assertThat(money.cash(id, team)).isEqualTo(cash - e.cost());
        assertThat(events.processDay(id, team, date)).isTrue();
        assertThat(bookings.fanTrust(id, team)).isEqualTo(Math.min(100, fans + 6));
        assertThat(events.processDay(id, team, date)).isFalse();
        assertThat(bookings.fanTrust(id, team)).isEqualTo(Math.min(100, fans + 6));
        assertThatThrownBy(() -> events.create(id, token(a), request("MARKETING", date.plusDays(1)))).isInstanceOf(
            DefaultErrorException.class
        );
    }

    @Test
    void campsConsumeConditionAccumulateSkillAndKeepFutureDaysScheduled() {
        var a = create();
        String id = a.room().id();
        long team = team(a);
        LocalDate date = days.date(id, team);
        db.update("UPDATE save_players SET fitness=90 WHERE save_id=? AND team_id=?", id, team);
        db.update("UPDATE save_player_profiles SET stamina=50 WHERE save_id=?", id);
        var e = events.create(id, token(a), new ClubEventRequest("CAMP", date, 5, null, "ATIBAIA", null));
        for (int d = 0; d < 4; d++) events.processDay(id, team, date.plusDays(d));
        assertThat(bookings.find(id, e.id()).status()).isEqualTo("SCHEDULED");
        assertThat(bookings.find(id, e.id()).processedDays()).isEqualTo(4);
        assertThat(
            db.queryForObject(
                "SELECT MIN(fitness) FROM save_players WHERE save_id=? AND team_id=?",
                Integer.class,
                id,
                team
            )
        ).isEqualTo(50);
        assertThat(
            db.queryForObject(
                "SELECT MIN(stamina) FROM save_player_profiles WHERE save_id=? AND player_id IN(SELECT player_id FROM save_players WHERE save_id=? AND team_id=?)",
                Integer.class,
                id,
                id,
                team
            )
        ).isEqualTo(51);
        events.processDay(id, team, date.plusDays(3));
        assertThat(bookings.find(id, e.id()).processedDays()).isEqualTo(4);
        events.processDay(id, team, date.plusDays(4));
        assertThat(bookings.find(id, e.id()).status()).isEqualTo("COMPLETED");
    }

    @Test
    void cancellationRefundsOnlyUnusedCampDays() {
        var a = create();
        String id = a.room().id();
        long team = team(a);
        LocalDate date = days.date(id, team);
        long cash = money.cash(id, team);
        var e = events.create(id, token(a), new ClubEventRequest("CAMP", date, 4, null, "ATIBAIA", null));
        events.processDay(id, team, date);
        events.cancel(id, token(a), e.id());
        assertThat(money.cash(id, team)).isEqualTo(cash - 55000);
        assertThatThrownBy(() ->
            events.create(id, token(a), new ClubEventRequest("CAMP", date.plusDays(1), 1, null, "ATIBAIA", null))
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void anniversaryUsesAndKeepsTheFoundationDateInsteadOfTheRequestedDate() {
        var a = create();
        String id = a.room().id();
        LocalDate now = days.date(id, team(a));
        LocalDate founded = LocalDate.of(1914, 8, 26);
        var e = events.create(id, token(a), new ClubEventRequest("ANNIVERSARY", now, 1, null, null, founded));
        assertThat(e.date()).isEqualTo(ClubEventRules.anniversary(founded, now));
        assertThat(events.options(id, token(a)).foundationDate()).isEqualTo(founded);
        assertThatThrownBy(() ->
            events.create(id, token(a), new ClubEventRequest("ANNIVERSARY", now, 1, null, null, founded))
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void friendlyUsesTheMatchScreenPaysEqualGateAndNeverBecomesANationalSeason() {
        var a = create();
        String id = a.room().id();
        long home = team(a);
        LocalDate date = days.date(id, home);
        var away = bookings
            .opponents(id, home)
            .stream()
            .filter(o -> !o.human())
            .findFirst()
            .orElseThrow();
        var e = events.create(id, token(a), new ClubEventRequest("FRIENDLY", date, 1, away.id(), null, null));
        assertThat(e.fixtureId()).isNotNull();
        assertThat(money.nextSeason(id, home)).isNotEqualTo(
            db.queryForObject("SELECT season_id FROM national_fixtures WHERE id=?", Long.class, e.fixtureId())
        );
        matches.start(id, token(a));
        var state = matchRepository.session(id, e.fixtureId()).orElseThrow().state();
        assertThat(state.friendlyMatch).isTrue();
        assertThat(state.cupMatch).isTrue();
        assertThat(state.decisiveTie).isFalse();
        assertThat(state.extraTimeAllowed).isFalse();
        long homeCash = money.cash(id, home),
            awayCash = money.cash(id, away.id());
        events.completeFriendly(id, state);
        var result = bookings.find(id, e.id());
        assertThat(money.cash(id, home) - homeCash).isEqualTo(result.revenue() / 2);
        assertThat(money.cash(id, away.id()) - awayCash - e.cost()).isEqualTo(result.revenue() / 2);
        events.completeFriendly(id, state);
        assertThat(money.cash(id, home) - homeCash).isEqualTo(result.revenue() / 2);
        assertThatCode(() -> tournaments.completed(id, state)).doesNotThrowAnyException();
    }

    @Test
    void eventCannotSpendTheClubsLastReservedFunds() {
        var a = create();
        String id = a.room().id();
        db.update("UPDATE save_teams SET budget=0 WHERE save_id=? AND team_id=?", id, team(a));
        assertThatThrownBy(() -> events.create(id, token(a), request("CHARITY", days.date(id, team(a))))).isInstanceOf(
            DefaultErrorException.class
        );
        assertThat(bookings.list(id, team(a))).isEmpty();
    }

    @Test
    void anotherLeaguesCoachCannotCancelABooking() {
        var a = create();
        var b = create();
        var e = events.create(a.room().id(), token(a), request("CHARITY", days.date(a.room().id(), team(a))));
        assertThatThrownBy(() -> events.cancel(a.room().id(), token(b), e.id())).isInstanceOf(
            DefaultErrorException.class
        );
        assertThat(bookings.find(a.room().id(), e.id()).status()).isEqualTo("SCHEDULED");
    }

    @Test
    void birthdayHandlesLeapYearsAndVenueCostsArePerDay() {
        assertThat(ClubEventRules.anniversary(LocalDate.of(2000, 2, 29), LocalDate.of(2027, 1, 1))).isEqualTo(
            LocalDate.of(2027, 2, 28)
        );
        assertThat(ClubEventRules.cost("CAMP", 80, 0, 3, "ATIBAIA")).isEqualTo(165000);
        assertThat(ClubEventRules.anniversary(LocalDate.of(1914, 8, 26), LocalDate.of(2026, 9, 1))).isEqualTo(
            LocalDate.of(2027, 8, 26)
        );
    }

    @Test
    void humanOpponentMustAcceptBeforeTheFriendlyExists() {
        var clubs = catalog.teams(null);
        var a = saves.create(new CreateLeagueRequest("Convite amistoso", 2, "Organizador", clubs.getFirst().id()));
        var b = saves.join(
            a.room().inviteCode(),
            new JoinLeagueRequest(
                "Convidado",
                clubs
                    .stream()
                    .filter(
                        c -> !c.id().equals(clubs.getFirst().id()) && c.leagueId().equals(clubs.getFirst().leagueId())
                    )
                    .findFirst()
                    .orElseThrow()
                    .id()
            )
        );
        String id = a.room().id();
        long host = team(a);
        long guest = saves.realtimeMember(id, b.accessToken()).teamId();
        management.state(id, token(a));
        finances.initialize(id, host);
        db.update("UPDATE save_teams SET budget=100000000 WHERE save_id=?", id);
        var e = events.create(
            id,
            token(a),
            new ClubEventRequest("FRIENDLY", days.date(id, host).plusDays(3), 1, guest, null, null)
        );
        assertThat(e.status()).isEqualTo("PROPOSED");
        assertThat(e.fixtureId()).isNull();
        assertThatThrownBy(() -> events.accept(id, token(a), e.id())).isInstanceOf(DefaultErrorException.class);
        var confirmed = events.accept(id, token(b), e.id());
        assertThat(confirmed.status()).isEqualTo("SCHEDULED");
        assertThat(confirmed.fixtureId()).isNotNull();
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM club_event_calendar WHERE event_id=?", Integer.class, e.id())
        ).isZero();
    }

    @Test
    void overlappingDaysAreRejectedAndYouthDayGeneratesTheAcademy() {
        var a = create();
        String id = a.room().id();
        long team = team(a);
        LocalDate date = days.date(id, team);
        events.create(id, token(a), request("YOUTH_DAY", date));
        assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM league_juniors WHERE save_id=? AND team_id=?",
                Integer.class,
                id,
                team
            )
        ).isEqualTo(18);
        assertThatThrownBy(() -> events.create(id, token(a), request("TEAM_PARTY", date))).isInstanceOf(
            DefaultErrorException.class
        );
        int potential = db.queryForObject(
            "SELECT MIN(potential) FROM league_juniors WHERE save_id=? AND team_id=?",
            Integer.class,
            id,
            team
        );
        events.processDay(id, team, date);
        assertThat(
            db.queryForObject(
                "SELECT MIN(potential) FROM league_juniors WHERE save_id=? AND team_id=?",
                Integer.class,
                id,
                team
            )
        ).isEqualTo(Math.min(99, potential + 1));
    }
}

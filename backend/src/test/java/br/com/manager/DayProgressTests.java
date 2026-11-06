package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.*;
import br.com.manager.utils.TrainingRules;
import java.time.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DayProgressTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    DayProgressService progress;

    @Autowired
    ManagementService management;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create() {
        return saves.create(
            new CreateLeagueRequest("Avanço diário", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
    }

    private String token(LeagueAccessResponse a) {
        return "Bearer " + a.accessToken();
    }

    private void fixture(LeagueAccessResponse a, LocalDate date) {
        db.update("UPDATE national_fixtures SET match_date=? WHERE save_id=? AND season_id IN(SELECT s.id FROM national_seasons s JOIN save_teams t ON t.save_id=s.save_id AND t.league_id=s.league_id WHERE s.save_id=? AND t.team_id=?)", date, a.room().id(),a.room().id(),a.room().members().getFirst().teamId());
    }

    @Test
    void advancesAndStopsAtMatchDayWithoutPlayingOrDuplicatingDays() {
        var a = create();
        var id = a.room().id();
        var initial = progress.state(id, token(a));
        fixture(a, initial.currentDate().plusDays(2));
        try (var rules = mockStatic(TrainingRules.class, CALLS_REAL_METHODS)) {
            rules.when(() -> TrainingRules.injuryChance(anyInt())).thenReturn(0.0);
            var result = progress.advance(id, token(a), new AdvanceDaysRequest(initial.currentDate(), 7));
            assertThat(result.advancedDays()).isEqualTo(2);
            assertThat(result.currentDate()).isEqualTo(initial.currentDate().plusDays(2));
            assertThat(result.events()).isNotEmpty();
            assertThat(result.stopReason()).contains("Dia de jogo");
            assertThat(
                db.queryForObject(
                    "SELECT COUNT(*) FROM national_fixtures f JOIN national_seasons s ON s.id=f.season_id WHERE f.save_id=? AND f.status='PLAYED' AND EXISTS(SELECT 1 FROM save_members m JOIN save_teams t ON t.save_id=m.save_id AND t.team_id=m.team_id WHERE m.save_id=f.save_id AND t.league_id=s.league_id)",
                    Integer.class,
                    id
                )
            ).isZero();
            assertThatThrownBy(() ->
                progress.advance(id, token(a), new AdvanceDaysRequest(initial.currentDate(), 1))
            ).isInstanceOf(DefaultErrorException.class);
            assertThat(
                progress.advance(id, token(a), new AdvanceDaysRequest(result.currentDate(), 1)).advancedDays()
            ).isZero();
        }
    }

    @Test
    void trainingInjuryStopsFastForwardAndBlocksPlayerUntilRecovery() {
        var a = create();
        var id = a.room().id();
        var initial = progress.state(id, token(a));
        var monday = initial.currentDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        db.update("UPDATE game_saves SET calendar_start_date=? WHERE id=?", monday, id);
        fixture(a, monday.plusDays(20));
        long player = management.state(id, token(a)).players().getFirst().id();
        try (var rules = mockStatic(TrainingRules.class, CALLS_REAL_METHODS)) {
            rules
                .when(() -> TrainingRules.random(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(call ->
                    new Random() {
                        @Override
                        public double nextDouble() {
                            return (Long) call.getArgument(2) == player ? 0 : 1;
                        }
                    }
                );
            var result = progress.advance(id, token(a), new AdvanceDaysRequest(monday, 7));
            assertThat(result.advancedDays()).isEqualTo(1);
            assertThat(result.stopReason()).contains("lesão");
            assertThat(result.events()).anyMatch(e -> e.title().startsWith("Lesão no treino"));
            assertThat(
                management
                    .state(id, token(a))
                    .players()
                    .stream()
                    .filter(p -> p.id() == player)
                    .findFirst()
                    .orElseThrow()
                    .condition()
                    .available()
            ).isFalse();
            db.update("UPDATE save_player_profiles SET return_round=2 WHERE save_id=? AND player_id=?", id, player);
            rules.when(() -> TrainingRules.injuryChance(anyInt())).thenReturn(0.0);
            var recovered = progress.advance(id, token(a), new AdvanceDaysRequest(result.currentDate(), 1));
            assertThat(recovered.events()).anyMatch(e -> e.title().startsWith("Retorno aos treinos"));
            assertThat(
                management
                    .state(id, token(a))
                    .players()
                    .stream()
                    .filter(p -> p.id() == player)
                    .findFirst()
                    .orElseThrow()
                    .condition()
                    .available()
            ).isTrue();
        }
    }

    @Test
    void respectsReadyAndLiveBarrierAndRejectsUnauthenticatedAccess() {
        var a = create();
        var id = a.room().id();
        var initial = progress.state(id, token(a));
        fixture(a, initial.currentDate().plusDays(2));
        assertThatThrownBy(() -> progress.state(id, null)).isInstanceOf(DefaultErrorException.class);
        db.update("UPDATE game_saves SET capacity=2 WHERE id=?",id);
        db.update("UPDATE save_members SET ready=TRUE WHERE save_id=?", id);
        assertThatThrownBy(() ->
            progress.advance(id, token(a), new AdvanceDaysRequest(initial.currentDate(), 1))
        ).isInstanceOf(DefaultErrorException.class);
        db.update("UPDATE save_members SET ready=FALSE WHERE save_id=?", id);
        db.update("UPDATE game_saves SET status='LIVE' WHERE id=?", id);
        assertThatThrownBy(() ->
            progress.advance(id, token(a), new AdvanceDaysRequest(initial.currentDate(), 1))
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void personalCalendarDoesNotAdvanceOtherClubsAndClubEventStopsThePeriod() {
        var a = create();
        var id = a.room().id();
        var team = a.room().members().getFirst().teamId();
        var initial = progress.state(id, token(a));
        fixture(a, initial.currentDate().plusDays(20));
        long other = db.queryForObject(
            "SELECT MIN(team_id) FROM save_teams WHERE save_id=? AND team_id<>?",
            Long.class,
            id,
            team
        );
        db.update(
            "INSERT INTO league_calendar_events(save_id,team_id,event_date,event_type,title) VALUES(?,?,?,'CLUB','Reunião da diretoria')",
            id,
            team,
            initial.currentDate()
        );
        try (var rules = mockStatic(TrainingRules.class, CALLS_REAL_METHODS)) {
            rules.when(() -> TrainingRules.injuryChance(anyInt())).thenReturn(0.0);
            var result = progress.advance(id, token(a), new AdvanceDaysRequest(initial.currentDate(), 7));
            assertThat(result.advancedDays()).isEqualTo(1);
            assertThat(result.stopReason()).contains("evento");
            assertThat(
                db.queryForObject(
                    "SELECT calendar_day FROM save_teams WHERE save_id=? AND team_id=?",
                    Integer.class,
                    id,
                    other
                )
            ).isEqualTo(1);
            assertThat(result.events()).anyMatch(
                e -> e.title().equals("Reunião da diretoria") && e.status().equals("COMPLETED")
            );
        }
    }

    @Test
    void trainingInjuriesAreRareAndFatigueRaisesRiskWithinABound() {
        assertThat(TrainingRules.injuryChance(100)).isEqualTo(.0006);
        assertThat(TrainingRules.injuryChance(40)).isBetween(.0006, .0018);
        assertThat(TrainingRules.injuryChance(0)).isEqualTo(.0018);
        int count = 0;
        for (int i = 0; i < 100000; i++) if (
            TrainingRules.random("sample", 1, i, LocalDate.of(2026, 7, 1)).nextDouble() <
            TrainingRules.injuryChance(100)
        ) count++;
        assertThat(count).isBetween(25, 110);
    }
}

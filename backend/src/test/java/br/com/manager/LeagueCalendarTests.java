package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
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
class LeagueCalendarTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    LeagueCalendarService calendar;

    @Autowired
    ManagementService management;

    @Autowired
    JdbcTemplate db;

    @Test
    void rollsOverFromSundayToMondayWithOnlySevenDaysAndNoWallClockDependency() {
        var access = saves.create(
            new CreateLeagueRequest("Calendário", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        db.update("UPDATE game_saves SET calendar_start_date=DATE '2026-12-28',round_number=7 WHERE id=?", id);
        var sunday = calendar.week(id, token);
        assertThat(sunday.currentDate()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(sunday.weekStart()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(sunday.weekEnd()).isEqualTo(sunday.weekStart().plusDays(6));
        db.update("UPDATE game_saves SET round_number=8 WHERE id=?", id);
        var monday = calendar.week(id, token);
        assertThat(monday.currentDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(monday.weekStart()).isEqualTo(monday.currentDate());
        assertThat(monday.weekEnd()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(calendar.week(id, token)).isEqualTo(monday);
    }

    @Test
    void isolatesTeamAndLeagueEventsAndIncludesActualRecoveryForecast() {
        var teams = catalog.teams(null);
        var access = saves.create(new CreateLeagueRequest("Eventos", 1, "Treinador", teams.getFirst().id()));
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var other = saves.create(new CreateLeagueRequest("Outra liga", 1, "Outro treinador", teams.get(1).id()));
        var week = calendar.week(id, token);
        var date = week.currentDate().plusDays(1);
        db.update(
            "INSERT INTO league_calendar_events(save_id,team_id,event_date,event_type,title) VALUES(?,?,?,'CLUB','Evento do meu clube')",
            id,
            teams.getFirst().id(),
            date
        );
        db.update(
            "INSERT INTO league_calendar_events(save_id,event_date,event_type,title) VALUES(?,?,'CLUB','Evento da liga')",
            id,
            date
        );
        db.update(
            "INSERT INTO league_calendar_events(save_id,team_id,event_date,event_type,title) VALUES(?,?,?,'CLUB','Evento privado de outro clube')",
            id,
            teams.get(1).id(),
            date
        );
        db.update(
            "INSERT INTO league_calendar_events(save_id,event_date,event_type,title) VALUES(?,?,'CLUB','Evento de outra liga')",
            other.room().id(),
            date
        );
        db.update(
            "INSERT INTO league_calendar_events(save_id,event_date,event_type,title) VALUES(?,?,'MATCH','Próxima semana')",
            id,
            week.weekEnd().plusDays(1)
        );
        var player = management.state(id, token).players().getFirst();
        db.update(
            "UPDATE save_player_profiles SET injury_name='Lesão de teste',return_round=2 WHERE save_id=? AND player_id=?",
            id,
            player.id()
        );
        var result = calendar.week(id, token);
        assertThat(result.events())
            .extracting(e -> e.title())
            .contains("Evento do meu clube", "Evento da liga", "Retorno previsto: " + player.name())
            .doesNotContain("Evento privado de outro clube", "Evento de outra liga", "Próxima semana");
        assertThat(result.events()).allMatch(
            e -> !e.date().isBefore(result.weekStart()) && !e.date().isAfter(result.weekEnd())
        );
        saves.delete(id, token);
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM league_calendar_events WHERE save_id=?", Integer.class, id)
        ).isZero();
    }

    @Test
    void requiresMembershipAndReflectsReadinessWithoutInventingFixtures() {
        var access = saves.create(
            new CreateLeagueRequest("Agenda", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        assertThatThrownBy(() -> calendar.week(id, null)).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() -> calendar.week(id, "Bearer inválido")).isInstanceOf(DefaultErrorException.class);
        assertThat(calendar.week(id, token).events())
            .hasSize(1)
            .allMatch(e -> e.type().equals("PREPARATION") && e.status().equals("WAITING"));
        saves.ready(id, token, true);
        assertThat(calendar.week(id, token).events()).anyMatch(
            e -> e.type().equals("PREPARATION") && e.status().equals("READY")
        );
    }
}

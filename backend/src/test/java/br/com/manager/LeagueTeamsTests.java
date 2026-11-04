package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.manager.controller.LeagueTeamsController;
import br.com.manager.exception.DefaultErrorException;
import br.com.manager.exception.GlobalExceptionHandler;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.CatalogService;
import br.com.manager.service.LeagueTeamsService;
import br.com.manager.service.SaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeagueTeamsTests {

    @Autowired
    LeagueTeamsService teams;

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create(int capacity) {
        return saves.create(
            new CreateLeagueRequest("Consulta de clubes", capacity, "Treinador", catalog.teams(null).getFirst().id())
        );
    }

    @Test
    void listsSnapshotClubsIncludingInternationalTeamsAndTheirHumanManagers() {
        var owner = create(2);
        var rival = catalog.teams(null).get(1);
        saves.join(owner.room().inviteCode(), new JoinLeagueRequest("Amigo", rival.id()));
        var result = teams.teams(owner.room().id(), "Bearer " + owner.accessToken());
        assertThat(result.ownTeamId()).isEqualTo(owner.room().members().getFirst().teamId());
        assertThat(result.teams()).hasSize(
            db.queryForObject("SELECT COUNT(*) FROM save_teams WHERE save_id=?", Integer.class, owner.room().id())
        );
        var target = result
            .teams()
            .stream()
            .filter(t -> t.id().equals(rival.id()))
            .findFirst()
            .orElseThrow();
        assertThat(target.managerName()).isEqualTo("Amigo");
        assertThat(target.playerCount()).isEqualTo(catalog.players(rival.id()).size());
        assertThat(target.crestUrl()).isEqualTo("/api/teams/" + rival.id() + "/crest");
        var international = catalog
            .snapshot()
            .teams()
            .stream()
            .filter(t -> !t.selectable())
            .findFirst()
            .orElseThrow();
        assertThat(result.teams())
            .extracting(t -> t.id())
            .contains(international.id());
    }

    @Test
    void clubAndPlayerDetailsComeFromTheLeagueInsteadOfTheCurrentCatalogue() {
        var owner = create(1);
        var id = owner.room().id();
        var token = "Bearer " + owner.accessToken();
        var rival = catalog.teams(null).get(1);
        var player = teams.team(id, rival.id(), token).players().getFirst();
        db.update(
            "UPDATE save_teams SET name=?,rating=? WHERE save_id=? AND team_id=?",
            "Clube da liga",
            67,
            id,
            rival.id()
        );
        db.update(
            "UPDATE save_players SET name=?,ability=? WHERE save_id=? AND player_id=?",
            "Atleta da liga",
            66,
            id,
            player.id()
        );
        db.update("UPDATE catalog_teams SET name=?,rating=? WHERE id=?", "Clube atualizado", 90, rival.id());
        db.update("UPDATE catalog_players SET name=?,ability=? WHERE id=?", "Atleta atualizado", 99, player.id());
        var result = teams.team(id, rival.id(), token);
        assertThat(result.team().name()).isEqualTo("Clube da liga");
        assertThat(result.team().rating()).isEqualTo(67);
        assertThat(result.players()).anySatisfy(p -> {
            assertThat(p.id()).isEqualTo(player.id());
            assertThat(p.name()).isEqualTo("Atleta da liga");
            assertThat(p.ability()).isEqualTo(66);
        });
    }

    @Test
    void tokensFromAnotherLeagueAndMissingCredentialsCannotReadClubs() {
        var owner = create(1);
        var foreign = create(1);
        var rival = catalog.teams(null).get(1).id();
        for (var token : new String[] { null, "Bearer invalid", "Bearer " + foreign.accessToken() }) {
            assertThatThrownBy(() -> teams.teams(owner.room().id(), token))
                .isInstanceOf(DefaultErrorException.class)
                .extracting(e -> ((DefaultErrorException) e).getStatusCode().value())
                .isEqualTo(401);
            assertThatThrownBy(() -> teams.team(owner.room().id(), rival, token))
                .isInstanceOf(DefaultErrorException.class)
                .extracting(e -> ((DefaultErrorException) e).getStatusCode().value())
                .isEqualTo(401);
        }
    }

    @Test
    void consultationDoesNotExposeTacticsOrFinancesAndCannotFallBackToMasterTeams() throws Exception {
        var owner = create(1);
        var id = owner.room().id();
        var token = "Bearer " + owner.accessToken();
        var rival = catalog.teams(null).get(1).id();
        var json = JsonMapper.builder()
            .build()
            .writeValueAsString(teams.team(id, rival, token));
        assertThat(json).doesNotContain("budget", "formation", "lineup", "token", "email", "password");
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM save_team_management WHERE save_id=?", Integer.class, id)
        ).isZero();
        db.update("DELETE FROM save_players WHERE save_id=? AND team_id=?", id, rival);
        db.update("DELETE FROM save_teams WHERE save_id=? AND team_id=?", id, rival);
        assertThatThrownBy(() -> teams.team(id, rival, token))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(e -> ((DefaultErrorException) e).getStatusCode().value())
            .isEqualTo(404);
    }

    @Test
    void httpApiValidatesTeamIdsAndDoesNotAllowEditingOtherClubs() throws Exception {
        var service = mock(LeagueTeamsService.class);
        var http = MockMvcBuilders.standaloneSetup(new LeagueTeamsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        http.perform(get("/api/saves/league/teams/-1")).andExpect(status().isBadRequest());
        http.perform(get("/api/saves/league/teams/invalid")).andExpect(status().isBadRequest());
        http.perform(put("/api/saves/league/teams/1")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}

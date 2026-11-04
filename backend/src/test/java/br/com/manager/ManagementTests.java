package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.CatalogService;
import br.com.manager.service.ManagementService;
import br.com.manager.service.SaveService;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagementTests {

    @Autowired
    SaveService saves;

    @Autowired
    ManagementService management;

    @Autowired
    CatalogService catalog;

    @Autowired
    JdbcTemplate db;

    private LeagueAccessResponse create() {
        return saves.create(
            new CreateLeagueRequest("Gerenciamento", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
    }

    @Test
    void readsSnapshotAndPersistsTacticsAndElevenUniqueStarters() {
        var access = create();
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var state = management.state(id, token);
        assertThat(state.lineup()).hasSize(11).doesNotContain(0L).doesNotHaveDuplicates();
        assertThat(state.budget()).isEqualTo(10000000);
        assertThat(state.palette().primary()).matches("#[a-fA-F0-9]{6}");
        assertThat(state.palette().homeMethod()).isIn("official", "crest", "neutral");
        assertThat(state.palette().homeColors()).contains(state.palette().primary());
        var saved = management.update(
            id,
            token,
            new UpdateTeamRequest(state.formation(), "ATTACKING", state.lineup(), 0)
        );
        assertThat(saved.version()).isEqualTo(1);
        assertThat(management.state(id, token).mentality()).isEqualTo("ATTACKING");
        assertThat(management.state(id, token).lineup()).containsExactlyElementsOf(state.lineup());
    }

    @Test
    void playerNationalityComesFromTheLeagueSnapshot() {
        var access = create();
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var playerId = management.state(id, token).players().getFirst().id();
        db.update("UPDATE save_players SET nationality=? WHERE save_id=? AND player_id=?", "Brazil", id, playerId);
        db.update("UPDATE catalog_players SET nationality=? WHERE id=?", "Spain", playerId);
        assertThat(
            management
                .state(id, token)
                .players()
                .stream()
                .filter(p -> p.id().equals(playerId))
                .findFirst()
                .orElseThrow()
                .nationality()
        ).isEqualTo("Brazil");
        db.update("UPDATE save_players SET nationality=NULL WHERE save_id=? AND player_id=?", id, playerId);
        assertThat(
            management
                .state(id, token)
                .players()
                .stream()
                .filter(p -> p.id().equals(playerId))
                .findFirst()
                .orElseThrow()
                .nationality()
        ).isNull();
    }

    @Test
    void duplicateOrForeignPlayersAndInvalidFormationsAreRejected() {
        var access = create();
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var state = management.state(id, token);
        var invalid = new ArrayList<>(state.lineup());
        invalid.set(1, invalid.getFirst());
        assertThatThrownBy(() -> management.update(id, token, new UpdateTeamRequest("4-3-3", "BALANCED", invalid, 0)))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(400);
        invalid.set(1, Long.MAX_VALUE);
        assertThatThrownBy(() -> management.update(id, token, new UpdateTeamRequest("4-3-3", "BALANCED", invalid, 0)))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(400);
        assertThatThrownBy(() ->
            management.update(id, token, new UpdateTeamRequest("0-0-10", "BALANCED", state.lineup(), 0))
        )
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(400);
    }

    @Test
    void readyPlayersCannotEditAndStaleTabsCannotOverwriteTactics() {
        var access = create();
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var state = management.state(id, token);
        var input = new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), 0);
        management.update(id, token, input);
        assertThatThrownBy(() -> management.update(id, token, input))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(409);
        saves.ready(id, token, true);
        assertThatThrownBy(() ->
            management.update(id, token, new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), 1))
        )
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(409);
    }

    @Test
    void tokenFromAnotherSaveDoesNotExposeManagementData() {
        var first = create();
        var other = create();
        assertThatThrownBy(() -> management.state(first.room().id(), "Bearer " + other.accessToken()))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
    }
}

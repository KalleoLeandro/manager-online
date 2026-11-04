package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
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
class JuniorPlayersTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    @Autowired
    JuniorPlayersService juniors;

    @Autowired
    JdbcTemplate db;

    @Test
    void createsPersistentYoungerWeakerSquadWithFullProfilesAndCannotSelectForFirstTeam() {
        var access = saves.create(
            new CreateLeagueRequest("Base de teste", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var main = management.state(id, token);
        int lowest = main
            .players()
            .stream()
            .mapToInt(p -> p.ability())
            .min()
            .orElseThrow();
        var players = juniors.players(id, token);
        assertThat(players).hasSize(18);
        assertThat(players).allSatisfy(p -> {
            assertThat(p.id()).isGreaterThanOrEqualTo(1000000000L);
            assertThat(p.generated()).isTrue();
            assertThat(p.age()).isBetween(15, 18);
            if (p.level().equals("Júnior")) assertThat(p.ability()).isLessThan(lowest);
            else assertThat(p.ability()).isLessThan(
                (int) Math.floor(
                    main
                        .players()
                        .stream()
                        .mapToInt(player -> player.ability())
                        .average()
                        .orElseThrow()
                )
            );
            assertThat(p.technical().attributes()).hasSize(40);
            assertThat(p.technical().attributes().values()).allMatch(v -> v >= 1 && v <= 99);
        });
        assertThat(
            players
                .stream()
                .filter(p -> p.position().equals("GOL"))
                .count()
        ).isEqualTo(2);
        assertThat(juniors.players(id, token)).isEqualTo(players);
        assertThat(management.state(id, token).players()).isEqualTo(main.players());
        var invalid = new ArrayList<>(main.lineup());
        invalid.set(0, players.getFirst().id());
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(main.formation(), main.mentality(), invalid, main.version())
            )
        ).isInstanceOf(DefaultErrorException.class);
        saves.delete(id, token);
        assertThat(
            db.queryForObject("SELECT COUNT(*) FROM league_juniors WHERE save_id=?", Integer.class, id)
        ).isZero();
    }

    @Test
    void isolatesLeaguesAndRejectsUnauthorizedAccess() {
        var team = catalog.teams(null).getFirst().id();
        var first = saves.create(new CreateLeagueRequest("Base A", 1, "Treinador A", team));
        var second = saves.create(new CreateLeagueRequest("Base B", 1, "Treinador B", team));
        var a = juniors.players(first.room().id(), "Bearer " + first.accessToken());
        var b = juniors.players(second.room().id(), "Bearer " + second.accessToken());
        assertThat(
            a
                .stream()
                .map(p -> p.id())
                .toList()
        ).doesNotContainAnyElementsOf(
            b
                .stream()
                .map(p -> p.id())
                .toList()
        );
        assertThatThrownBy(() -> juniors.players(first.room().id(), "Bearer " + second.accessToken())).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() -> juniors.players(first.room().id(), null)).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void searchesOnlyLeagueSnapshotsAndTreatsWildcardsLiterally() {
        var access = saves.create(
            new CreateLeagueRequest("Busca", 1, "Treinador", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var player = management.state(id, token).players().getFirst();
        db.update("UPDATE save_players SET name='AtletaBuscaUnico' WHERE save_id=? AND player_id=?", id, player.id());
        var results = management.search(id, token, "BuscaUnico");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().player().id()).isEqualTo(player.id());
        assertThat(results.getFirst().teamName()).isNotBlank();
        assertThat(management.search(id, token, "%%")).isEmpty();
        assertThatThrownBy(() -> management.search(id, token, "a")).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() -> management.search(id, null, "BuscaUnico")).isInstanceOf(DefaultErrorException.class);
    }
}

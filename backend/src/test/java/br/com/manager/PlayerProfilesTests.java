package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.service.CatalogService;
import br.com.manager.service.ManagementService;
import br.com.manager.service.PlayerProfilesService;
import br.com.manager.service.SaveService;
import br.com.manager.utils.PlayerCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerProfilesTests {

    @Autowired
    CatalogService catalog;

    @Autowired
    SaveService saves;

    @Autowired
    ManagementService management;

    @Autowired
    PlayerProfilesService profiles;

    @Autowired
    JdbcTemplate db;

    @Test
    void profilesHaveFortyBoundedSkillsAndDistinguishSourceFromEstimates() {
        var players = catalog.snapshot().players();
        assertThat(players).isNotEmpty();
        for (var player : players) {
            var profile = player.technical();
            assertThat(profile.attributes()).hasSize(40);
            assertThat(profile.attributes().values()).allMatch(v -> v >= 1 && v <= 99);
            assertThat(profile.potential()).isBetween(player.ability(), 99);
            assertThat(profile.attributes().keySet()).containsAll(profile.sourcedAttributes());
        }
        assertThat(players).anyMatch(p -> !p.technical().sourcedAttributes().isEmpty());
        assertThat(players).anyMatch(p -> p.technical().sourcedAttributes().isEmpty());
    }

    @Test
    void leagueSkillsAreIndependentOfCatalogAndOtherLeagues() {
        var team = catalog.teams(null).getFirst();
        var first = saves.create(new CreateLeagueRequest("Perfil A", 1, "Treinador A", team.id()));
        var second = saves.create(new CreateLeagueRequest("Perfil B", 1, "Treinador B", team.id()));
        var player = management
            .state(first.room().id(), "Bearer " + first.accessToken())
            .players()
            .getFirst();
        var original = player.technical().attributes().get("dribbling");
        db.update(
            "UPDATE save_player_profiles SET dribbling=1 WHERE save_id=? AND player_id=?",
            first.room().id(),
            player.id()
        );
        db.update("UPDATE catalog_player_profiles SET dribbling=99 WHERE player_id=?", player.id());
        assertThat(
            management
                .state(first.room().id(), "Bearer " + first.accessToken())
                .players()
                .stream()
                .filter(p -> p.id().equals(player.id()))
                .findFirst()
                .orElseThrow()
                .technical()
                .attributes()
                .get("dribbling")
        ).isEqualTo(1);
        assertThat(
            management
                .state(second.room().id(), "Bearer " + second.accessToken())
                .players()
                .stream()
                .filter(p -> p.id().equals(player.id()))
                .findFirst()
                .orElseThrow()
                .technical()
                .attributes()
                .get("dribbling")
        ).isEqualTo(original);
    }

    @Test
    void backfillUsesSnapshotAbilityAndNeverResetsExistingProgress() {
        var access = saves.create(
            new CreateLeagueRequest("Migração", 1, "Treinador A", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var player = management
            .state(id, "Bearer " + access.accessToken())
            .players()
            .getFirst();
        db.update("DELETE FROM save_player_profiles WHERE save_id=? AND player_id=?", id, player.id());
        db.update("UPDATE save_players SET ability=50 WHERE save_id=? AND player_id=?", id, player.id());
        profiles.initialize();
        assertThat(
            db.queryForObject(
                "SELECT reference_ability FROM save_player_profiles WHERE save_id=? AND player_id=?",
                Integer.class,
                id,
                player.id()
            )
        ).isEqualTo(50);
        db.update("UPDATE save_player_profiles SET finishing=98 WHERE save_id=? AND player_id=?", id, player.id());
        profiles.initialize();
        assertThat(
            db.queryForObject(
                "SELECT finishing FROM save_player_profiles WHERE save_id=? AND player_id=?",
                Integer.class,
                id,
                player.id()
            )
        ).isEqualTo(98);
    }

    @Test
    void injuryBlocksSelectionAndReadinessWithoutChangingSkillsAndExpiresAtReturnRound() {
        var access = saves.create(
            new CreateLeagueRequest("Condição", 1, "Treinador A", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var state = management.state(id, token);
        var starter = state.lineup().getFirst();
        var base = state
            .players()
            .stream()
            .filter(p -> p.id().equals(starter))
            .findFirst()
            .orElseThrow()
            .technical();
        management.update(
            id,
            token,
            new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), state.version())
        );
        db.update(
            "UPDATE save_player_profiles SET injury_name='Lesão de teste',return_round=3 WHERE save_id=? AND player_id=?",
            id,
            starter
        );
        var injuredState = management.state(id, token);
        var injured = injuredState
            .players()
            .stream()
            .filter(p -> p.id().equals(starter))
            .findFirst()
            .orElseThrow();
        assertThat(injured.technical()).isEqualTo(base);
        assertThat(injured.condition().available()).isFalse();
        assertThat(injured.condition().effectiveAbility()).isZero();
        assertThatThrownBy(() ->
            management.update(
                id,
                token,
                new UpdateTeamRequest(state.formation(), state.mentality(), state.lineup(), injuredState.version())
            )
        )
            .isInstanceOf(DefaultErrorException.class)
            .extracting(e -> ((DefaultErrorException) e).getStatusCode().value())
            .isEqualTo(400);
        assertThatThrownBy(() -> saves.ready(id, token, true))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(e -> ((DefaultErrorException) e).getStatusCode().value())
            .isEqualTo(409);
        db.update("UPDATE game_saves SET round_number=3 WHERE id=?", id);
        assertThat(saves.ready(id, token, true).status()).isEqualTo("READY");
        assertThat(
            management
                .state(id, token)
                .players()
                .stream()
                .filter(p -> p.id().equals(starter))
                .findFirst()
                .orElseThrow()
                .condition()
                .available()
        ).isTrue();
    }

    @Test
    void suggestedLineupSkipsPlayersWithoutCondition() {
        var access = saves.create(
            new CreateLeagueRequest("Sugestão", 1, "Treinador A", catalog.teams(null).getFirst().id())
        );
        var id = access.room().id();
        var token = "Bearer " + access.accessToken();
        var starter = management.state(id, token).lineup().getFirst();
        db.update("UPDATE save_players SET fitness=39 WHERE save_id=? AND player_id=?", id, starter);
        assertThat(management.state(id, token).lineup()).doesNotContain(starter);
    }

    @Test
    void temporaryConditionAffectsPerformanceAndRespectsAvailabilityBoundaries() {
        assertThat(PlayerCondition.evaluate(80, 100, 100, 100, null, null, 1).effectiveAbility()).isEqualTo(80);
        var tired = PlayerCondition.evaluate(80, 70, 100, 100, null, null, 1);
        assertThat(tired.available()).isTrue();
        assertThat(tired.effectiveAbility()).isLessThan(80);
        assertThat(PlayerCondition.evaluate(80, 39, 100, 100, null, null, 1).available()).isFalse();
        assertThat(PlayerCondition.evaluate(80, 40, 100, 100, null, null, 1).available()).isTrue();
        assertThat(PlayerCondition.evaluate(80, 100, 100, 100, "Lesão", null, 99).available()).isFalse();
        assertThat(PlayerCondition.evaluate(80, 100, 100, 100, "Lesão", 3, 2).available()).isFalse();
        assertThat(PlayerCondition.evaluate(80, 100, 100, 100, "Lesão", 3, 3).available()).isTrue();
    }
}

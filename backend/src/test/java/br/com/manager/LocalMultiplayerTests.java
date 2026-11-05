package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalMultiplayerTests {

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    @Test
    void localLeagueAllowsEightDistinctTeamsAndRequiresOwnerToRegisterThem() {
        var teams = catalog.teams(null);
        assertThatThrownBy(() ->
            saves.create(new CreateLeagueRequest("Local", 9, "Criador teste", teams.get(0).id(), true))
        ).isInstanceOf(DefaultErrorException.class);
        var a = saves.create(new CreateLeagueRequest("Local", 8, "Criador teste", teams.get(0).id(), true));
        var id = a.room().id();
        var header = "Bearer " + a.accessToken();
        assertThatThrownBy(() ->
            saves.join(a.room().inviteCode(), new JoinLeagueRequest("Intruso teste", teams.get(1).id()))
        ).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() ->
            saves.addLocal(id, header, new JoinLeagueRequest("Clube repetido", teams.get(0).id()))
        ).isInstanceOf(DefaultErrorException.class);
        var b = saves.addLocal(id, header, new JoinLeagueRequest("Segundo técnico", teams.get(1).id()));
        assertThatThrownBy(() ->
            saves.addLocal(id, "Bearer " + b.accessToken(), new JoinLeagueRequest("Outro técnico", teams.get(2).id()))
        ).isInstanceOf(DefaultErrorException.class);
        for (int i = 2; i < 8; i++) saves.addLocal(
            id,
            header,
            new JoinLeagueRequest("Treinador " + i, teams.get(i).id())
        );
        assertThat(saves.room(id).members()).hasSize(8);
        assertThatThrownBy(() ->
            saves.addLocal(id, header, new JoinLeagueRequest("Nono técnico", teams.get(8).id()))
        ).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void switchKeepsTeamsSeparateAndPreservesOwnerAccess() {
        var teams = catalog.teams(null);
        var a = saves.create(new CreateLeagueRequest("Local", 2, "Criador teste", teams.get(0).id(), true));
        var id = a.room().id();
        var header = "Bearer " + a.accessToken();
        var b = saves.addLocal(id, header, new JoinLeagueRequest("Segundo técnico", teams.get(1).id()));
        var switched = saves.switchLocal(id, header, b.memberId());
        assertThat(management.state(id, "Bearer " + switched.accessToken()).teamId()).isEqualTo(teams.get(1).id());
        assertThat(management.state(id, header).teamId()).isEqualTo(teams.get(0).id());
        assertThatThrownBy(() -> saves.summary(id, "Bearer " + b.accessToken())).isInstanceOf(
            DefaultErrorException.class
        );
        assertThat(saves.switchLocal(id, header, a.memberId()).accessToken()).isEqualTo(a.accessToken());
        assertThatThrownBy(() -> saves.switchLocal(id, "Bearer " + switched.accessToken(), a.memberId())).isInstanceOf(
            DefaultErrorException.class
        );
        var other = saves.create(new CreateLeagueRequest("Outra local", 2, "Outro criador", teams.get(2).id(), true));
        assertThatThrownBy(() -> saves.switchLocal(id, header, other.memberId())).isInstanceOf(
            DefaultErrorException.class
        );
        var online = saves.create(new CreateLeagueRequest("Online", 2, "Criador online", teams.get(0).id()));
        assertThatThrownBy(() ->
            saves.addLocal(
                online.room().id(),
                "Bearer " + online.accessToken(),
                new JoinLeagueRequest("Outro técnico", teams.get(1).id())
            )
        ).isInstanceOf(DefaultErrorException.class);
    }
}

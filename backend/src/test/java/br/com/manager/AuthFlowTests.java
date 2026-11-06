package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.controller.RegistrationController;
import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.RegistrationRequest;
import br.com.manager.models.responses.SessionResponse;
import br.com.manager.service.AuthService;
import br.com.manager.service.CatalogService;
import br.com.manager.service.SaveService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthFlowTests {

    @Autowired
    RegistrationController registration;

    @Autowired
    AuthService auth;

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    private SessionResponse account() {
        var email = UUID.randomUUID() + "@example.com";
        registration.register(
            new RegistrationRequest("Treinador", email, "SenhaTeste123!", "SenhaTeste123!", null, "Teste")
        );
        return auth.login(email, "SenhaTeste123!");
    }

    @Test
    void logoutRevokesSessionAndLegacyLeagueTokensWithoutDeletingLeagueOrOtherSessions(){
        String email=UUID.randomUUID()+"@example.com";
        registration.register(new RegistrationRequest("Coach",email,"Password123!","Password123!",null,"Test"));
        var session=auth.login(email,"Password123!");var otherSession=auth.login(email,"Password123!");
        String header="Bearer "+session.accessToken();
        var league=saves.createForAccount(new CreateLeagueRequest("Logout league",1,"Coach",catalog.teams(null).getFirst().id()),header);
        assertThat(saves.authorizedRoom(league.room().id(),"Bearer "+league.accessToken())).isNotNull();
        auth.logout(header);
        assertThat(auth.find(session.accessToken())).isNull();
        assertThatThrownBy(()->saves.mine(header)).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(()->saves.authorizedRoom(league.room().id(),"Bearer "+league.accessToken())).isInstanceOf(DefaultErrorException.class);
        assertThat(saves.mine("Bearer "+otherSession.accessToken())).hasSize(1);
        var loginAgain=auth.login(email,"Password123!");
        assertThat(saves.mine("Bearer "+loginAgain.accessToken()).getFirst().room().id()).isEqualTo(league.room().id());
        assertThatCode(()->auth.logout(header)).doesNotThrowAnyException();
    }

    @Test
    void newAccountHasNoSavesAndCreatedSaveBelongsToAccount() {
        var owner = account();
        var header = "Bearer " + owner.accessToken();
        assertThat(saves.mine(header)).isEmpty();
        var created = saves.createForAccount(
            new CreateLeagueRequest("Meu campeonato", 2, "Nome enviado", catalog.teams(null).getFirst().id()),
            header
        );
        assertThat(saves.mine(header)).hasSize(1);
        assertThat(saves.mine(header).getFirst().room().id()).isEqualTo(created.room().id());
        assertThat(created.room().members().getFirst().name()).isEqualTo(owner.name());
        var other = account();
        assertThat(saves.mine("Bearer " + other.accessToken())).isEmpty();
        assertThatThrownBy(() ->
            saves.authorizedRoom(created.room().id(), "Bearer " + other.accessToken())
        ).isInstanceOf(DefaultErrorException.class);
        assertThat(saves.authorizedRoom(created.room().id(), header).id()).isEqualTo(created.room().id());
    }

    @Test
    void invalidCredentialsAndMissingSessionAreRejected() {
        var email = UUID.randomUUID() + "@example.com";
        registration.register(
            new RegistrationRequest("Treinador", email, "SenhaTeste123!", "SenhaTeste123!", null, "Teste")
        );
        assertThatThrownBy(() -> auth.login(email, "senha errada"))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
        assertThatThrownBy(() -> saves.mine(null))
            .isInstanceOf(DefaultErrorException.class)
            .extracting(error -> ((DefaultErrorException) error).getStatusCode().value())
            .isEqualTo(401);
    }
}

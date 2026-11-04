package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.RegistrationRequest;
import br.com.manager.service.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerNamesTests {

    @Autowired
    RegistrationService registration;

    @Autowired
    AuthService auth;

    @Test
    void persistsCompoundFirstAndLastNamesSeparatelyAndKeepsFullName() {
        var email = UUID.randomUUID() + "@example.com";
        var result = registration.register(
            new RegistrationRequest(" João   Pedro ", email, "SenhaTeste123!", "SenhaTeste123!", null, " da  Silva ")
        );
        assertThat(result.firstName()).isEqualTo("João Pedro");
        assertThat(result.lastName()).isEqualTo("da Silva");
        assertThat(result.name()).isEqualTo("João Pedro da Silva");
        var session = auth.login(email, "SenhaTeste123!");
        assertThat(session.firstName()).isEqualTo(result.firstName());
        assertThat(session.lastName()).isEqualTo(result.lastName());
        assertThat(session.name()).isEqualTo(result.name());
        var manager = auth.require("Bearer " + session.accessToken());
        assertThat(manager.firstName()).isEqualTo(result.firstName());
        assertThat(manager.lastName()).isEqualTo(result.lastName());
    }

    @Test
    void refusesMissingOrBlankSurnameAndOverlongFullName() {
        for (var surname : new String[] { null, "  ", "S".repeat(40) })
            assertThatThrownBy(() ->
                registration.register(
                    new RegistrationRequest(
                        "João",
                        UUID.randomUUID() + "@example.com",
                        "SenhaTeste123!",
                        "SenhaTeste123!",
                        null,
                        surname
                    )
                )
            ).isInstanceOf(DefaultErrorException.class);
    }
}

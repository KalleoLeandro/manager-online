package br.com.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.manager.controller.RegistrationController;
import br.com.manager.controller.SaveController;
import br.com.manager.exception.DefaultErrorException;
import br.com.manager.exception.GlobalExceptionHandler;
import br.com.manager.service.RegistrationService;
import br.com.manager.service.SaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTests {

    private RegistrationService registration;
    private SaveService saves;
    private MockMvc http;
    private static final String VALID = """
    {"name":"Treinador","lastName":"Teste","email":"test@example.com","password":"Senha123!","passwordConfirmation":"Senha123!"}
    """;

    @BeforeEach
    void setup() {
        registration = mock(RegistrationService.class);
        saves = mock(SaveService.class);
        http = MockMvcBuilders.standaloneSetup(new RegistrationController(registration), new SaveController(saves))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void businessErrorPreservesStatusAndPublicMessage() throws Exception {
        when(registration.register(any())).thenThrow(
            new DefaultErrorException(HttpStatus.CONFLICT, "Conta já cadastrada.")
        );
        http.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Conta já cadastrada."))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void databaseTimeoutReturnsGatewayTimeoutWithoutSqlDetails() throws Exception {
        when(registration.register(any())).thenThrow(
            new org.springframework.dao.QueryTimeoutException("SELECT private-secret")
        );
        http.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("foi cancelada")))
            .andExpect(
                content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret")))
            );
    }

    @Test
    void lockTimeoutReturnsServiceUnavailableWithRetryHint() throws Exception {
        when(registration.register(any())).thenThrow(
            new org.springframework.dao.CannotAcquireLockException("private-secret")
        );
        http.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "3"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ocupado")));
    }

    @Test
    void postgresCancellationStatesAreHandledEvenWhenJdbcDoesNotTranslateThem() throws Exception {
        for (var state : new String[] { "55P03", "57014" }) {
            doThrow(
                new org.springframework.jdbc.UncategorizedSQLException(
                    "query",
                    "private-secret",
                    new java.sql.SQLException("private-secret", state)
                )
            ).when(registration).register(any());
            http.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().is(state.equals("55P03") ? 503 : 504))
                .andExpect(
                    content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret")))
                );
        }
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalDetails() throws Exception {
        when(registration.register(any())).thenThrow(new IllegalStateException("SQL password=private-secret"));
        http.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"message\":\"Não foi possível processar a solicitação. Tente novamente.\"}"));
    }

    @Test
    void mismatchedPasswordsAreRejectedBeforeService() throws Exception {
        http.perform(
            post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    VALID.replace("\"passwordConfirmation\":\"Senha123!\"", "\"passwordConfirmation\":\"Outra123!\"")
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Confira os campos e tente novamente."));
        verifyNoInteractions(registration);
    }

    @Test
    void malformedJsonDoesNotEchoSubmittedSecrets() throws Exception {
        http.perform(
            post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content("{\"password\":private-secret")
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"message\":\"Confira os campos e tente novamente.\"}"));
        verifyNoInteractions(registration);
    }

    @Test
    void missingReadyIsRejectedRatherThanChangingStateToFalse() throws Exception {
        http.perform(put("/api/saves/league/ready").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(
            status().isBadRequest()
        );
        verifyNoInteractions(saves);
    }

    @Test
    void frameworkErrorsKeepTheirStatusAndHeaders() throws Exception {
        http.perform(delete("/api/registrations"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().exists("Allow"))
            .andExpect(jsonPath("$.message").value("Método não permitido."));
        http.perform(post("/api/registrations").contentType(MediaType.TEXT_PLAIN).content("private-secret"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("Formato de requisição não suportado."));
    }
}

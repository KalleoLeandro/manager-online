package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManagerPhotoIntroductionTests {

    @Autowired
    RegistrationService registration;

    @Autowired
    AuthService auth;

    @Autowired
    ManagerPhotoService photos;

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    ManagementService management;

    private String png(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    @Test
    void optionalPhotoPersistsAndIsReturnedAfterLogin() throws Exception {
        var email = UUID.randomUUID() + "@example.com";
        var manager = registration.register(
            new RegistrationRequest(
                "Treinador com foto",
                email,
                "SenhaTeste123!",
                "SenhaTeste123!",
                png(64, 64),
                "Teste"
            )
        );
        var session = auth.login(email, "SenhaTeste123!");
        assertThat(session.photoUrl()).isEqualTo("/api/managers/" + manager.id() + "/photo");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(photos.photo(manager.id()))).getWidth()).isEqualTo(64);
        var noPhotoEmail = UUID.randomUUID() + "@example.com";
        registration.register(
            new RegistrationRequest("Sem foto", noPhotoEmail, "SenhaTeste123!", "SenhaTeste123!", null, "Teste")
        );
        assertThat(auth.login(noPhotoEmail, "SenhaTeste123!").photoUrl()).isNull();
    }

    @Test
    void rejectsFakeOversizedAndOverDimensionedPhotosBeforeCreatingAccount() throws Exception {
        assertThatThrownBy(() -> photos.validate("data:image/svg+xml;base64,PHN2Zz4=")).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() -> photos.validate("data:image/png;base64,bm90YW5pbWFnZQ==")).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() -> photos.validate("data:image/png;base64," + "A".repeat(350000))).isInstanceOf(
            DefaultErrorException.class
        );
        var huge = png(1025, 1);
        assertThatThrownBy(() -> photos.validate(huge)).isInstanceOf(DefaultErrorException.class);
        var email = UUID.randomUUID() + "@example.com";
        assertThatThrownBy(() ->
            registration.register(
                new RegistrationRequest(
                    "Inválido",
                    email,
                    "SenhaTeste123!",
                    "SenhaTeste123!",
                    "data:image/png;base64,bm90YW5pbWFnZQ==",
                    "Teste"
                )
            )
        ).isInstanceOf(DefaultErrorException.class);
        registration.register(
            new RegistrationRequest("Válido", email, "SenhaTeste123!", "SenhaTeste123!", null, "Teste")
        );
    }

    @Test
    void announcementIsAcknowledgedOncePerParticipantAndLeague() {
        var teams = catalog.teams(null);
        var owner = saves.create(new CreateLeagueRequest("Jornal", 2, "Treinador", teams.getFirst().id()));
        var friend = saves.join(owner.room().inviteCode(), new JoinLeagueRequest("Amigo", teams.get(1).id()));
        var id = owner.room().id();
        var token = "Bearer " + owner.accessToken();
        var other = "Bearer " + friend.accessToken();
        assertThat(management.state(id, token).introductionSeen()).isFalse();
        management.acknowledgeIntroduction(id, token);
        management.acknowledgeIntroduction(id, token);
        assertThat(management.state(id, token).introductionSeen()).isTrue();
        assertThat(management.state(id, other).introductionSeen()).isFalse();
        var next = saves.create(new CreateLeagueRequest("Outro jornal", 1, "Treinador", teams.getFirst().id()));
        assertThat(management.state(next.room().id(), "Bearer " + next.accessToken()).introductionSeen()).isFalse();
        assertThatThrownBy(() -> management.acknowledgeIntroduction(id, null)).isInstanceOf(
            DefaultErrorException.class
        );
        assertThatThrownBy(() -> management.acknowledgeIntroduction(id, "Bearer " + next.accessToken())).isInstanceOf(
            DefaultErrorException.class
        );
    }
}

package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.manager.controller.StadiumImageController;
import br.com.manager.exception.*;
import br.com.manager.repository.StadiumImageRepository;
import br.com.manager.service.StadiumImageService;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StadiumImageTests {

    @TempDir
    Path images;

    private StadiumImageService service() {
        return new StadiumImageService(new StadiumImageRepository(images.toString()));
    }

    @Test
    void servesPhotoForTheRequestedClubWithTheCorrectTypeAndLocalEtag() throws Exception {
        ImageIO.write(new BufferedImage(24, 12, BufferedImage.TYPE_INT_RGB), "jpg", images.resolve("98.jpg").toFile());
        var http = MockMvcBuilders.standaloneSetup(new StadiumImageController(service()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        http.perform(get("/api/teams/98/stadium"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("ETag"));
        http.perform(get("/api/teams/99/stadium")).andExpect(status().isNotFound());
    }

    @Test
    void refusesInvalidIdsAndFilesThatAreNotImages() throws Exception {
        Files.writeString(images.resolve("98.jpg"), "<html>Not a photograph</html>");
        assertThatThrownBy(() -> service().photo("98")).isInstanceOf(DefaultErrorException.class);
        for (var id : new String[] { "../98", "0", "-1", "98.jpg", "generic" })
            assertThatThrownBy(() -> service().photo(id)).isInstanceOf(DefaultErrorException.class);
    }

    @Test
    void supportsPngAndLocalAttributionWithoutExternalRequests() throws Exception {
        ImageIO.write(new BufferedImage(24, 12, BufferedImage.TYPE_INT_RGB), "png", images.resolve("1.png").toFile());
        assertThat(service().photo("1").mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        Files.writeString(images.resolve("credits.json"), "[{\"teamId\":1,\"author\":\"Photographer\"}]");
        var http = MockMvcBuilders.standaloneSetup(new StadiumImageController(service()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        http.perform(get("/api/stadiums/credits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].author").value("Photographer"));
    }
}

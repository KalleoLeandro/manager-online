package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.controller.PlayerPortraitController;
import br.com.manager.service.LocalImageService;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlayerPortraitTests {

    @Autowired
    LocalImageService imageService;

    @TempDir
    static Path images;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.portraits.directory", () -> images.toString());
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<byte[]> get(String id, String tag) throws Exception {
        var request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/players/" + id + "/portrait")
        );
        if (tag != null) request.header("If-None-Match", tag);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void manualPngAppearsImmediatelyAndReplacesCachedGeneric() throws Exception {
        var missing = get("999993", null);
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(missing.headers().firstValue("X-Portrait-Source")).contains("generic");
        assertThat(missing.headers().firstValue("Cache-Control")).contains("no-cache");
        byte[] png = new ClassPathResource("res/player-generic.png").getContentAsByteArray();
        Files.write(images.resolve("999993.png"), png);
        var local = get("999993", missing.headers().firstValue("ETag").orElseThrow());
        assertThat(local.statusCode()).isEqualTo(200);
        assertThat(local.headers().firstValue("X-Portrait-Source")).contains("local");
        assertThat(local.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(local.body()).isEqualTo(png);
        assertThat(get("999993", local.headers().firstValue("ETag").orElseThrow()).statusCode()).isEqualTo(304);
        Files.delete(images.resolve("999993.png"));
        assertThat(get("999993", null).headers().firstValue("X-Portrait-Source")).contains("generic");
    }

    @Test
    void invalidPngUsesGenericAndInvalidIdsCannotReadPaths() throws Exception {
        Files.writeString(images.resolve("999994.png"), "not a PNG");
        assertThat(get("999994", null).headers().firstValue("X-Portrait-Source")).contains("generic");
        var controller = new PlayerPortraitController(imageService);
        assertThatThrownBy(() -> controller.portrait("../football")).isInstanceOf(
            br.com.manager.exception.DefaultErrorException.class
        );
    }
}

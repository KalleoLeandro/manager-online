package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.controller.ClubCrestController;
import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.service.CatalogService;
import br.com.manager.service.LocalImageService;
import br.com.manager.service.ManagementService;
import br.com.manager.service.SaveService;
import br.com.manager.utils.AssetUrls;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClubCrestTests {

    @Test
    void mutableCataloguesAreNotHiddenByAnOldVersionEtag() throws Exception {
        var version = catalog.version();
        for (var url : java.util.List.of("/api/catalog", "/api/catalog/teams/2029/players")) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + url))
                .header("If-None-Match", "\"" + version + "\"")
                .GET()
                .build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
            assertThat(response.headers().firstValue("ETag")).isEmpty();
        }
    }

    @Autowired
    LocalImageService imageService;

    @TempDir
    static Path images;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.crests.directory", () -> images.toString());
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    CatalogService catalog;

    @Autowired
    SaveService saves;

    @Autowired
    ManagementService management;

    @Autowired
    JdbcTemplate db;

    private HttpResponse<byte[]> get(String id, String tag) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/teams/" + id + "/crest"));
        if (tag != null) request.header("If-None-Match", tag);
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void localCrestReplacesGenericWithoutRestartAndSupportsConditionalCache() throws Exception {
        var missing = get("999991", null);
        assertThat(missing.statusCode()).isEqualTo(200);
        assertThat(missing.headers().firstValue("X-Crest-Source")).contains("generic");
        byte[] png = new ClassPathResource("res/club-generic.png").getContentAsByteArray();
        Files.write(images.resolve("999991.png"), png);
        var local = get("999991", missing.headers().firstValue("ETag").orElseThrow());
        assertThat(local.statusCode()).isEqualTo(200);
        assertThat(local.headers().firstValue("X-Crest-Source")).contains("local");
        assertThat(local.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(local.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(local.body()).isEqualTo(png);
        assertThat(get("999991", local.headers().firstValue("ETag").orElseThrow()).statusCode()).isEqualTo(304);
        Files.delete(images.resolve("999991.png"));
    }

    @Test
    void corruptImagesAndPathTraversalNeverExposeFilesOrRedirectOutside() throws Exception {
        Files.writeString(images.resolve("999992.png"), "not a PNG");
        var result = get("999992", null);
        assertThat(result.headers().firstValue("X-Crest-Source")).contains("generic");
        assertThat(result.headers().firstValue("Location")).isEmpty();
        assertThat(get("invalid.id", null).statusCode()).isEqualTo(404);
        assertThatThrownBy(() -> new ClubCrestController(imageService).crest("../football")).isInstanceOf(
            br.com.manager.exception.DefaultErrorException.class
        );
    }

    @Test
    @Transactional
    void catalogAndOldLeagueSnapshotsExposeOnlyLocalCrestUrls() {
        assertThat(catalog.teams(null)).allSatisfy(t -> assertThat(t.crestUrl()).isEqualTo(AssetUrls.crest(t.id())));
        var team = catalog.teams(null).getFirst();
        var access = saves.create(new CreateLeagueRequest("Escudos locais", 1, "Treinador", team.id()));
        db.update(
            "UPDATE save_teams SET crest_url=? WHERE save_id=? AND team_id=?",
            "https://external.invalid/legacy.png",
            access.room().id(),
            team.id()
        );
        var room = saves.room(access.room().id());
        assertThat(room.members().getFirst().crestUrl()).isEqualTo(AssetUrls.crest(team.id()));
        assertThat(management.state(room.id(), "Bearer " + access.accessToken()).crestUrl()).isEqualTo(
            AssetUrls.crest(team.id())
        );
    }
}

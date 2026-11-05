package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.repository.LocalImageRepository;
import br.com.manager.service.LocalImageService;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

class TrophyImageTests {

    @TempDir
    Path directory;

    @Test
    void missingAndCorruptImagesUsePackagedFallbackAndManualReplacementWorks() throws Exception {
        var service = new LocalImageService(
            new LocalImageRepository(directory.toString(), directory.toString(), directory.toString())
        );
        var missing = service.trophy("30");
        assertThat(missing.local()).isFalse();
        assertThat(missing.resource().exists()).isTrue();
        Files.writeString(directory.resolve("30.png"), "not an image");
        assertThat(service.trophy("30").local()).isFalse();
        var png = new ClassPathResource("res/trophy-generic.png").getContentAsByteArray();
        Files.write(directory.resolve("30.png"), png);
        var local = service.trophy("30");
        assertThat(local.local()).isTrue();
        assertThat(local.resource().getContentAsByteArray()).isEqualTo(png);
        assertThat(local.etag()).isNotEqualTo(missing.etag());
        assertThatThrownBy(() -> service.trophy("../application")).isInstanceOf(DefaultErrorException.class);
        assertThatThrownBy(() -> service.trophy("30.svg")).isInstanceOf(DefaultErrorException.class);
    }
}

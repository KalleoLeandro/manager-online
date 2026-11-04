package br.com.manager.repository;

import br.com.manager.models.dto.LocalImageDto;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Repository;

@Repository
public class LocalImageRepository {

    private static final byte[] PNG_SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
    private final Path portraits;
    private final Path crests;

    public LocalImageRepository(
        @Value("${app.portraits.directory}") String portraits,
        @Value("${app.crests.directory}") String crests
    ) {
        this.portraits = Path.of(portraits).toAbsolutePath().normalize();
        this.crests = Path.of(crests).toAbsolutePath().normalize();
    }

    public LocalImageDto portrait(String id) throws IOException {
        return read(portraits, id, "player-generic.png", "generic-v1");
    }

    public LocalImageDto crest(String id) throws IOException {
        return read(crests, id, "club-generic.png", "club-generic-v1");
    }

    private LocalImageDto read(Path directory, String id, String fallback, String fallbackTag) throws IOException {
        var file = directory.resolve(id + ".png").normalize();
        boolean local = file.getParent().equals(directory) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
        if (local) try (var input = Files.newInputStream(file)) {
            local = Arrays.equals(input.readNBytes(8), PNG_SIGNATURE);
        }
        Resource resource = local ? new FileSystemResource(file) : new ClassPathResource("res/" + fallback);
        String tag = local
            ? "\"" + id + "-" + Files.getLastModifiedTime(file).toMillis() + "-" + Files.size(file) + "\""
            : "\"" + fallbackTag + "\"";
        return new LocalImageDto(resource, tag, local);
    }
}

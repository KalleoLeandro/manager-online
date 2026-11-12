package br.com.manager.repository;

import br.com.manager.exception.DefaultErrorException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Repository;

@Repository
public class StadiumImageRepository {

    private final Path directory;

    public record Photo(Resource resource, MediaType mediaType, String etag) {}

    public StadiumImageRepository(
        @Value("${app.stadiums.directory:./src/main/resources/res/stadiums}") String directory
    ) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public Photo find(String id) throws IOException {
        for (var extension : List.of("jpg", "jpeg", "png", "webp")) {
            var file = directory.resolve(id + "." + extension).normalize();
            if (!file.getParent().equals(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
            byte[] bytes;
            try (var stream = Files.newInputStream(file)) {
                bytes = stream.readNBytes(12);
            }
            MediaType type = null;
            if (
                bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255
            ) type = MediaType.IMAGE_JPEG;
            else if (
                bytes.length >= 8 &&
                Arrays.equals(Arrays.copyOf(bytes, 8), new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 })
            ) type = MediaType.IMAGE_PNG;
            else if (
                bytes.length >= 12 &&
                new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") &&
                new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")
            ) type = MediaType.parseMediaType("image/webp");
            if (type != null) return new Photo(
                new FileSystemResource(file),
                type,
                "\"" + id + "-" + Files.getLastModifiedTime(file).toMillis() + "-" + Files.size(file) + "\""
            );
        }
        throw new DefaultErrorException(HttpStatus.NOT_FOUND, "Foto do estádio ainda não cadastrada.");
    }

    public Resource credits() {
        var file = directory.resolve("credits.json");
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            ? new FileSystemResource(file)
            : new ByteArrayResource("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

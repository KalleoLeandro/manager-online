package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.repository.ManagerPhotoRepository;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ManagerPhotoService {

    private final ManagerPhotoRepository repository;

    public ManagerPhotoService(ManagerPhotoRepository repository) {
        this.repository = repository;
    }

    public byte[] validate(String data) {
        if (data == null || data.isBlank()) return null;
        if (data.length() > 350000 || !data.startsWith("data:image/png;base64,")) throw invalid();
        try {
            var bytes = Base64.getDecoder().decode(data.substring(22));
            if (bytes.length == 0 || bytes.length > 256 * 1024) throw invalid();
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    if (
                        !reader.getFormatName().equalsIgnoreCase("png") ||
                        reader.getWidth(0) < 1 ||
                        reader.getHeight(0) < 1 ||
                        reader.getWidth(0) > 1024 ||
                        reader.getHeight(0) > 1024
                    ) throw invalid();
                    var image = reader.read(0);
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", output);
                    if (output.size() > 256 * 1024) throw invalid();
                    return output.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            throw invalid();
        }
    }

    public void save(String id, byte[] png) {
        if (png != null) repository.create(id, png);
    }

    public String url(String id) {
        return repository.exists(id) ? "/api/managers/" + id + "/photo" : null;
    }

    public byte[] photo(String id) {
        return repository
            .photo(id)
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Foto não encontrada."));
    }

    private DefaultErrorException invalid() {
        return new DefaultErrorException(
            HttpStatus.BAD_REQUEST,
            "Envie uma foto PNG válida de até 256 KB e 1024 × 1024 pixels."
        );
    }
}

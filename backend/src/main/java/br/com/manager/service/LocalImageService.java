package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.LocalImageDto;
import br.com.manager.repository.LocalImageRepository;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LocalImageService {

    private final LocalImageRepository repository;

    public LocalImageService(LocalImageRepository repository) {
        this.repository = repository;
    }

    public LocalImageDto portrait(String id) throws IOException {
        validate(id);
        return repository.portrait(id);
    }

    public LocalImageDto crest(String id) throws IOException {
        validate(id);
        return repository.crest(id);
    }

    private void validate(String id) {
        if (id == null || !id.matches("generic|[1-9][0-9]{0,18}")) throw new DefaultErrorException(
            HttpStatus.NOT_FOUND,
            "Imagem não encontrada."
        );
    }
}

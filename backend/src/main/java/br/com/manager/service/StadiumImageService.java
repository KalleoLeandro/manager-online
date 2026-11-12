package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.repository.StadiumImageRepository;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StadiumImageService {

    private final StadiumImageRepository repository;

    public StadiumImageService(StadiumImageRepository repository) {
        this.repository = repository;
    }

    public StadiumImageRepository.Photo photo(String id) throws IOException {
        if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw new DefaultErrorException(
            HttpStatus.NOT_FOUND,
            "Imagem não encontrada."
        );
        return repository.find(id);
    }

    public Resource credits() {
        return repository.credits();
    }
}

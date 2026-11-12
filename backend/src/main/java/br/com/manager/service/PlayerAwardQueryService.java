package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.PlayerAwardDtos.Ceremony;
import br.com.manager.repository.PlayerAwardRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAwardQueryService {

    private final PlayerAwardRepository repository;
    private final SaveService saves;

    public PlayerAwardQueryService(PlayerAwardRepository repository, SaveService saves) {
        this.repository = repository;
        this.saves = saves;
    }

    private String member(String id, String header) {
        saves.summary(id, header);
        return saves.realtimeMember(id, header.substring(7)).id();
    }

    @Transactional(readOnly = true)
    public List<Ceremony> pending(String id, String header) {
        return repository.pending(id, member(id, header));
    }

    @Transactional(readOnly = true)
    public List<Ceremony> history(String id, String header) {
        member(id, header);
        return repository.history(id);
    }

    @Transactional(readOnly = true)
    public Ceremony detail(String id, String header, long award) {
        member(id, header);
        var ceremony = repository.find(id, award);
        if (ceremony == null) throw new DefaultErrorException(
            HttpStatus.NOT_FOUND,
            "Premiação não encontrada nesta liga."
        );
        return ceremony;
    }

    @Transactional
    public void acknowledge(String id, String header, long award) {
        String member = member(id, header);
        if (repository.find(id, award) == null) throw new DefaultErrorException(
            HttpStatus.NOT_FOUND,
            "Premiação não encontrada nesta liga."
        );
        repository.acknowledge(award, member);
    }
}

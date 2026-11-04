package br.com.manager.service;

import br.com.manager.models.dto.*;
import br.com.manager.models.responses.CatalogResponse;
import br.com.manager.repository.CatalogRepository;
import br.com.manager.repository.RosterPolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CatalogService {

    private final CatalogRepository repository;
    private final RosterPolicyRepository policies;

    public CatalogService(CatalogRepository repository, RosterPolicyRepository policies) {
        this.repository = repository;
        this.policies = policies;
    }

    public CatalogResponse catalog() {
        var metadata = repository.metadata();
        return new CatalogResponse(
            metadata.version(),
            metadata.source(),
            metadata.ratingMethod(),
            leagues(),
            teams(null),
            policies.catalog(),
            repository.internationalTeams()
        );
    }

    public String version() {
        return repository.metadata().version();
    }

    public List<CatalogLeagueDto> leagues() {
        return repository.leagues(true);
    }

    public List<CatalogTeamDto> teams(Long leagueId) {
        return repository.teams(leagueId, true);
    }

    public List<CatalogPlayerDto> players(Long teamId) {
        return repository.players(teamId);
    }

    public CatalogSnapshotDto snapshot() {
        var metadata = repository.metadata();
        return new CatalogSnapshotDto(
            metadata.version(),
            metadata.source(),
            metadata.ratingMethod(),
            repository.leagues(false),
            repository.teams(null, false),
            repository.players(null),
            List.of(),
            policies.catalog()
        );
    }
}

package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.responses.LeagueTeamDetailsResponse;
import br.com.manager.models.responses.LeagueTeamsResponse;
import br.com.manager.repository.LeagueTeamsRepository;
import br.com.manager.repository.ManagementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeagueTeamsService {

    private final SaveService saves;
    private final LeagueTeamsRepository repository;
    private final ManagementRepository management;

    public LeagueTeamsService(SaveService saves, LeagueTeamsRepository repository, ManagementRepository management) {
        this.saves = saves;
        this.repository = repository;
        this.management = management;
    }

    @Transactional
    public LeagueTeamsResponse teams(String saveId, String token) {
        saves.authorizedRoom(saveId, token);
        var member = saves.realtimeMember(saveId, token.substring(7));
        var teams = repository.teams(saveId);
        var own = teams
            .stream()
            .filter(t -> t.id().equals(member.teamId()))
            .findFirst()
            .orElseThrow(() ->
                new DefaultErrorException(HttpStatus.NOT_FOUND, "Seu clube não foi encontrado nesta liga.")
            );
        return new LeagueTeamsResponse(own.id(), own.leagueId(), teams);
    }

    @Transactional
    public LeagueTeamDetailsResponse team(String saveId, Long teamId, String token) {
        saves.authorizedRoom(saveId, token);
        var team = repository
            .find(saveId, teamId)
            .orElseThrow(() -> new DefaultErrorException(HttpStatus.NOT_FOUND, "Clube não encontrado nesta liga."));
        return new LeagueTeamDetailsResponse(team, management.players(saveId, teamId));
    }
}

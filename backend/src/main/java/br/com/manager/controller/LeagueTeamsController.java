package br.com.manager.controller;

import br.com.manager.models.responses.LeagueTeamDetailsResponse;
import br.com.manager.models.responses.LeagueTeamsResponse;
import br.com.manager.service.LeagueTeamsService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/teams")
public class LeagueTeamsController {

    private final LeagueTeamsService service;

    public LeagueTeamsController(LeagueTeamsService service) {
        this.service = service;
    }

    @GetMapping
    public LeagueTeamsResponse teams(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.teams(id, token);
    }

    @GetMapping("/{teamId}")
    public LeagueTeamDetailsResponse team(
        @PathVariable String id,
        @PathVariable @Positive Long teamId,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.team(id, teamId, token);
    }
}

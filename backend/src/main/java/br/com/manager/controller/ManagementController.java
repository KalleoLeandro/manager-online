package br.com.manager.controller;

import br.com.manager.models.dto.LeaguePlayerSearchDto;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.models.responses.TeamManagementResponse;
import br.com.manager.service.ManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management")
public class ManagementController {

    private final ManagementService service;

    public ManagementController(ManagementService service) {
        this.service = service;
    }

    @GetMapping
    public TeamManagementResponse state(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.state(id, token);
    }

    @PutMapping
    public TeamManagementResponse update(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody UpdateTeamRequest input
    ) {
        return service.update(id, token, input);
    }

    @GetMapping("/players")
    public List<LeaguePlayerSearchDto> search(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @RequestParam String query
    ) {
        return service.search(id, token, query);
    }

    @PutMapping("/introduction")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void acknowledgeIntroduction(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        service.acknowledgeIntroduction(id, token);
    }
}

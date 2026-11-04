package br.com.manager.controller;

import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.requests.ReadyRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.models.responses.LeagueRoomResponse;
import br.com.manager.service.SaveService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves")
public class SaveController {

    private final SaveService service;

    public SaveController(SaveService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeagueAccessResponse create(
        @Valid @RequestBody CreateLeagueRequest input,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return header == null ? service.create(input) : service.createForAccount(input, header);
    }

    @GetMapping("/mine")
    public List<LeagueAccessResponse> mine(@RequestHeader(value = "Authorization", required = false) String header) {
        return service.mine(header);
    }

    @GetMapping("/invites/{code}")
    public LeagueRoomResponse invite(@PathVariable String code) {
        return service.byCode(code);
    }

    @PostMapping("/invites/{code}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public LeagueAccessResponse join(
        @PathVariable String code,
        @Valid @RequestBody JoinLeagueRequest input,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return header == null ? service.join(code, input) : service.joinForAccount(code, input, header);
    }

    @GetMapping("/{id}")
    public LeagueRoomResponse room(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.authorizedRoom(id, token);
    }

    @GetMapping("/{id}/summary")
    public LeagueRoomResponse summary(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.summary(id, token);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        service.delete(id, token);
    }

    @PutMapping("/{id}/ready")
    public LeagueRoomResponse ready(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody ReadyRequest input
    ) {
        return service.ready(id, token, input.ready());
    }
}

package br.com.manager.controller;

import br.com.manager.models.dto.PlayerAwardDtos.Ceremony;
import br.com.manager.service.PlayerAwardQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/player-awards")
public class PlayerAwardController {

    private final PlayerAwardQueryService service;

    public PlayerAwardController(PlayerAwardQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ceremony> history(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.history(id, header);
    }

    @GetMapping("/pending")
    public List<Ceremony> pending(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.pending(id, header);
    }

    @GetMapping("/{award}")
    public Ceremony detail(
        @PathVariable String id,
        @PathVariable long award,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.detail(id, header, award);
    }

    @PostMapping("/{award}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seen(
        @PathVariable String id,
        @PathVariable long award,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        service.acknowledge(id, header, award);
    }
}

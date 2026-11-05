package br.com.manager.controller;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.models.dto.TournamentDrawDto;
import br.com.manager.service.SaveService;
import br.com.manager.service.TournamentDrawService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/draws")
public class TournamentDrawController {

    private final SaveService saves;
    private final TournamentDrawService service;

    public TournamentDrawController(SaveService saves, TournamentDrawService service) {
        this.saves = saves;
        this.service = service;
    }

    @GetMapping
    @Transactional
    public List<TournamentDrawDto> pending(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        saves.authorizedRoom(id, token);
        return service.pending(id, saves.realtimeMember(id, token.substring(7)).id());
    }

    @PostMapping("/{draw}/seen")
    @Transactional
    public void seen(
        @PathVariable String id,
        @PathVariable long draw,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        saves.authorizedRoom(id, token);
        service.acknowledge(id, saves.realtimeMember(id, token.substring(7)).id(), draw);
    }
}

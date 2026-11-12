package br.com.manager.controller;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.service.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/tournaments")
public class TournamentController {

    private final TournamentQueryService service;

    public TournamentController(TournamentQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Edition> list(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.list(id, header);
    }

    @GetMapping("/titles")
    public List<Title> titles(
        @PathVariable String id,
        @RequestParam long team,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.titles(id, team, header);
    }

    @GetMapping("/{edition}")
    public Summary detail(
        @PathVariable String id,
        @PathVariable long edition,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        return service.detail(id, edition, header);
    }
}

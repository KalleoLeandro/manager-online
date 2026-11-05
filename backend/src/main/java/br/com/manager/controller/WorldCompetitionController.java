package br.com.manager.controller;

import br.com.manager.models.dto.WorldCompetitionDtos.*;
import br.com.manager.service.SaveService;
import br.com.manager.service.WorldCompetitionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/competitions")
public class WorldCompetitionController {

    private final SaveService saves;
    private final WorldCompetitionService service;

    public WorldCompetitionController(SaveService saves, WorldCompetitionService service) {
        this.saves = saves;
        this.service = service;
    }

    @GetMapping
    public Overview overview(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        saves.authorizedRoom(id, token);
        return service.overview(id);
    }

    @GetMapping("/{season}")
    public Championship championship(
        @PathVariable String id,
        @PathVariable long season,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        saves.authorizedRoom(id, token);
        return service.championship(id, season);
    }
}

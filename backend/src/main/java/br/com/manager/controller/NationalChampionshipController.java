package br.com.manager.controller;

import br.com.manager.models.responses.NationalChampionshipResponse;
import br.com.manager.service.NationalChampionshipService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/national-championship")
public class NationalChampionshipController {

    private final NationalChampionshipService service;

    public NationalChampionshipController(NationalChampionshipService service) {
        this.service = service;
    }

    @GetMapping
    public NationalChampionshipResponse championship(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @RequestParam(required = false) Integer year
    ) {
        return service.championship(id, token, year);
    }
}

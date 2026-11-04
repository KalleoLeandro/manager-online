package br.com.manager.controller;

import br.com.manager.models.responses.LeagueCalendarResponse;
import br.com.manager.service.LeagueCalendarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/calendar")
public class LeagueCalendarController {

    private final LeagueCalendarService service;

    public LeagueCalendarController(LeagueCalendarService service) {
        this.service = service;
    }

    @GetMapping
    public LeagueCalendarResponse week(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.week(id, token);
    }
}

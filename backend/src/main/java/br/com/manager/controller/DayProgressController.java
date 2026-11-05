package br.com.manager.controller;

import br.com.manager.models.requests.AdvanceDaysRequest;
import br.com.manager.models.responses.DayProgressResponse;
import br.com.manager.service.DayProgressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/calendar/progress")
public class DayProgressController {

    private final DayProgressService service;

    public DayProgressController(DayProgressService service) {
        this.service = service;
    }

    @GetMapping
    public DayProgressResponse state(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.state(id, token);
    }

    @PostMapping
    public DayProgressResponse advance(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody AdvanceDaysRequest input
    ) {
        return service.advance(id, token, input);
    }
}

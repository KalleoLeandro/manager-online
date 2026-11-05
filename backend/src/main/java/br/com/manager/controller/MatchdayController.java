package br.com.manager.controller;

import br.com.manager.models.requests.MatchActionRequest;
import br.com.manager.models.responses.MatchdayResponse;
import br.com.manager.service.MatchdayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/matchday")
public class MatchdayController {

    private final MatchdayService service;

    public MatchdayController(MatchdayService service) {
        this.service = service;
    }

    @GetMapping
    public MatchdayResponse view(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header,
        @RequestParam(required = false) Long fixture
    ) {
        return service.view(id, header, fixture);
    }

    @PostMapping("/start")
    public MatchdayResponse start(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header
    ) {
        service.start(id, header);
        return service.view(id, header, null);
    }

    @PutMapping("/speed")
    public MatchdayResponse speed(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String header,
        @RequestParam(required = false) Long fixture,
        @Valid @RequestBody br.com.manager.models.requests.MatchSpeedRequest input
    ) {
        return service.speed(id, header, input.percent(), fixture);
    }

    @PostMapping("/{fixture}/actions")
    public MatchdayResponse action(
        @PathVariable String id,
        @PathVariable long fixture,
        @RequestHeader(value = "Authorization", required = false) String header,
        @Valid @RequestBody MatchActionRequest input
    ) {
        return service.action(id, fixture, header, input);
    }
}

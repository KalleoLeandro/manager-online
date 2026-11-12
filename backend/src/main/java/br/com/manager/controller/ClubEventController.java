package br.com.manager.controller;

import br.com.manager.models.dto.ClubEventDtos.*;
import br.com.manager.models.requests.ClubEventRequest;
import br.com.manager.service.ClubEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/calendar/events")
public class ClubEventController {

    private final ClubEventService service;

    public ClubEventController(ClubEventService service) {
        this.service = service;
    }

    @GetMapping
    public Options options(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.options(id, token);
    }

    @PostMapping("/quote")
    public Quote quote(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody ClubEventRequest input
    ) {
        return service.quote(id, token, input);
    }

    @PostMapping
    public Booking create(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody ClubEventRequest input
    ) {
        return service.create(id, token, input);
    }

    @PostMapping("/{event}/accept")
    public Booking accept(
        @PathVariable String id,
        @PathVariable long event,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.accept(id, token, event);
    }

    @DeleteMapping("/{event}")
    public Booking cancel(
        @PathVariable String id,
        @PathVariable long event,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.cancel(id, token, event);
    }
}

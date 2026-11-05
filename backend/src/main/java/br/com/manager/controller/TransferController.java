package br.com.manager.controller;

import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.requests.*;
import br.com.manager.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/saves/{id}/management/transfers")
public class TransferController {

    private final SaveService saves;
    private final TransferService service;

    public TransferController(SaveService saves, TransferService service) {
        this.saves = saves;
        this.service = service;
    }

    private long team(String id, String token) {
        saves.authorizedRoom(id, token);
        return saves.realtimeMember(id, token.substring(7)).teamId();
    }

    @GetMapping
    public List<Offer> list(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.list(id, team(id, token));
    }

    @GetMapping("/completed")
    public List<Offer> completed(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.completed(id, team(id, token));
    }

    @GetMapping("/market")
    public List<Contract> market(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @RequestParam @Size(min = 2, max = 80) String query
    ) {
        return service.market(id, team(id, token), query);
    }

    @GetMapping("/players/{player}")
    public Contract player(
        @PathVariable String id,
        @PathVariable @Positive long player,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.player(id, team(id, token), player);
    }

    @PostMapping
    public Offer submit(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody TransferOfferRequest input
    ) {
        return service.submit(id, team(id, token), input);
    }

    @PostMapping("/{offer}/improve")
    public Offer improve(
        @PathVariable String id,
        @PathVariable long offer,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody TransferOfferRequest input
    ) {
        return service.improve(id, team(id, token), offer, input);
    }

    @PostMapping("/{offer}/cancel")
    public Offer cancel(
        @PathVariable String id,
        @PathVariable long offer,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.cancel(id, team(id, token), offer);
    }

    @PostMapping("/{offer}/club-decision")
    public Offer decide(
        @PathVariable String id,
        @PathVariable long offer,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody TransferDecisionRequest input
    ) {
        return service.decideClub(id, team(id, token), offer, input);
    }
}

package br.com.manager.controller;

import br.com.manager.models.dto.FinanceDtos.*;
import br.com.manager.models.requests.*;
import br.com.manager.models.responses.FinanceStateResponse;
import br.com.manager.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/finance")
public class FinanceController {

    private final FinanceFacadeService service;

    public FinanceController(FinanceFacadeService service) {
        this.service = service;
    }

    @GetMapping
    public FinanceStateResponse state(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.state(id, token);
    }

    @PostMapping("/season")
    public FinanceStateResponse approve(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token,
        @Valid @RequestBody SeasonBudgetRequest input
    ) {
        return service.approve(id, token, input);
    }

    @GetMapping("/contracts")
    public List<Contract> contracts(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.contracts(id, token);
    }
}

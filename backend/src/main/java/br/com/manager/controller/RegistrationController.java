package br.com.manager.controller;

import br.com.manager.models.requests.RegistrationRequest;
import br.com.manager.models.responses.RegistrationResponse;
import br.com.manager.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return service.register(request);
    }
}

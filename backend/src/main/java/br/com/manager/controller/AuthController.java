package br.com.manager.controller;

import br.com.manager.models.requests.LoginRequest;
import br.com.manager.models.responses.SessionResponse;
import br.com.manager.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest input) {
        return auth.login(input.email(), input.password());
    }
}

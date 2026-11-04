package br.com.manager.controller;

import br.com.manager.models.dto.ManagedPlayerDto;
import br.com.manager.service.JuniorPlayersService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saves/{id}/management/juniors")
public class JuniorPlayersController {

    private final JuniorPlayersService service;

    public JuniorPlayersController(JuniorPlayersService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedPlayerDto> players(
        @PathVariable String id,
        @RequestHeader(value = "Authorization", required = false) String token
    ) {
        return service.players(id, token);
    }
}

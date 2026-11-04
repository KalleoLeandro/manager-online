package br.com.manager.controller;

import br.com.manager.models.dto.CatalogPlayerDto;
import br.com.manager.models.responses.CatalogResponse;
import br.com.manager.service.CatalogService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CatalogResponse> catalog() {
        var catalog = service.catalog();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(catalog);
    }

    @GetMapping("/teams/{id}/players")
    public ResponseEntity<List<CatalogPlayerDto>> players(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.players(id));
    }
}

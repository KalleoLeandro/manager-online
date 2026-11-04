package br.com.manager.controller;

import br.com.manager.service.LocalImageService;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players")
public class PlayerPortraitController {

    private final LocalImageService service;

    public PlayerPortraitController(LocalImageService service) {
        this.service = service;
    }

    @GetMapping(value = "/{id}/portrait", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> portrait(@PathVariable String id) throws IOException {
        var image = service.portrait(id);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .contentLength(image.resource().contentLength())
            .cacheControl(CacheControl.noCache())
            .eTag(image.etag())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Portrait-Source", image.local() ? "local" : "generic")
            .body(image.resource());
    }
}

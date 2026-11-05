package br.com.manager.controller;

import br.com.manager.service.LocalImageService;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trophies")
public class TrophyController {

    private final LocalImageService service;

    public TrophyController(LocalImageService service) {
        this.service = service;
    }

    @GetMapping(value = "/credits", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> credits() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .body(service.trophyCredits());
    }

    @GetMapping(value = "/{id}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> image(@PathVariable String id) throws IOException {
        var image = service.trophy(id);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .contentLength(image.resource().contentLength())
            .cacheControl(CacheControl.noCache())
            .eTag(image.etag())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Trophy-Source", image.local() ? "local" : "generic")
            .body(image.resource());
    }
}

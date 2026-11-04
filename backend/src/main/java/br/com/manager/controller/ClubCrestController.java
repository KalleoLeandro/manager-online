package br.com.manager.controller;

import br.com.manager.service.LocalImageService;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams")
public class ClubCrestController {

    private final LocalImageService service;

    public ClubCrestController(LocalImageService service) {
        this.service = service;
    }

    @GetMapping(value = "/{id}/crest", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> crest(@PathVariable String id) throws IOException {
        var image = service.crest(id);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .contentLength(image.resource().contentLength())
            .cacheControl(CacheControl.noCache())
            .eTag(image.etag())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Crest-Source", image.local() ? "local" : "generic")
            .body(image.resource());
    }
}

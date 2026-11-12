package br.com.manager.controller;

import br.com.manager.service.StadiumImageService;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StadiumImageController {

    private final StadiumImageService service;

    public StadiumImageController(StadiumImageService service) {
        this.service = service;
    }

    @GetMapping("/teams/{id}/stadium")
    public ResponseEntity<Resource> photo(@PathVariable String id) throws IOException {
        var photo = service.photo(id);
        return ResponseEntity.ok()
            .contentType(photo.mediaType())
            .contentLength(photo.resource().contentLength())
            .cacheControl(CacheControl.noCache())
            .eTag(photo.etag())
            .header("X-Content-Type-Options", "nosniff")
            .body(photo.resource());
    }

    @GetMapping(value = "/stadiums/credits", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> credits() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .body(service.credits());
    }
}

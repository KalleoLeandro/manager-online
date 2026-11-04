package br.com.manager.controller;

import br.com.manager.service.ManagerPhotoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/managers")
public class ManagerPhotoController {

    private final ManagerPhotoService photos;

    public ManagerPhotoController(ManagerPhotoService photos) {
        this.photos = photos;
    }

    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> photo(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .body(photos.photo(id.toString()));
    }
}

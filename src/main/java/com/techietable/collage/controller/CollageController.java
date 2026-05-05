package com.techietable.collage.controller;

import com.techietable.collage.service.BackgroundCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollageController {

    @Autowired
    private BackgroundCache backgroundCache;

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> background() throws Exception {
        byte[] img = backgroundCache.get();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(img);
    }
}

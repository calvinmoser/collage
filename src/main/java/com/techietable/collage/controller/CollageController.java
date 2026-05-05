package com.techietable.collage.controller;

import com.techietable.collage.service.BackgroundCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollageController {

    private static final Logger log = LoggerFactory.getLogger(CollageController.class);

    @Autowired
    private BackgroundCache backgroundCache;

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> background() throws Exception {
        long start = System.currentTimeMillis();
        byte[] img = backgroundCache.get();
        log.info("GET /api/background → {}KB in {}ms", img.length / 1024, System.currentTimeMillis() - start);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(img);
    }
}

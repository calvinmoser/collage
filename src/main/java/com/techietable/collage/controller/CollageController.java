package com.techietable.collage.controller;

import com.techietable.collage.service.CollageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class CollageController {

    private static final Logger log = LoggerFactory.getLogger(CollageController.class);

    @Autowired
    private CollageService collageService;

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> background(
            @RequestParam(defaultValue = "1920") int w,
            @RequestParam(defaultValue = "1080") int h) throws IOException {
        long start = System.currentTimeMillis();
        byte[] img = collageService.generateBackground(w, h);
        log.info("GET /api/background {}x{} → {}KB in {}ms", w, h, img.length / 1024, System.currentTimeMillis() - start);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(img);
    }
}

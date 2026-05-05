package com.techietable.collage.controller;

import com.techietable.collage.service.CollageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class CollageController {

    @Autowired
    private CollageService collageService;

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> background(
            @RequestParam(defaultValue = "1920") int w,
            @RequestParam(defaultValue = "1080") int h) throws IOException {
        byte[] img = collageService.generateBackground(w, h);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(img);
    }
}

package com.techietable.collage.controller;

import com.techietable.collage.service.BackgroundCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollageController {

    @Autowired
    private BackgroundCache backgroundCache;

    @GetMapping({"/tutoring", "/tutoring/"})
    public void tutoring(HttpServletRequest req, HttpServletResponse res) throws Exception {
        req.getRequestDispatcher("/tutoring/index.html").forward(req, res);
    }

    @GetMapping({"/contact", "/contact/"})
    public void contact(HttpServletRequest req, HttpServletResponse res) throws Exception {
        req.getRequestDispatcher("/contact/index.html").forward(req, res);
    }

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> background() throws Exception {
        byte[] img = backgroundCache.get();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(img);
    }
}

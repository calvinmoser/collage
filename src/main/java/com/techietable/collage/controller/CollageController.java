package com.techietable.collage.controller;

import com.techietable.collage.service.BackgroundCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CollageController {

    private static final Logger log = LoggerFactory.getLogger(CollageController.class);

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
    public ResponseEntity<byte[]> background(
            @RequestParam(defaultValue = "2024") int w,
            @RequestParam(defaultValue = "2024") int h) throws Exception {
        long t0 = System.currentTimeMillis();
        BackgroundCache.Cached cached = backgroundCache.get();
        int size = Math.min(Math.max(w, h), BackgroundCache.W);

        byte[] bytes;
        String sizeLabel;
        if (size <= BackgroundCache.MOBILE_MAX) {
            bytes = cached.mobileWebp();
            sizeLabel = "mobile-cached";
        } else {
            // Full-size pre-encoded bytes; browser scales via background-size:cover
            bytes = cached.fullWebp();
            sizeLabel = "full-cached";
        }

        long fetchMs = System.currentTimeMillis() - t0;
        log.info("[perf] /api/background: {}ms, {} bytes ({})", fetchMs, bytes.length, sizeLabel);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .header("Cache-Control", "no-store")
                .body(bytes);
    }
}

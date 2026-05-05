package com.techietable.collage;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class BackgroundController {

    @GetMapping("/api/background")
    public ResponseEntity<byte[]> getBackground(
            @RequestParam(required = false) Integer w,
            @RequestParam(required = false) Integer h) throws IOException {
        ClassPathResource resource = new ClassPathResource("static/images/fragment00.png");
        byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }
}

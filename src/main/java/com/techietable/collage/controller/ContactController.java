package com.techietable.collage.controller;

import com.techietable.collage.model.ContactRequest;
import com.techietable.collage.service.MatrixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContactController {

    @Autowired
    private MatrixService matrixService;

    @PostMapping("/api/contact")
    public ResponseEntity<Void> submit(@RequestBody ContactRequest req) {
        matrixService.sendContactMessage(req);
        return ResponseEntity.ok().build();
    }
}

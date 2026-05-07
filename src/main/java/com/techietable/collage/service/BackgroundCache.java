package com.techietable.collage.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BackgroundCache {

    private static final Logger log = LoggerFactory.getLogger(BackgroundCache.class);
    private static final int W = 2024;
    private static final int H = 2024;

    @Autowired
    private CollageService collageService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "collage-pregen"));
    private final AtomicReference<Future<byte[]>> next = new AtomicReference<>();

    @PostConstruct
    public void init() {
        next.set(submitGeneration());
        log.info("Background pre-generation started ({}x{})", W, H);
    }

    public byte[] get() throws Exception {
        Future<byte[]> current = next.getAndSet(submitGeneration());
        byte[] img = current.get();
        log.debug("Served pre-generated background, next generation queued");
        return img;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private Future<byte[]> submitGeneration() {
        return executor.submit(() -> collageService.generateBackground(W, H));
    }
}

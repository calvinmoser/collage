package com.techietable.collage.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class BackgroundCache {

    private static final Logger log = LoggerFactory.getLogger(BackgroundCache.class);

    public static final int W = 2024;
    public static final int H = 2024;
    public static final int MOBILE_MAX = 1024;
    public static final float WEBP_QUALITY = 0.75f;

    public record Cached(BufferedImage full, byte[] mobileWebp, byte[] fullWebp) {}

    @Autowired
    private CollageService collageService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "collage-pregen"));
    private final AtomicReference<Future<Cached>> next = new AtomicReference<>();

    @PostConstruct
    public void init() {
        next.set(submitGeneration());
        log.info("Background pre-generation started ({}x{})", W, H);
    }

    public Cached get() throws Exception {
        Future<Cached> current = next.getAndSet(submitGeneration());
        Cached cached = current.get();
        log.debug("Served pre-generated background, next generation queued");
        return cached;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private Future<Cached> submitGeneration() {
        return executor.submit(() -> {
            BufferedImage img = collageService.generateBackground(W, H);
            byte[] mobileWebp = encodeWebP(downscale(img, MOBILE_MAX), WEBP_QUALITY);
            byte[] fullWebp   = encodeWebP(img, WEBP_QUALITY);
            log.debug("Pre-encoded WebP: mobile {} bytes, full {} bytes", mobileWebp.length, fullWebp.length);
            return new Cached(img, mobileWebp, fullWebp);
        });
    }

    public static BufferedImage downscale(BufferedImage src, int maxDim) {
        if (src.getWidth() <= maxDim) return src;
        BufferedImage out = new BufferedImage(maxDim, maxDim, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, maxDim, maxDim, null);
        g.dispose();
        return out;
    }

    public static byte[] encodeWebP(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(param.getCompressionTypes()[0]); // "Lossy"
            param.setCompressionQuality(quality);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();
        return baos.toByteArray();
    }
}

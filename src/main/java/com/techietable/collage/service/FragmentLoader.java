package com.techietable.collage.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class FragmentLoader {

    private static final Logger log = LoggerFactory.getLogger(FragmentLoader.class);

    private List<BufferedImage> fragments;

    @PostConstruct
    public void load() {
        List<BufferedImage> loaded = new ArrayList<>();
        try {
            ClassPathResource manifest = new ClassPathResource("static/images/manifest.json");
            Map<String, List<String>> data = new ObjectMapper().readValue(
                    manifest.getInputStream(), new TypeReference<>() {});
            List<String> names = data.getOrDefault("fragments", List.of());

            for (String name : names) {
                try {
                    ClassPathResource res = new ClassPathResource("static/images/" + name);
                    BufferedImage raw = ImageIO.read(res.getInputStream());
                    if (raw != null) loaded.add(toRGB(raw));
                } catch (Exception e) {
                    log.warn("Skipping fragment {}: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load fragment manifest: {}", e.getMessage());
        }
        fragments = Collections.unmodifiableList(loaded);
        log.info("Loaded {} fragment images", fragments.size());
    }

    public List<BufferedImage> getFragments() {
        return fragments;
    }

    private static BufferedImage toRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}

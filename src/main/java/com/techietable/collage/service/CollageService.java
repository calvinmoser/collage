package com.techietable.collage.service;

import com.techietable.collage.model.Scrap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CollageService {


    // ── Configuration ────────────────────────────────────────────────────────────

    private static final int    MAX_IMAGES         = 40;
    private static final double OVERLAP_TARGET      = 0.50;
    private static final double MAX_AREA_FRAC       = 0.097;
    private static final double MIN_AREA_FRAC       = 0.018;
    private static final double BACKING_MIN_FRAC    = 0.12;
    private static final double BACKING_MAX_FRAC    = 0.30;
    private static final double MIN_ASPECT          = 0.15;
    private static final int[]  RIPPED_DIST         = {0,0,0,0,0,0,1,1,1,2}; // 60% 0, 30% 1, 10% 2
    private static final int    PLACEMENT_ATTEMPTS  = 200;
    private static final double EDGE_OVERHANG       = 0.04;
    private static final double BORING_THRESHOLD    = 0.60;
    private static final int    CROP_RETRIES        = 10;

    @Autowired
    private FragmentLoader fragmentLoader;

    // ── Public API ───────────────────────────────────────────────────────────────

    public BufferedImage generateBackground(int W, int H) throws IOException {
        List<BufferedImage> pool = selectFragments();
        List<Scrap> scraps = createScraps(pool, W, H);
        assignShadows(scraps);
        placeScraps(scraps, W, H);
        BufferedImage canvas = render(scraps, W, H);
        applyFilters(canvas);
        return canvas;
    }

    // ── Fragment selection ───────────────────────────────────────────────────────

    private List<BufferedImage> selectFragments() {
        List<BufferedImage> all = new ArrayList<>(fragmentLoader.getFragments());
        Collections.shuffle(all);
        return all.subList(0, Math.min(MAX_IMAGES, all.size()));
    }

    // ── Scrap creation ───────────────────────────────────────────────────────────

    private List<Scrap> createScraps(List<BufferedImage> pool, int W, int H) {
        int numBacking = randInt(4, 6);
        List<Scrap> scraps = new ArrayList<>();

        for (int i = 0; i < Math.min(numBacking, pool.size()); i++) {
            Scrap s = makeCrop(i, pool.get(i), W, H, BACKING_MIN_FRAC, BACKING_MAX_FRAC);
            if (s != null) scraps.add(s);
        }
        for (int i = numBacking; i < pool.size(); i++) {
            for (int pass = 0; pass < 2; pass++) {
                Scrap s = makeCrop(i, pool.get(i), W, H, MIN_AREA_FRAC, MAX_AREA_FRAC);
                if (s != null) scraps.add(s);
            }
        }

        // Largest scraps land at the bottom (lower z-order = drawn first)
        scraps.sort((a, b) -> Double.compare(b.getW() * b.getH(), a.getW() * a.getH()));
        return scraps;
    }

    private Scrap makeCrop(int fragIdx, BufferedImage frag, int W, int H,
                           double minFrac, double maxFrac) {
        double aspect  = rnd(MIN_ASPECT, 1.0);
        double area    = W * H * rnd(minFrac, maxFrac);
        double longer  = Math.sqrt(area / aspect);
        double shorter = longer * aspect;
        boolean landscape = Math.random() < 0.5;
        double w = Math.min(landscape ? longer : shorter, frag.getWidth());
        double h = Math.min(landscape ? shorter : longer, frag.getHeight());
        double rot = rnd(-28, 28);

        double bestCropX = 0, bestCropY = 0;
        double bestScore = Double.MAX_VALUE;

        for (int t = 0; t < CROP_RETRIES; t++) {
            double cx = rnd(0, Math.max(0, frag.getWidth()  - w));
            double cy = rnd(0, Math.max(0, frag.getHeight() - h));
            double score = cropBoringScore(frag, (int) cx, (int) cy, (int) w, (int) h);
            if (score < bestScore) { bestScore = score; bestCropX = cx; bestCropY = cy; }
            if (score <= BORING_THRESHOLD) break;
        }
        if (bestScore > BORING_THRESHOLD) return null;

        Set<Integer> ripped = chooseRippedEdges();
        ClipResult clip = generateClipResult(ripped);
        return new Scrap(frag, fragIdx, bestCropX, bestCropY, w, h, rot, clip.points, clip.fringes);
    }

    private double cropBoringScore(BufferedImage img, int x, int y, int w, int h) {
        int sampleSize = 32;
        BufferedImage sample = new BufferedImage(sampleSize, sampleSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sample.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, sampleSize, sampleSize, x, y, x + w, y + h, null);
        g.dispose();

        int total = sampleSize * sampleSize;
        int white = 0, black = 0;
        for (int py = 0; py < sampleSize; py++) {
            for (int px = 0; px < sampleSize; px++) {
                int rgb = sample.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int lum = (r + green + b) / 3;
                if (lum > 230) white++;
                else if (lum < 25) black++;
            }
        }
        return (double) Math.max(white, black) / total;
    }

    // ── Clip-path generation ─────────────────────────────────────────────────────

    private Set<Integer> chooseRippedEdges() {
        int n = RIPPED_DIST[randInt(0, RIPPED_DIST.length - 1)];
        List<Integer> edges = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(edges);
        return new HashSet<>(edges.subList(0, n));
    }

    private static class ClipResult {
        final double[] points;
        final List<double[]> fringes;
        ClipResult(double[] points, List<double[]> fringes) {
            this.points = points;
            this.fringes = fringes;
        }
    }

    // Clockwise edges: 0=top(L→R), 1=right(T→B), 2=bottom(R→L), 3=left(B→T)
    // Points are percentage-based [0,100] relative to scrap bounds.
    private ClipResult generateClipResult(Set<Integer> rippedEdges) {
        // For background scraps innerAngle=0, so cos=1, sin=0, scale=1 — no rotation of corners
        double[][] rawEdges = {
            {  0,   0, 100,   0,  0,  1},
            {100,   0, 100, 100, -1,  0},
            {100, 100,   0, 100,  0, -1},
            {  0, 100,   0,   0,  1,  0},
        };

        List<double[]> allPts = new ArrayList<>();
        List<RippedEdgeData> rippedData = new ArrayList<>();

        for (int e = 0; e < 4; e++) {
            double x0 = rawEdges[e][0], y0 = rawEdges[e][1];
            double x1 = rawEdges[e][2], y1 = rawEdges[e][3];
            double dx = rawEdges[e][4], dy = rawEdges[e][5];
            boolean isRipped = rippedEdges.contains(e);

            allPts.add(new double[]{x0, y0});
            List<double[]> ePts = new ArrayList<>();
            ePts.add(new double[]{x0, y0});

            if (isRipped) {
                double off1 = rnd(-3, 13), off2 = rnd(-3, 13);
                int n = 22 + randInt(0, 8);
                for (int j = 1; j < n; j++) {
                    double t = (double) j / n, u = 1 - t;
                    double bx = x0 + (x1 - x0) * t;
                    double by = y0 + (y1 - y0) * t;
                    double smooth = 3 * u * u * t * off1 + 3 * u * t * t * off2;
                    double micro  = rnd(-0.4, 0.4);
                    double[] pt = {bx + dx * (smooth + micro), by + dy * (smooth + micro)};
                    allPts.add(pt);
                    ePts.add(pt);
                }
                rippedData.add(new RippedEdgeData(ePts, dx, dy));
            } else {
                double off1 = rnd(-3.5, 3.5), off2 = rnd(-3.5, 3.5);
                int n = 12;
                for (int j = 1; j < n; j++) {
                    double t = (double) j / n, u = 1 - t;
                    double bx = x0 + (x1 - x0) * t;
                    double by = y0 + (y1 - y0) * t;
                    double off = 3 * u * u * t * off1 + 3 * u * t * t * off2;
                    allPts.add(new double[]{bx + dx * off, by + dy * off});
                }
            }
        }

        double[] clipPoints = flattenPoints(allPts);
        List<double[]> fringes = rippedData.stream().map(this::buildFringe).toList();
        return new ClipResult(clipPoints, fringes);
    }

    private record RippedEdgeData(List<double[]> ePts, double dx, double dy) {}

    private double[] buildFringe(RippedEdgeData data) {
        double fringeW = rnd(0.7, 1.4);
        double half = fringeW / 2;
        List<double[]> inner = new ArrayList<>(), outer = new ArrayList<>();
        for (double[] pt : data.ePts()) {
            inner.add(new double[]{pt[0] + data.dx() * (half + rnd(-0.3, 0.3)),
                                   pt[1] + data.dy() * (half + rnd(-0.3, 0.3))});
            outer.add(new double[]{pt[0] - data.dx() * (half + rnd(-0.3, 0.3)),
                                   pt[1] - data.dy() * (half + rnd(-0.3, 0.3))});
        }
        Collections.reverse(outer);
        List<double[]> poly = new ArrayList<>(inner);
        poly.addAll(outer);
        return flattenPoints(poly);
    }

    private static double[] flattenPoints(List<double[]> pts) {
        double[] flat = new double[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            flat[i * 2]     = pts.get(i)[0];
            flat[i * 2 + 1] = pts.get(i)[1];
        }
        return flat;
    }

    // ── Shadow assignment ────────────────────────────────────────────────────────

    private void assignShadows(List<Scrap> scraps) {
        double lightAngle = rnd(25, 65) * Math.PI / 180;
        double baseDist   = rnd(4, 8);
        double baseBlur   = rnd(5, 10);
        int n = scraps.size();
        for (int i = 0; i < n; i++) {
            double rank = n > 1 ? (double) i / (n - 1) : 0.5;
            double dist  = baseDist * (0.3 + rank * 1.0);
            double blur  = baseBlur * (0.4 + rank * 0.9);
            double alpha = 0.10 + rank * 0.45;
            scraps.get(i).setShadow(
                Math.cos(lightAngle) * dist,
                Math.sin(lightAngle) * dist,
                blur, alpha);
        }
    }

    // ── Stochastic placement ─────────────────────────────────────────────────────

    private void placeScraps(List<Scrap> scraps, int W, int H) {
        List<Scrap> placed = new ArrayList<>();
        for (Scrap s : scraps) {
            List<Scrap> forbidden = placed.stream()
                    .filter(p -> p.getFragmentIndex() == s.getFragmentIndex())
                    .toList();
            double[] pos = bestPosition(s.getW(), s.getH(), s.getRot(), W, H, placed, forbidden);
            s.place(pos[0], pos[1]);
            placed.add(s);
        }
    }

    private double[] bestPosition(double w, double h, double rot,
                                  int W, int H,
                                  List<Scrap> placed, List<Scrap> forbidden) {
        double oh = EDGE_OVERHANG;
        double bestCx = W / 2.0, bestCy = H / 2.0;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            double cx = rnd(-W * oh, W * (1 + oh));
            double cy = rnd(-H * oh, H * (1 + oh));

            if (!forbidden.isEmpty()) {
                double[] box = aabb(cx, cy, w, h, rot);
                boolean blocked = forbidden.stream().anyMatch(p -> {
                    double[] fb = aabb(p.getCx(), p.getCy(), p.getW(), p.getH(), p.getRot());
                    return overlapArea(box, fb) > 0;
                });
                if (blocked) continue;
            }

            double score = scorePlacement(cx, cy, w, h, rot, placed);
            if (score < bestScore) { bestScore = score; bestCx = cx; bestCy = cy; }
        }
        return new double[]{bestCx, bestCy};
    }

    private double scorePlacement(double cx, double cy, double w, double h, double rot,
                                  List<Scrap> placed) {
        double[] box = aabb(cx, cy, w, h, rot);
        double total = 0;
        for (Scrap p : placed) {
            total += overlapArea(box, aabb(p.getCx(), p.getCy(), p.getW(), p.getH(), p.getRot()));
        }
        double frac = total / (w * h);
        return frac <= OVERLAP_TARGET ? frac : OVERLAP_TARGET + (frac - OVERLAP_TARGET) * 4;
    }

    // Axis-aligned bounding box [l, r, t, b]
    private double[] aabb(double cx, double cy, double w, double h, double rot) {
        double rad = rot * Math.PI / 180;
        double cos = Math.abs(Math.cos(rad)), sin = Math.abs(Math.sin(rad));
        double hw = (w * cos + h * sin) / 2, hh = (w * sin + h * cos) / 2;
        return new double[]{cx - hw, cx + hw, cy - hh, cy + hh};
    }

    private double overlapArea(double[] a, double[] b) {
        double ox = Math.max(0, Math.min(a[1], b[1]) - Math.max(a[0], b[0]));
        double oy = Math.max(0, Math.min(a[3], b[3]) - Math.max(a[2], b[2]));
        return ox * oy;
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    private BufferedImage render(List<Scrap> scraps, int W, int H) {
        BufferedImage canvas = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);

        for (Scrap s : scraps) renderScrap(g, s);

        g.dispose();
        return canvas;
    }

    private void renderScrap(Graphics2D g, Scrap s) {
        double w = s.getW(), h = s.getH();

        AffineTransform saved = g.getTransform();
        g.translate(s.getCx(), s.getCy());
        g.rotate(Math.toRadians(s.getRot()));
        // Local space: origin at scrap center, scrap occupies [-w/2,-h/2]..[w/2,h/2]

        // 1. Shadow
        drawShadow(g, s, w, h);

        // 2. Fringe strips (cream-colored torn-edge paper)
        g.setColor(new Color(0xf0, 0xeb, 0xe0));
        for (double[] fringe : s.getFringePolygons()) {
            g.fill(buildPath(fringe, w, h, -w / 2, -h / 2));
        }

        // 3. Clipped fragment image — off-screen buffer + DstIn mask for anti-aliased edges
        // (g.clip() ignores VALUE_ANTIALIAS_ON; fill() does not)
        int icx = (int) s.getCropX(), icy = (int) s.getCropY();
        int iw  = Math.min((int) w, s.getFragment().getWidth()  - icx);
        int ih  = Math.min((int) h, s.getFragment().getHeight() - icy);
        if (iw > 0 && ih > 0) {
            int bw = (int) w, bh = (int) h;

            BufferedImage scrapBuf = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scrapBuf.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.drawImage(s.getFragment().getSubimage(icx, icy, iw, ih), 0, 0, null);

            BufferedImage mask = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D mg = mask.createGraphics();
            mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            mg.setColor(Color.WHITE);
            mg.fill(buildPath(s.getClipPoints(), w, h, 0, 0));
            mg.dispose();

            sg.setComposite(AlphaComposite.DstIn);
            sg.drawImage(mask, 0, 0, null);
            sg.dispose();

            // Convert to pre-multiplied so bilinear interpolation during rotation
            // doesn't blend ghost RGB values from DstIn-zeroed transparent pixels.
            BufferedImage premult = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D pc = premult.createGraphics();
            pc.drawImage(scrapBuf, 0, 0, null);
            pc.dispose();
            g.drawImage(premult, (int) (-w / 2), (int) (-h / 2), null);
        }
        g.setTransform(saved);
    }

    private void drawShadow(Graphics2D g, Scrap s, double w, double h) {
        double sigma = Math.min(s.getShadowBlur(), 12);  // cap to keep kernel manageable
        int pad = (int) (sigma * 2.5) + 2;
        int bw = (int) w + pad * 2, bh = (int) h + pad * 2;

        BufferedImage shadowBuf = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = shadowBuf.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        sg.setColor(Color.BLACK);
        sg.fill(buildPath(s.getClipPoints(), w, h, pad, pad));
        sg.dispose();

        if (sigma > 0) shadowBuf = gaussianBlur(shadowBuf, sigma);

        Composite savedComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) s.getShadowAlpha()));
        g.drawImage(shadowBuf,
            (int) (s.getShadowX() - w / 2 - pad),
            (int) (s.getShadowY() - h / 2 - pad), null);
        g.setComposite(savedComp);
    }

    // Builds a Path2D from flat percentage points, with top-left corner at (originX, originY)
    private Path2D.Double buildPath(double[] pts, double w, double h,
                                    double originX, double originY) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(originX + pts[0] / 100.0 * w, originY + pts[1] / 100.0 * h);
        for (int i = 2; i < pts.length; i += 2) {
            path.lineTo(originX + pts[i] / 100.0 * w, originY + pts[i + 1] / 100.0 * h);
        }
        path.closePath();
        return path;
    }

    // Separable Gaussian blur — two 1D passes for O(n·k) instead of O(n·k²)
    private BufferedImage gaussianBlur(BufferedImage src, double sigma) {
        int radius = (int) Math.ceil(sigma * 2);
        int size   = 2 * radius + 1;
        float[] k  = new float[size];
        float sum  = 0;
        for (int i = 0; i < size; i++) {
            float x = i - radius;
            k[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < size; i++) k[i] /= sum;

        ConvolveOp hPass = new ConvolveOp(new Kernel(size, 1, k), ConvolveOp.EDGE_ZERO_FILL, null);
        ConvolveOp vPass = new ConvolveOp(new Kernel(1, size, k), ConvolveOp.EDGE_ZERO_FILL, null);
        return vPass.filter(hPass.filter(src, null), null);
    }

    // ── Post-processing ──────────────────────────────────────────────────────────

    private void applyFilters(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) continue;

                int r  = (argb >> 16) & 0xFF;
                int gv = (argb >>  8) & 0xFF;
                int b  =  argb        & 0xFF;

                int gray = (int) (0.299 * r + 0.587 * gv + 0.114 * b);
                r  = clamp((int) ((gray + 0.45 * (r  - gray)) * 0.70));
                gv = clamp((int) ((gray + 0.45 * (gv - gray)) * 0.70));
                b  = clamp((int) ((gray + 0.45 * (b  - gray)) * 0.70));

                img.setRGB(x, y, (a << 24) | (r << 16) | (gv << 8) | b);
            }
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    // ── Random helpers ───────────────────────────────────────────────────────────

    private static double rnd(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static int randInt(int min, int max) {
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }
}

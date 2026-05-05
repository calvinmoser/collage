package com.techietable.collage.model;

import java.awt.image.BufferedImage;
import java.util.List;

public class Scrap {

    private final BufferedImage fragment;
    private final int fragmentIndex;     // index in shuffled pool — same index = same source image
    private final double cropX, cropY;   // top-left of crop within fragment (px)
    private final double w, h;           // scrap dimensions (px)
    private final double rot;            // rotation in degrees
    private final double[] clipPoints;   // flat [x0%,y0%, x1%,y1%,...] polygon, 0–100 relative to scrap bounds
    private final List<double[]> fringePolygons; // same format; one strip per ripped edge

    private double cx, cy;              // center position on canvas — set during placement
    private double shadowX, shadowY, shadowBlur, shadowAlpha;

    public Scrap(BufferedImage fragment, int fragmentIndex,
                 double cropX, double cropY, double w, double h, double rot,
                 double[] clipPoints, List<double[]> fringePolygons) {
        this.fragment = fragment;
        this.fragmentIndex = fragmentIndex;
        this.cropX = cropX;
        this.cropY = cropY;
        this.w = w;
        this.h = h;
        this.rot = rot;
        this.clipPoints = clipPoints;
        this.fringePolygons = fringePolygons;
    }

    public void place(double cx, double cy) {
        this.cx = cx;
        this.cy = cy;
    }

    public void setShadow(double x, double y, double blur, double alpha) {
        this.shadowX = x;
        this.shadowY = y;
        this.shadowBlur = blur;
        this.shadowAlpha = alpha;
    }

    public BufferedImage getFragment()       { return fragment; }
    public int getFragmentIndex()            { return fragmentIndex; }
    public double getCropX()                 { return cropX; }
    public double getCropY()                 { return cropY; }
    public double getW()                     { return w; }
    public double getH()                     { return h; }
    public double getRot()                   { return rot; }
    public double[] getClipPoints()          { return clipPoints; }
    public List<double[]> getFringePolygons(){ return fringePolygons; }
    public double getCx()                    { return cx; }
    public double getCy()                    { return cy; }
    public double getShadowX()               { return shadowX; }
    public double getShadowY()               { return shadowY; }
    public double getShadowBlur()            { return shadowBlur; }
    public double getShadowAlpha()           { return shadowAlpha; }
}

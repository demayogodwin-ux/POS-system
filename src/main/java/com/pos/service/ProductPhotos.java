package com.pos.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Turns a chosen picture into a small JPEG for the database, so phones load it quickly. */
public final class ProductPhotos {
    public static final String CONTENT_TYPE = "image/jpeg";
    private static final int MAX_SIZE = 600;
    private static final float QUALITY = 0.85F;

    private ProductPhotos() {
    }

    public static byte[] prepare(File file) throws IOException {
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IOException("This file is not a picture the POS can read. Use a JPG or PNG photo.");
        }

        return toJpeg(source);
    }

    /** Shrinks to at most 600 px on the longest side (never enlarges) on a white background, as JPEG. */
    public static byte[] toJpeg(BufferedImage source) throws IOException {
        double scale = Math.min(1.0, (double)MAX_SIZE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int)Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int)Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // JPEG has no transparency: see-through parts of a PNG become white.
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(target, null, null), param);
        } finally {
            writer.dispose();
        }

        return bytes.toByteArray();
    }
}

package ma.rhflow.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Réduit une image avant stockage. Les PDF et Word ne passent pas ici.
 * Un format illisible (WebP sans décodeur) est laissé tel quel.
 */
public final class ImageShrink {

    public record Result(byte[] bytes, String contentType, String extension) {
    }

    private ImageShrink() {
    }

    public static Result shrink(byte[] input, String contentType, int maxEdge) {
        if (input == null || input.length == 0 || contentType == null || !contentType.startsWith("image/")) {
            return null;
        }
        if (maxEdge < 64) {
            maxEdge = 64;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(input));
            if (src == null) {
                return null;
            }
            boolean alpha = src.getColorModel().hasAlpha();
            int width = src.getWidth();
            int height = src.getHeight();
            double scale = Math.min(1d, maxEdge / (double) Math.max(width, height));
            int targetW = Math.max(1, (int) Math.round(width * scale));
            int targetH = Math.max(1, (int) Math.round(height * scale));
            BufferedImage out = new BufferedImage(targetW, targetH, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, targetW, targetH);
            }
            g.drawImage(src, 0, 0, targetW, targetH, null);
            g.dispose();
            byte[] encoded = alpha ? png(out) : jpeg(out, 0.82f);
            if (encoded == null || encoded.length == 0 || encoded.length >= input.length) {
                return null;
            }
            return alpha
                    ? new Result(encoded, "image/png", ".png")
                    : new Result(encoded, "image/jpeg", ".jpg");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            return null;
        }
        return bytes.toByteArray();
    }
}

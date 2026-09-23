package com.mikedev.mutxamelcf.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class ImageUtils {

    private ImageUtils() {
    }

    /**
     * Convierte una imagen original en una miniatura JPEG
     * y la devuelve en Base64.
     *
     * Tamaño máximo: 300x300 px.
     * Mantiene la proporción original.
     */
    public static String convertirAMiniaturaBase64(byte[] imagen) {

        if (imagen == null || imagen.length == 0) {
            return null;
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagen));

            if (original == null) {
                return null;
            }

            int anchoOriginal = original.getWidth();
            int altoOriginal = original.getHeight();

            int maxAncho = 300;
            int maxAlto = 300;

            double escala = Math.min(
                    (double) maxAncho / anchoOriginal,
                    (double) maxAlto / altoOriginal);

            // No ampliamos imágenes que ya sean pequeñas.
            escala = Math.min(1.0, escala);

            int nuevoAncho = Math.max(1, (int) Math.round(anchoOriginal * escala));

            int nuevoAlto = Math.max(1, (int) Math.round(altoOriginal * escala));

            BufferedImage miniatura = new BufferedImage(
                    nuevoAncho,
                    nuevoAlto,
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = miniatura.createGraphics();

            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            graphics.drawImage(
                    original.getScaledInstance(
                            nuevoAncho,
                            nuevoAlto,
                            Image.SCALE_SMOOTH),
                    0,
                    0,
                    null);

            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg")
                    .next();

            ImageWriteParam params = writer.getDefaultWriteParam();

            params.setCompressionMode(
                    ImageWriteParam.MODE_EXPLICIT);

            params.setCompressionQuality(0.75f);

            try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {

                writer.setOutput(imageOutput);

                writer.write(
                        null,
                        new IIOImage(
                                miniatura,
                                null,
                                null),
                        params);

            } finally {
                writer.dispose();
            }

            return Base64.getEncoder()
                    .encodeToString(output.toByteArray());

        } catch (Exception e) {
            return null;
        }
    }
}
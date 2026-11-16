package com.mikedev.mutxamelcf.serviceimpl;

import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Valida y reduce el escudo que sube el panel: una imagen de verdad (PNG o JPG), de hasta 2 MB, a 256 px en PNG. */
public final class EscudoImagen {

    public static final int MAXIMO_BYTES = 2 * 1024 * 1024;
    public static final int LADO_MAXIMO = 256;

    // Contra imágenes que ocupan poco comprimidas y mucho en memoria.
    private static final long MAXIMO_PIXELES = 25_000_000L;

    private EscudoImagen() {
    }

    /**
     * @return el PNG de a lo sumo 256 px por el lado largo (no se amplía una imagen más pequeña) y con la
     *         transparencia conservada
     * @throws IllegalArgumentException con un mensaje claro si no es un PNG o JPG válido o pesa más de 2 MB
     */
    public static byte[] procesar(byte[] archivo) {

        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException("No se ha recibido ningún archivo");
        }

        if (archivo.length > MAXIMO_BYTES) {
            throw new IllegalArgumentException("El escudo pesa más de 2 MB");
        }

        BufferedImage original = leer(archivo);

        int ancho = original.getWidth();
        int alto = original.getHeight();
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(ancho, alto));
        int nuevoAncho = Math.max(1, (int) Math.round(ancho * escala));
        int nuevoAlto = Math.max(1, (int) Math.round(alto * escala));

        BufferedImage destino = original;

        if (nuevoAncho != ancho || nuevoAlto != alto) {
            destino = reducir(original, nuevoAncho, nuevoAlto);
        } else if (original.getType() != BufferedImage.TYPE_INT_ARGB) {
            destino = reducir(original, ancho, alto);
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

            if (!ImageIO.write(destino, "png", salida)) {
                throw new IllegalArgumentException("No se ha podido convertir la imagen a PNG");
            }

            return salida.toByteArray();

        } catch (IOException e) {
            throw new IllegalArgumentException("No se ha podido convertir la imagen a PNG");
        }
    }

    private static BufferedImage leer(byte[] archivo) {

        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(archivo))) {

            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);

            if (lectores == null || !lectores.hasNext()) {
                throw new IllegalArgumentException("El archivo no es una imagen válida (usa PNG o JPG)");
            }

            ImageReader lector = lectores.next();

            try {
                String formato = lector.getFormatName().toLowerCase();

                if (!formato.equals("png") && !formato.equals("jpeg") && !formato.equals("jpg")) {
                    throw new IllegalArgumentException("Solo se admiten imágenes PNG o JPG");
                }

                lector.setInput(entrada, true, true);

                if ((long) lector.getWidth(0) * lector.getHeight(0) > MAXIMO_PIXELES) {
                    throw new IllegalArgumentException("La imagen es demasiado grande (máximo 25 megapíxeles)");
                }

                BufferedImage imagen = lector.read(0);

                if (imagen == null) {
                    throw new IllegalArgumentException("El archivo no es una imagen válida (usa PNG o JPG)");
                }

                return imagen;

            } finally {
                lector.dispose();
            }

        } catch (IOException | RuntimeException e) {

            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }

            throw new IllegalArgumentException("El archivo no es una imagen válida (usa PNG o JPG)");
        }
    }

    // Por pasos (a lo sumo a la mitad cada vez) y siempre en ARGB: así se conserva la transparencia y no se pierde
    // calidad al reducir mucho.
    private static BufferedImage reducir(BufferedImage origen, int ancho, int alto) {

        BufferedImage actual = origen;
        int anchoActual = origen.getWidth();
        int altoActual = origen.getHeight();

        boolean primera = true;

        while (primera || anchoActual != ancho || altoActual != alto) {

            primera = false;

            anchoActual = anchoActual > ancho ? Math.max(ancho, anchoActual / 2) : ancho;
            altoActual = altoActual > alto ? Math.max(alto, altoActual / 2) : alto;

            BufferedImage paso = new BufferedImage(anchoActual, altoActual, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = paso.createGraphics();

            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(actual, 0, 0, anchoActual, altoActual, null);
            } finally {
                g.dispose();
            }

            actual = paso;
        }

        return actual;
    }
}

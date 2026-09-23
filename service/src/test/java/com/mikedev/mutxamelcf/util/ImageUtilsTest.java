package com.mikedev.mutxamelcf.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUtilsTest {

    private static byte[] imagenPng(int ancho, int alto) throws Exception {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    @Test
    void devuelveNullSiLaImagenEsNula() {
        assertThat(ImageUtils.convertirAMiniaturaBase64(null)).isNull();
    }

    @Test
    void devuelveNullSiLaImagenEstaVacia() {
        assertThat(ImageUtils.convertirAMiniaturaBase64(new byte[0])).isNull();
    }

    @Test
    void devuelveNullSiLosBytesNoSonUnaImagenValida() {
        assertThat(ImageUtils.convertirAMiniaturaBase64(new byte[] { 1, 2, 3, 4, 5 })).isNull();
    }

    @Test
    void generaUnaMiniaturaValidaParaUnaImagenGrande() throws Exception {
        byte[] original = imagenPng(600, 400);

        String base64 = ImageUtils.convertirAMiniaturaBase64(original);

        assertThat(base64).isNotBlank();

        byte[] miniaturaBytes = Base64.getDecoder().decode(base64);
        BufferedImage miniatura = ImageIO.read(new java.io.ByteArrayInputStream(miniaturaBytes));

        // 600x400 escalado a maximo 300x300 manteniendo proporcion -> 300x200
        assertThat(miniatura.getWidth()).isEqualTo(300);
        assertThat(miniatura.getHeight()).isEqualTo(200);
    }

    @Test
    void noAmpliaImagenesPequenas() throws Exception {
        byte[] original = imagenPng(50, 40);

        String base64 = ImageUtils.convertirAMiniaturaBase64(original);

        byte[] miniaturaBytes = Base64.getDecoder().decode(base64);
        BufferedImage miniatura = ImageIO.read(new java.io.ByteArrayInputStream(miniaturaBytes));

        assertThat(miniatura.getWidth()).isEqualTo(50);
        assertThat(miniatura.getHeight()).isEqualTo(40);
    }
}

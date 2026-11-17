package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Estado;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Fila;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Imagen;

/**
 * Renderiza imágenes con datos de ejemplo. Con {@code -Dimagenes.salida=<carpeta>} deja los PNG ahí para compararlos
 * con las de referencia (docs/referencias-redes/generadas).
 */
class ImagenesRedesRendererTest {

    private final ImagenesRedesRenderer renderer = new ImagenesRedesRenderer();

    @Test
    void generaUnPngDe1080x1350() throws Exception {

        byte[] png = renderer.renderizar(new Imagen("PREVIA", "F11", 1, 1, previaF11().subList(0, 6)));

        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(imagen.getWidth()).isEqualTo(1080);
        assertThat(imagen.getHeight()).isEqualTo(1350);
    }

    @Test
    void sinFilasTambienDibuja() throws Exception {

        byte[] png = renderer.renderizar(new Imagen("RESULTADOS", "F5", 1, 1, List.of()));

        assertThat(ImageIO.read(new ByteArrayInputStream(png)).getWidth()).isEqualTo(1080);
    }

    @Test
    void escudoIlegibleSeSustituyePorElGenerico() throws Exception {

        Fila fila = new Fila("CADETE A", "VS PEGO C.F.", "10:00 H", "14/11", "CAMP NOU LOS OLMOS", null,
                Estado.SIN_RESULTADO, new byte[] { 1, 2, 3 });

        byte[] png = renderer.renderizar(new Imagen("PREVIA", "F11", 1, 1, List.of(fila)));

        assertThat(ImageIO.read(new ByteArrayInputStream(png)).getHeight()).isEqualTo(1350);
    }

    /** Solo escribe ficheros si se pasa -Dimagenes.salida. */
    @Test
    void generaLosEjemplosParaCompararConLasOriginales() throws Exception {

        String salida = System.getProperty("imagenes.salida");

        if (salida == null || salida.isBlank()) {
            return;
        }

        Path carpeta = Path.of(salida);
        Files.createDirectories(carpeta);

        List<Fila> f11 = previaF11();
        List<Fila> f8 = previaF8();

        escribir(carpeta, "previa_f11.png", new Imagen("PREVIA", "F11", 1, 1, f11.subList(0, 6)));
        escribir(carpeta, "previa_f8.png", new Imagen("PREVIA", "F8", 1, 1, f8));

        List<List<Fila>> paginas = SeleccionImagenesRedes.paginar(f11);

        for (int i = 0; i < paginas.size(); i++) {
            escribir(carpeta, "previa_f11_" + (i + 1) + "de" + paginas.size() + ".png",
                    new Imagen("PREVIA", "F11", i + 1, paginas.size(), paginas.get(i)));
        }

        escribir(carpeta, "resultados_f11.png", new Imagen("RESULTADOS", "F11", 1, 1, resultados(f11.subList(0, 6))));
        escribir(carpeta, "resultados_f8.png", new Imagen("RESULTADOS", "F8", 1, 1, resultados(f8)));
    }

    private void escribir(Path carpeta, String nombre, Imagen imagen) throws Exception {
        Files.write(carpeta.resolve(nombre), renderer.renderizar(imagen));
    }

    // ---- datos de ejemplo (los de las imágenes del club) -------------------------------------

    private static Fila fila(String equipo, String rival, String hora, String fecha, String campo, int color) {
        return new Fila(equipo, SeleccionImagenesRedes.lineaRival(rival), SeleccionImagenesRedes.hora(hora), fecha, campo,
                null, Estado.SIN_RESULTADO, color == 0 ? null : escudoFalso(new Color(color)));
    }

    private static List<Fila> previaF11() {

        List<Fila> filas = new ArrayList<>();

        filas.add(fila("JUVENIL A", "CD Betis Florida B", "19:15", "15/11", "CAMPO MUNICIPAL LA FLORIDA", 0xE0B000));
        filas.add(fila("JUVENIL B", "CF Benidorm B", "19:30", "14/11", "CAMP NOU LOS OLMOS", 0x1F3A7A));
        filas.add(fila("CADETE A", "CD Español San Vicente B", "09:00", "14/11", "CAMP NOU LOS OLMOS", 0));
        filas.add(fila("CADETE B", "Villajoyosa CF B", "10:15", "14/11", "HOGAR PROVINCIAL", 0x2D6CC0));
        filas.add(fila("INFANTIL A", "Barrio Obrero A", "15:30", "14/11", "CAMP NOU LOS OLMOS", 0x4C8FD0));
        filas.add(fila("INFANTIL B", "Villajoyosa CF B", "10:45", "14/11", "CAMP NOU LOS OLMOS", 0x2D6CC0));
        filas.add(fila("INFANTIL C", "CD El Campello C", "12:00", "15/11", "POLIDEPORTIVO PLÁ GARBINET", 0xE8C300));
        filas.add(fila("CADETE C", "Elda Unión D", "16:00", "15/11", "CAMPO SISMAT (ELDA)", 0xC0392B));
        filas.add(fila("JUVENIL C", "SCD Carolinas A", "11:00", "15/11", "CAMP NOU LOS OLMOS", 0));
        filas.add(fila("VETERANOS", "Alicante CF", "17:00", "15/11", "CAMP NOU LOS OLMOS", 0x777777));
        filas.add(fila("FILIAL", "Racing Playa de San Juan B", "18:00", "15/11", "CAMP NOU LOS OLMOS", 0x6A2C91));
        filas.add(fila("AFICIONADOS", "Polideportivo Mpal. El Vincle F-11 El Campello", "12:30", "14/11",
                "POLIDEPORTIVO MUNICIPAL EL VINCLE", 0x2E7D32));
        filas.add(fila("JUVENIL D", "CD La Voz B", "13:00", "14/11", "CAMP NOU LOS OLMOS", 0));

        return filas;
    }

    private static List<Fila> previaF8() {

        List<Fila> filas = new ArrayList<>();

        filas.add(fila("ALEVÍN A", "Elda Unión E", "09:45", "15/11", "CAMPO SISMAT (ELDA)", 0xC0392B));
        filas.add(fila("ALEVÍN C", "CD La Voz B", "09:00", "15/11", "CAMPO LA FLORIDA", 0x4CAF8C));
        filas.add(fila("ALEVÍN B", "CD El Campello D", "13:30", "14/11", "CAMP NOU LOS OLMOS", 0xE8C300));
        filas.add(fila("BENJAMÍN A", "Elda Unión B", "19:30", "13/11", "CAMPO SISMAT (ELDA)", 0xC0392B));
        filas.add(fila("BENJAMÍN C", "SCD Carolinas A", "10:30", "15/11", "POLIDEPORTIVO PLÁ GARBINET", 0));
        filas.add(fila("BENJAMÍN B", "Barrio Obrero C", "12:30", "14/11", "CAMP NOU LOS OLMOS", 0x4C8FD0));
        filas.add(fila("PREBENJAMÍN A", "Racing Playa de San Juan B", "10:00", "14/11", "CAMPO MUNICIPAL VIA PARQUE",
                0x6A2C91));
        filas.add(fila("PREBENJAMÍN B", "SCD Carolinas B", "12:30", "14/11", "CAMP NOU LOS OLMOS", 0));

        return filas;
    }

    private static List<Fila> resultados(List<Fila> previa) {

        List<Fila> filas = new ArrayList<>();
        String[] marcadores = { "3 - 1", "0 - 0", "1 - 2", null, "4 - 4", "2 - 0", "1 - 0", "0 - 3" };
        Estado[] estados = { Estado.VICTORIA, Estado.EMPATE, Estado.DERROTA, Estado.SIN_RESULTADO, Estado.EMPATE,
                Estado.VICTORIA, Estado.APLAZADO, Estado.DERROTA };

        for (int i = 0; i < previa.size(); i++) {
            Fila f = previa.get(i);
            filas.add(new Fila(f.equipo(), f.rival(), f.hora(), f.fecha(), f.campo(), marcadores[i % 8],
                    estados[i % 8], f.escudo()));
        }

        return filas;
    }

    private static byte[] escudoFalso(Color color) {

        try {
            BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillOval(20, 20, 216, 216);
            g.setColor(Color.WHITE);
            g.fillOval(90, 90, 76, 76);
            g.dispose();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(img, "png", salida);

            return salida.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.mikedev.mutxamelcf.serviceimpl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Estado;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Fila;
import com.mikedev.mutxamelcf.serviceimpl.ImagenesRedesModelos.Imagen;

/**
 * Dibuja en servidor (Java2D, sin pantalla) las imágenes para redes sociales: 1080x1350 PNG, con el estilo de las
 * que publica el club. El diseño está separado en bloques ({@code pintarFondo}, {@code pintarCabecera},
 * {@code pintarFila}...) y sus medidas son constantes al principio, para poder ajustarlo fácilmente.
 *
 * <p>
 * Las fuentes (Barlow Condensed, licencia OFL) van en {@code resources/redes/fonts}: el servidor no tiene ninguna
 * instalada.
 */
@Component
public class ImagenesRedesRenderer {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public static final int ANCHO = 1080;
    public static final int ALTO = 1350;

    // ---- paleta -------------------------------------------------------------------------------
    private static final Color FONDO = new Color(0xF6F6F6);
    private static final Color BEIGE = new Color(0xFBF1CF);
    private static final Color AZUL = new Color(0x1A1A9E);
    private static final Color AZUL_OSCURO = new Color(0x12127A);
    private static final Color TEXTO = new Color(0x1F3A2B);
    private static final Color ROJO = new Color(0xC62828);
    private static final Color VERDE = new Color(0x2E7D32);
    private static final Color AMBAR = new Color(0xE08A00);
    private static final Color GRIS = new Color(0x6E7480);

    // ---- medidas ------------------------------------------------------------------------------
    private static final int TITULO_X = 120;
    private static final int TITULO_ANCHO_MAX = 720;
    private static final int FILAS_Y = 240;
    private static final int FILAS_ALTO_MAX = 896;
    private static final int FILA_PASO_MAX = 140;
    private static final int PARALELOGRAMO_X = 150;
    private static final int PARALELOGRAMO_ANCHO = 200;
    private static final int CENTRO_X = 365;
    private static final int CENTRO_ANCHO = 270;
    private static final int DERECHA_X = 675;
    private static final int DERECHA_ANCHO = 360;
    private static final int PIE_ALTO = 215;

    private final Font ultraNegrita;
    private final Font negrita;
    private final Font seminegrita;
    private final BufferedImage estadio;
    private final BufferedImage escudoClub;

    public ImagenesRedesRenderer() {
        this.ultraNegrita = fuente("BarlowCondensed-ExtraBoldItalic.ttf");
        this.negrita = fuente("BarlowCondensed-BoldItalic.ttf");
        this.seminegrita = fuente("BarlowCondensed-SemiBoldItalic.ttf");
        this.estadio = imagen("/redes/estadio.jpg");
        this.escudoClub = recortarTransparencia(imagen("/redes/escudo.png"));
    }

    /** El PNG de la imagen completa. */
    public byte[] renderizar(Imagen imagen) {

        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();

        try {
            configurar(g);
            pintarFondo(g);
            pintarCabecera(g, imagen);

            List<Fila> filas = imagen.filas();
            int paso = filas.isEmpty() ? FILA_PASO_MAX : Math.min(FILA_PASO_MAX, FILAS_ALTO_MAX / filas.size());

            for (int i = 0; i < filas.size(); i++) {
                pintarFila(g, filas.get(i), FILAS_Y + i * paso, paso, ImagenesRedesModelos.RESULTADOS.equals(imagen.tipo()));
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido codificar la imagen", e);
        }
    }

    // ---- bloques ------------------------------------------------------------------------------

    /** Blanco, foto del estadio difuminada hacia el blanco abajo y la curva beige de la izquierda. */
    void pintarFondo(Graphics2D g) {

        g.setColor(FONDO);
        g.fillRect(0, 0, ANCHO, ALTO);

        // Foto del estadio: se recorta por arriba para que llene el ancho.
        int y0 = ALTO - PIE_ALTO;
        double escala = (double) ANCHO / estadio.getWidth();
        int origenY = (int) Math.max(0, estadio.getHeight() - PIE_ALTO / escala);
        int altoOrigen = estadio.getHeight() - origenY;

        g.drawImage(estadio, 0, y0, ANCHO, ALTO, 0, origenY, estadio.getWidth(), origenY + altoOrigen, null);

        // Difuminado hacia el blanco por arriba.
        g.setPaint(new GradientPaint(0, y0 - 2, FONDO, 0, y0 + 110, new Color(FONDO.getRed(), FONDO.getGreen(),
                FONDO.getBlue(), 0)));
        g.fillRect(0, y0 - 2, ANCHO, 112);
        g.setPaint(null);

        // Curva beige de la izquierda: ancha arriba, casi nula en medio y ancha abajo.
        Path2D curva = new Path2D.Double();
        curva.moveTo(0, 0);
        curva.lineTo(125, 0);
        curva.curveTo(10, 330, -20, 800, 170, ALTO);
        curva.lineTo(0, ALTO);
        curva.closePath();
        g.setColor(BEIGE);
        g.fill(curva);
    }

    /** Título enorme en azul, con el escudo del Mutxamel C.F. arriba a la derecha. */
    void pintarCabecera(Graphics2D g, Imagen imagen) {

        boolean previa = !ImagenesRedesModelos.RESULTADOS.equals(imagen.tipo());
        String palabra = previa ? "PARTIDOS" : "RESULTADOS";
        String deporte = nombreDeporte(imagen.modalidad());

        // Escudo del club
        int alto = 205;
        int ancho = escudoClub.getWidth() * alto / escudoClub.getHeight();
        g.drawImage(escudoClub, ANCHO - 40 - ancho, 22, ancho, alto, null);

        g.setColor(AZUL);

        // Todo en una línea si cabe con un tamaño grande; si no, la palabra grande y el deporte debajo.
        String completo = palabra + " " + deporte;
        float tamano = ajustar(g, ultraNegrita, completo, 124f, TITULO_ANCHO_MAX);

        int paginaX = TITULO_X;
        int paginaY = 214;

        if (tamano >= 104f) {
            g.setFont(ultraNegrita.deriveFont(tamano));
            g.drawString(completo, TITULO_X, 150);
        } else {
            float grande = ajustar(g, ultraNegrita, palabra, 150f, TITULO_ANCHO_MAX);
            g.setFont(ultraNegrita.deriveFont(grande));
            g.drawString(palabra, TITULO_X, 140);

            float pequeno = ajustar(g, ultraNegrita, deporte, 82f, TITULO_ANCHO_MAX);
            g.setColor(AZUL_OSCURO);
            g.setFont(ultraNegrita.deriveFont(pequeno));
            int ancho2 = g.getFontMetrics().stringWidth(deporte);
            int anchoPalabra = g.getFontMetrics(ultraNegrita.deriveFont(grande)).stringWidth(palabra);
            g.drawString(deporte, TITULO_X + (anchoPalabra - ancho2) / 2, 218);

            // El marcador de página va a la derecha del deporte.
            paginaX = TITULO_X + (anchoPalabra + ancho2) / 2 + 40;
        }

        if (imagen.totalPaginas() > 1) {
            g.setColor(AZUL_OSCURO);
            g.setFont(negrita.deriveFont(48f));
            g.drawString(imagen.pagina() + "/" + imagen.totalPaginas(), paginaX, paginaY);
        }
    }

    /** Una fila: escudo del rival, equipo y rival, y a la derecha la hora, fecha y campo o el marcador. */
    void pintarFila(Graphics2D g, Fila fila, int y, int paso, boolean resultados) {

        int centroY = y + paso / 2;
        int alto = paso - 20;

        pintarEscudoRival(g, fila.escudo(), PARALELOGRAMO_X, centroY - alto / 2, PARALELOGRAMO_ANCHO, alto);
        pintarEquipoYRival(g, fila, centroY);

        if (resultados) {
            pintarMarcador(g, fila, centroY);
        } else {
            pintarHorario(g, fila, centroY);
        }
    }

    /** Equipo en negrita cursiva y, debajo, "VS RIVAL" (en las líneas que hagan falta). */
    private void pintarEquipoYRival(Graphics2D g, Fila fila, int centroY) {

        float tamanoEquipo = ajustar(g, negrita, fila.equipo(), 36f, CENTRO_ANCHO);
        Font fuenteRival = seminegrita.deriveFont(31f);
        List<String> lineas = partir(g, fuenteRival, fila.rival(), CENTRO_ANCHO);

        if (lineas.size() > 2) {
            fuenteRival = seminegrita.deriveFont(26f);
            lineas = partir(g, fuenteRival, fila.rival(), CENTRO_ANCHO);
        }

        int altoEquipo = 38;
        int altoLinea = 34;
        int total = altoEquipo + lineas.size() * altoLinea;
        int base = centroY - total / 2 + 30;
        int centroX = CENTRO_X + CENTRO_ANCHO / 2;

        g.setColor(TEXTO);
        g.setFont(negrita.deriveFont(tamanoEquipo));
        centrar(g, fila.equipo(), centroX, base);

        g.setFont(fuenteRival);

        for (int i = 0; i < lineas.size(); i++) {
            centrar(g, lineas.get(i), centroX, base + altoEquipo + i * altoLinea - 2);
        }
    }

    /** PREVIA: reloj con la hora, calendario con el día y la chincheta con el campo. */
    private void pintarHorario(Graphics2D g, Fila fila, int centroY) {

        int arriba = centroY - 20;
        int abajo = centroY + 26;

        pintarReloj(g, DERECHA_X + 18, arriba, 18);
        g.setColor(TEXTO);
        g.setFont(negrita.deriveFont(36f));
        g.drawString(fila.hora(), DERECHA_X + 48, arriba + 12);

        pintarCalendario(g, DERECHA_X + 205, arriba, 34);
        g.setFont(negrita.deriveFont(36f));
        g.drawString(fila.fecha(), DERECHA_X + 232, arriba + 12);

        pintarChincheta(g, DERECHA_X + 18, abajo, 19);

        int anchoCampo = DERECHA_ANCHO - 48;
        String campo = fila.campo() == null || fila.campo().isBlank() ? "CAMPO POR CONFIRMAR" : fila.campo();
        float tamano = ajustar(g, seminegrita, campo, 33f, anchoCampo);
        g.setColor(TEXTO);
        g.setFont(seminegrita.deriveFont(tamano));
        g.drawString(campo, DERECHA_X + 48, abajo + 11);
    }

    /** RESULTADOS: marcador grande (local primero) y, debajo, la etiqueta pequeña. */
    private void pintarMarcador(Graphics2D g, Fila fila, int centroY) {

        int centroX = DERECHA_X + DERECHA_ANCHO / 2;
        Estado estado = fila.estado();

        if (fila.marcador() != null && estado != Estado.APLAZADO) {
            g.setColor(AZUL);
            g.setFont(ultraNegrita.deriveFont(66f));
            centrar(g, fila.marcador(), centroX, centroY + 14);
            pintarEtiqueta(g, estado, centroX, centroY + 48);
        } else {
            pintarEtiqueta(g, estado, centroX, centroY + 10, 42f);
        }
    }

    private void pintarEtiqueta(Graphics2D g, Estado estado, int centroX, int baseY) {
        pintarEtiqueta(g, estado, centroX, baseY, 27f);
    }

    private void pintarEtiqueta(Graphics2D g, Estado estado, int centroX, int baseY, float tamano) {

        String texto = switch (estado) {
            case VICTORIA -> "VICTORIA";
            case EMPATE -> "EMPATE";
            case DERROTA -> "DERROTA";
            case SIN_RESULTADO -> "SIN RESULTADO";
            case APLAZADO -> "APLAZADO";
        };

        g.setColor(switch (estado) {
            case VICTORIA -> VERDE;
            case EMPATE -> AMBAR;
            case DERROTA -> ROJO;
            default -> GRIS;
        });

        g.setFont(negrita.deriveFont(tamano));
        centrar(g, texto, centroX, baseY);
    }

    // ---- elementos sueltos -------------------------------------------------------------------

    /** Paralelogramo beige con el escudo del rival (o uno genérico si no tiene). */
    private void pintarEscudoRival(Graphics2D g, byte[] png, int x, int y, int ancho, int alto) {

        int inclinacion = 34;
        Path2D forma = new Path2D.Double();
        forma.moveTo(x + inclinacion, y);
        forma.lineTo(x + ancho, y);
        forma.lineTo(x + ancho - inclinacion, y + alto);
        forma.lineTo(x, y + alto);
        forma.closePath();
        g.setColor(BEIGE);
        g.fill(forma);

        int lado = alto - 14;
        int cx = x + ancho / 2;
        int cy = y + alto / 2;

        BufferedImage escudo = leerEscudo(png);

        if (escudo == null) {
            pintarEscudoGenerico(g, cx, cy, lado);
            return;
        }

        double escala = Math.min((double) lado / escudo.getWidth(), (double) lado / escudo.getHeight());
        int w = (int) Math.round(escudo.getWidth() * escala);
        int h = (int) Math.round(escudo.getHeight() * escala);

        g.drawImage(escudo, cx - w / 2, cy - h / 2, w, h, null);
    }

    /** Un escudo neutro (gris, con un balón) para los rivales que no tienen el suyo. */
    void pintarEscudoGenerico(Graphics2D g, int cx, int cy, int lado) {

        double w = lado * 0.78;
        double h = lado;
        double x = cx - w / 2;
        double y = cy - h / 2;

        Path2D escudo = new Path2D.Double();
        escudo.moveTo(x, y + h * 0.08);
        escudo.curveTo(x + w * 0.3, y + h * 0.02, x + w * 0.7, y + h * 0.02, x + w, y + h * 0.08);
        escudo.lineTo(x + w, y + h * 0.55);
        escudo.curveTo(x + w, y + h * 0.8, x + w * 0.6, y + h * 0.93, x + w * 0.5, y + h);
        escudo.curveTo(x + w * 0.4, y + h * 0.93, x, y + h * 0.8, x, y + h * 0.55);
        escudo.closePath();

        g.setColor(new Color(0xC9CED6));
        g.fill(escudo);
        g.setColor(new Color(0x8E97A5));
        g.setStroke(new BasicStroke(3f));
        g.draw(escudo);

        double radio = w * 0.26;
        Ellipse2D balon = new Ellipse2D.Double(cx - radio, cy - radio * 0.8, radio * 2, radio * 2);
        g.setColor(Color.WHITE);
        g.fill(balon);
        g.setColor(new Color(0x8E97A5));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(balon);

        g.setStroke(new BasicStroke(1f));
    }

    private void pintarReloj(Graphics2D g, int cx, int cy, int radio) {

        g.setColor(AZUL);
        g.fill(new Ellipse2D.Double(cx - radio, cy - radio, radio * 2, radio * 2));

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, cy, cx, cy - radio + 6);
        g.drawLine(cx, cy, cx + radio - 7, cy + 3);
        g.setStroke(new BasicStroke(1f));
    }

    private void pintarCalendario(Graphics2D g, int cx, int cy, int lado) {

        int x = cx - lado / 2;
        int y = cy - lado / 2;

        g.setColor(AZUL_OSCURO);
        g.fill(new RoundRectangle2D.Double(x, y, lado, lado, 6, 6));

        g.setColor(Color.WHITE);
        g.fillRect(x + 4, y + 10, lado - 8, lado - 14);

        g.setColor(AZUL_OSCURO);

        for (int fila = 0; fila < 3; fila++) {
            for (int col = 0; col < 4; col++) {
                g.fillRect(x + 6 + col * 6, y + 13 + fila * 6, 4, 4);
            }
        }

        g.setColor(AZUL_OSCURO);
        g.fillRect(x + 8, y - 5, 4, 9);
        g.fillRect(x + lado - 12, y - 5, 4, 9);
    }

    private void pintarChincheta(Graphics2D g, int cx, int cy, int radio) {

        g.setColor(ROJO);
        g.fill(new Ellipse2D.Double(cx - radio, cy - radio, radio * 2, radio * 2));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Double(cx - radio + 3, cy - radio + 3, radio * 2 - 6, radio * 2 - 6));
        g.setStroke(new BasicStroke(1f));

        // El punto de la chincheta: una gota blanca con un hueco rojo.
        Path2D gota = new Path2D.Double();
        gota.moveTo(cx, cy + radio * 0.55);
        gota.curveTo(cx - radio * 0.75, cy - radio * 0.05, cx - radio * 0.45, cy - radio * 0.55, cx, cy - radio * 0.55);
        gota.curveTo(cx + radio * 0.45, cy - radio * 0.55, cx + radio * 0.75, cy - radio * 0.05, cx, cy + radio * 0.55);
        g.setColor(Color.WHITE);
        g.fill(gota);
        g.setColor(ROJO);
        g.fill(new Ellipse2D.Double(cx - radio * 0.2, cy - radio * 0.3, radio * 0.4, radio * 0.4));
    }

    // ---- utilidades de texto y recursos -----------------------------------------------------

    static String nombreDeporte(String modalidad) {

        return switch (modalidad == null ? "" : modalidad) {
            case "F8" -> "FÚTBOL 8";
            case "F5" -> "FÚTBOL 5";
            default -> "FÚTBOL 11";
        };
    }

    /** El mayor tamaño (hasta {@code maximo}) con el que el texto cabe en {@code anchoMax}. */
    private static float ajustar(Graphics2D g, Font base, String texto, float maximo, int anchoMax) {

        float tamano = maximo;

        while (tamano > 14f && g.getFontMetrics(base.deriveFont(tamano)).stringWidth(texto) > anchoMax) {
            tamano -= 1f;
        }

        return tamano;
    }

    private static List<String> partir(Graphics2D g, Font fuente, String texto, int anchoMax) {

        FontMetrics m = g.getFontMetrics(fuente);
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();

        for (String palabra : texto.split(" ")) {

            String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;

            if (actual.length() > 0 && m.stringWidth(prueba) > anchoMax) {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            } else {
                actual = new StringBuilder(prueba);
            }
        }

        if (actual.length() > 0) {
            lineas.add(actual.toString());
        }

        return lineas;
    }

    private static void centrar(Graphics2D g, String texto, int centroX, int baseY) {
        g.drawString(texto, centroX - g.getFontMetrics().stringWidth(texto) / 2, baseY);
    }

    private static void configurar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static BufferedImage leerEscudo(byte[] png) {

        if (png == null || png.length == 0) {
            return null;
        }

        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Font fuente(String fichero) {

        try (InputStream entrada = ImagenesRedesRenderer.class.getResourceAsStream("/redes/fonts/" + fichero)) {

            if (entrada == null) {
                throw new IllegalStateException("Falta la fuente " + fichero);
            }

            // Con el kerning activado ("LT", "AV"...) el espaciado se parece al de las imágenes del club.
            return Font.createFont(Font.TRUETYPE_FONT, entrada)
                    .deriveFont(java.util.Map.of(java.awt.font.TextAttribute.KERNING,
                            java.awt.font.TextAttribute.KERNING_ON));

        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("No se puede cargar la fuente " + fichero, e);
        }
    }

    private static BufferedImage imagen(String recurso) {

        try (InputStream entrada = ImagenesRedesRenderer.class.getResourceAsStream(recurso)) {

            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso " + recurso);
            }

            return ImageIO.read(entrada);

        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer " + recurso, e);
        }
    }

    /** Quita el margen transparente de una imagen (el escudo del club viene en un lienzo 16:9). */
    private static BufferedImage recortarTransparencia(BufferedImage origen) {

        int minX = origen.getWidth();
        int minY = origen.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < origen.getHeight(); y++) {
            for (int x = 0; x < origen.getWidth(); x++) {
                if (((origen.getRGB(x, y) >>> 24) & 0xFF) > 16) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < 0) {
            return origen;
        }

        return origen.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}

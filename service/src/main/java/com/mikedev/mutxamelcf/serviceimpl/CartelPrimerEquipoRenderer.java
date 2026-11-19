package com.mikedev.mutxamelcf.serviceimpl;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mikedev.mutxamelcf.model.Patrocinador;
import com.mikedev.mutxamelcf.model.PatrocinadoresClub;

/**
 * Dibuja en servidor (Java2D, sin pantalla) el cartel "Próximo partido del Primer Equipo" en PNG, en formato publicación
 * (1080×1350) o historia (1080×1920). El diseño y las medidas son los de docs/diseno-cartel-primer-equipo (medidas.md):
 * cada elemento tiene su caja y el texto se ajusta a ella. No guarda nada y no muestra nombres ni fotos de jugadores.
 */
@Component
public class CartelPrimerEquipoRenderer {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** La web que enlaza el QR. */
    public static final String URL_APP = "https://mutxamelcf.es";

    public enum Formato {
        PUBLICACION(1080, 1350), HISTORIA(1080, 1920);

        private final int ancho;
        private final int alto;

        Formato(int ancho, int alto) {
            this.ancho = ancho;
            this.alto = alto;
        }

        public int ancho() {
            return ancho;
        }

        public int alto() {
            return alto;
        }
    }

    /**
     * Todo lo que pinta el cartel, ya resuelto (textos en el idioma elegido y en mayúsculas).
     *
     * @param local            Mutxamel juega en casa (su escudo a la izquierda)
     * @param nombreRival      nombre del rival (bajo su escudo, o grande si no tiene escudo)
     * @param escudoRival      PNG del rival, o null si no tiene (rivalPropio: se pinta el del Mutxamel)
     * @param jornada          "JORNADA 11" o null si el partido no tiene jornada
     * @param logoCompeticion  recurso del classpath con el logo de la competición, o null
     * @param textoCompeticion texto en lugar del logo ("PARTIDO AMISTOSO"), o null
     * @param campo            las líneas del campo separadas por salto de línea (en casa, tres fijas)
     * @param racha            G, E o P del más antiguo al más reciente; null o vacía = sin racha
     */
    public record Datos(boolean local, String nombreMutxamel, String nombreRival, byte[] escudoRival,
            boolean rivalPropio, String titulo, String jornada, String logoCompeticion, String textoCompeticion,
            String dia, String fecha, String hora, String campo, String textoRacha, List<String> racha,
            boolean hashtags, boolean qr, String textoQr) {
    }

    // ---- medidas (docs/diseno-cartel-primer-equipo/medidas.md) ---------------------------------

    record Rect(double x, double y, double w, double h) {

        double cx() {
            return x + w / 2;
        }

        double cy() {
            return y + h / 2;
        }
    }

    private static Rect r(double x, double y, double w, double h) {
        return new Rect(x, y, w, h);
    }

    record Medidas(Rect ayuntamiento, Rect jornada, Rect competicion, Rect titulo, Rect fecha, Rect hora, Rect campo,
            Rect escudoIzquierda, Rect vs, Rect escudoDerecha, Rect nombreIzquierda, Rect nombreDerecha, Rect racha,
            Rect qr, Rect patrocinadores) {
    }

    static final Medidas PUBLICACION = new Medidas(r(40, 34, 185, 92), r(384, 53, 236, 54), r(779, 37, 261, 86),
            r(55, 146, 971, 155), r(80, 339, 202, 90), r(451, 362, 124, 45), r(745, 317, 255, 135),
            r(155, 477, 300, 300), r(485, 580, 110, 95), r(625, 477, 300, 300), r(60, 791, 460, 98),
            r(560, 791, 460, 98), r(167, 917, 572, 135), r(773, 915, 140, 139), r(40, 1124, 1000, 200));

    static final Medidas HISTORIA = new Medidas(r(40, 70, 221, 110), r(381, 98, 236, 54), r(736, 75, 304, 100),
            r(21, 265, 1037, 166), r(80, 537, 233, 102), r(438, 563, 143, 51), r(707, 512, 293, 153),
            r(125, 756, 330, 330), r(485, 873, 110, 95), r(625, 756, 330, 330), r(60, 1157, 460, 108),
            r(560, 1157, 460, 108), r(130, 1364, 646, 140), r(810, 1356, 140, 157), r(40, 1604, 1000, 266));

    // ---- diseño ----------------------------------------------------------------------------------

    private static final double COMPRESION_TITULO = 0.56;
    // El título manda el ancho; la altura (con la tilde de la Ó) puede pasar algo de la caja, como en la referencia.
    private static final double ALTURA_TITULO_MAXIMA = 1.12;
    private static final int PATROCINADORES_POR_FILA = 4;
    private static final int PADDING_LOGO = 10;

    private static final Color BLANCO = Color.WHITE;
    private static final Color VERDE = new Color(0x2E9E4F);
    private static final Color GRIS = new Color(0x8A949B);
    private static final Color ROJO = new Color(0xD64545);

    private static final double OSCURO_MAXIMO = 0.35;
    private static final double LUMINANCIA_SIN_DEGRADADO = 0.40;

    // La línea diagonal de arriba y la de abajo del VS, relativas al centro de su caja (medidas de la referencia).
    private static final double[][] DIAGONALES = { { 55, -192.5, 3, -84.5 }, { -5, 67.5, -57, 175.5 } };

    private final Font luz;
    private final Font medio;
    private final Font extraNegrita;
    private final Font negritaCursiva;
    private final Font titulo;
    private final BufferedImage escudoClub;
    private final Map<String, BufferedImage> logos = new ConcurrentHashMap<>();

    public CartelPrimerEquipoRenderer() {
        this.luz = ImagenesRedesRenderer.fuente("Inter-Light.otf");
        this.medio = ImagenesRedesRenderer.fuente("Inter-Medium.otf");
        this.extraNegrita = ImagenesRedesRenderer.fuente("Inter-ExtraBold.otf");
        this.negritaCursiva = ImagenesRedesRenderer.fuente("Inter-BlackItalic.otf");
        this.titulo = ImagenesRedesRenderer.fuente("LMRoman10-Regular.otf");
        this.escudoClub = ImagenesRedesRenderer
                .recortarTransparencia(ImagenesRedesRenderer.imagen("/redes/escudo.png"));
    }

    /** El PNG del cartel. {@code fondo} es el JPG o PNG del fondo (el de la tabla o el de por defecto). */
    public byte[] renderizar(Formato formato, Datos datos, byte[] fondo) {

        Medidas m = formato == Formato.HISTORIA ? HISTORIA : PUBLICACION;
        int ancho = formato.ancho();
        int alto = formato.alto();

        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();

        try {
            ImagenesRedesRenderer.configurar(g);

            pintarFondo(g, lienzo, leerFondo(fondo), ancho, alto);
            oscurecerSiHaceFalta(g, lienzo, ancho, alto);

            pintarCabecera(g, m, datos);
            pintarTitulo(g, m, datos);
            pintarDatos(g, m, datos);
            pintarEnfrentamiento(g, m, datos);
            pintarInferior(g, m, datos, ancho);
            pintarPatrocinadores(g, m);

        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido codificar el cartel", e);
        }
    }

    // ---- fondo -------------------------------------------------------------------------------------

    private static BufferedImage leerFondo(byte[] fondo) {

        try {

            BufferedImage imagen = fondo == null ? null : ImageIO.read(new ByteArrayInputStream(fondo));

            if (imagen == null) {
                throw new IllegalStateException("El fondo del cartel no es una imagen válida");
            }

            return imagen;

        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido leer el fondo del cartel", e);
        }
    }

    /** La foto a pantalla completa: escalada para cubrir el lienzo y recortada al centro ("cover"). */
    private static void pintarFondo(Graphics2D g, BufferedImage lienzo, BufferedImage foto, int ancho, int alto) {

        double escala = Math.max((double) ancho / foto.getWidth(), (double) alto / foto.getHeight());
        int anchoEscalado = (int) Math.max(ancho, Math.ceil(foto.getWidth() * escala));
        int altoEscalado = (int) Math.max(alto, Math.ceil(foto.getHeight() * escala));

        BufferedImage escalada = escalar(foto, anchoEscalado, altoEscalado);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, ancho, alto);
        g.drawImage(escalada, (ancho - anchoEscalado) / 2, (alto - altoEscalado) / 2, null);
    }

    /**
     * Si la foto es muy clara arriba o abajo, un degradado negro suave (como mucho el 35 %) para que el texto blanco se
     * lea. Con el fondo por defecto (oscuro) no se dibuja nada.
     */
    private static void oscurecerSiHaceFalta(Graphics2D g, BufferedImage lienzo, int ancho, int alto) {

        int bandaArriba = (int) (alto * 0.30);
        int bandaAbajo = (int) (alto * 0.38);

        double arriba = oscuridadNecesaria(luminancia(lienzo, 0, bandaArriba));
        double abajo = oscuridadNecesaria(luminancia(lienzo, alto - bandaAbajo, alto));

        if (arriba > 0) {
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, (int) Math.round(arriba * 255)), 0, bandaArriba,
                    new Color(0, 0, 0, 0)));
            g.fillRect(0, 0, ancho, bandaArriba);
        }

        if (abajo > 0) {
            g.setPaint(new GradientPaint(0, alto, new Color(0, 0, 0, (int) Math.round(abajo * 255)), 0,
                    alto - bandaAbajo, new Color(0, 0, 0, 0)));
            g.fillRect(0, alto - bandaAbajo, ancho, bandaAbajo);
        }

        g.setPaint(null);
    }

    static double oscuridadNecesaria(double luminancia) {

        if (luminancia <= LUMINANCIA_SIN_DEGRADADO) {
            return 0;
        }

        return Math.min(OSCURO_MAXIMO, (luminancia - LUMINANCIA_SIN_DEGRADADO) / 0.35 * OSCURO_MAXIMO);
    }

    /** Luminancia media (0 negro, 1 blanco) de las filas [desde, hasta) de la imagen. */
    static double luminancia(BufferedImage imagen, int desde, int hasta) {

        double suma = 0;
        long total = 0;

        for (int y = Math.max(0, desde); y < Math.min(imagen.getHeight(), hasta); y += 6) {
            for (int x = 0; x < imagen.getWidth(); x += 6) {
                int rgb = imagen.getRGB(x, y);
                suma += (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
                total++;
            }
        }

        return total == 0 ? 0 : suma / total;
    }

    // ---- cabecera ----------------------------------------------------------------------------------

    private void pintarCabecera(Graphics2D g, Medidas m, Datos d) {

        Patrocinador destacado = PatrocinadoresClub.destacadoCartel();

        if (destacado != null) {
            BufferedImage logo = logo(destacado.recursoLogoCartel());

            if (logo != null) {
                dibujarContenido(g, logo, m.ayuntamiento(), 0);
            }
        }

        g.setColor(BLANCO);

        if (d.jornada() != null) {
            // Letras algo juntas y un solo tamaño para cualquier jornada (el que cabe con dos cifras).
            Font base = luz.deriveFont(Map.of(TextAttribute.TRACKING, -0.02f));
            Font fuente = ajustar(g, base, 200, m.jornada().w(), m.jornada().h(), "JORNADA 88");
            centrarEnCaja(g, fuente, d.jornada(), m.jornada());
        }

        if (d.logoCompeticion() != null) {
            BufferedImage logo = logo(d.logoCompeticion());

            if (logo != null) {
                dibujarContenido(g, logo, m.competicion(), 0);
                return;
            }
        }

        if (d.textoCompeticion() != null && !d.textoCompeticion().isBlank()) {
            Rect caja = m.competicion();
            Ajuste ajuste = ajustarLineas(g, luz, d.textoCompeticion(), 2, caja.w(), caja.h(), caja.h() * 0.45, 1.05);
            lineas(g, ajuste, caja, 0, true);
        }
    }

    private void pintarTitulo(Graphics2D g, Medidas m, Datos d) {

        Rect caja = m.titulo();
        Font mejor = null;

        // El mayor tamaño con el que el título (comprimido en horizontal) ocupa el ancho de la caja sin salirse.
        for (float tamano = 400f; tamano > 10f; tamano -= 1f) {

            Font candidata = tituloConTamano(tamano);
            Rectangle2D limites = limites(g, candidata, d.titulo());

            if (limites.getWidth() <= caja.w() && limites.getHeight() <= caja.h() * ALTURA_TITULO_MAXIMA) {
                mejor = candidata;
                break;
            }
        }

        if (mejor == null) {
            mejor = tituloConTamano(10f);
        }

        g.setColor(BLANCO);
        centrarEnCaja(g, mejor, d.titulo(), caja);
    }

    private Font tituloConTamano(float tamano) {
        return titulo.deriveFont(tamano).deriveFont(AffineTransform.getScaleInstance(COMPRESION_TITULO, 1));
    }

    // ---- fecha, hora y campo ---------------------------------------------------------------------

    private void pintarDatos(Graphics2D g, Medidas m, Datos d) {

        g.setColor(BLANCO);

        // Día y fecha, en dos líneas alineadas a la derecha. Un solo tamaño para cualquier día.
        Rect fecha = m.fecha();
        Font fuenteFecha = ajustar(g, luz, 200, fecha.w(), fecha.h() / 2 * 1.2, d.dia(), d.fecha(), "00/00/0000");
        Ajuste ajusteFecha = new Ajuste(fuenteFecha, List.of(d.dia(), d.fecha()), 1.04);
        lineas(g, ajusteFecha, fecha, 1, true);

        // Hora.
        Rect hora = m.hora();
        Font fuenteHora = ajustar(g, luz, 200, hora.w(), hora.h(), d.hora(), "00:00H");
        centrarEnCaja(g, fuenteHora, d.hora(), hora);

        // Campo: a la izquierda, hasta tres líneas.
        Rect campo = m.campo();

        if (d.campo() != null && !d.campo().isBlank()) {
            Ajuste ajuste = ajustarCampo(g, d.campo(), campo);
            lineas(g, ajuste, campo, -1, true);
        }
    }

    private Ajuste ajustarCampo(Graphics2D g, String campo, Rect caja) {

        double maximo = caja.h() / 3 / 1.04;

        String[] fijas = campo.split("\n");

        if (fijas.length > 1) {
            // Líneas fijas (el campo de casa): un tamaño con el que todas caben.
            Font fuente = ajustar(g, luz, (float) maximo, caja.w(), caja.h() / 3 * 1.04, fijas);
            return new Ajuste(fuente, List.of(fijas), 1.04);
        }

        return ajustarLineas(g, luz, campo, 3, caja.w(), caja.h(), maximo, 1.04);
    }

    // ---- escudos, VS y nombres -------------------------------------------------------------------

    private void pintarEnfrentamiento(Graphics2D g, Medidas m, Datos d) {

        BufferedImage mutxamel = escudoClub;
        BufferedImage rival = d.rivalPropio() ? escudoClub : leerEscudo(d.escudoRival());

        Rect cajaMutxamel = d.local() ? m.escudoIzquierda() : m.escudoDerecha();
        Rect cajaRival = d.local() ? m.escudoDerecha() : m.escudoIzquierda();
        Rect nombreMutxamel = d.local() ? m.nombreIzquierda() : m.nombreDerecha();
        Rect nombreRival = d.local() ? m.nombreDerecha() : m.nombreIzquierda();

        dibujarEscudo(g, mutxamel, cajaMutxamel);

        g.setColor(BLANCO);

        if (rival != null) {
            dibujarEscudo(g, rival, cajaRival);
        } else {
            // Rival sin escudo: su nombre en grande en su hueco (hasta tres líneas) y nada debajo.
            Ajuste grande = ajustarLineas(g, medio, d.nombreRival(), 3, cajaRival.w(), cajaRival.h(), 96, 1.12);
            lineas(g, grande, cajaRival, 0, true);
        }

        // VS y sus dos diagonales.
        g.setColor(BLANCO);
        Font fuenteVs = ajustar(g, negritaCursiva, 300, m.vs().w(), m.vs().h(), "VS");
        centrarEnCaja(g, fuenteVs, "VS", m.vs());

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (double[] l : DIAGONALES) {
            g.draw(new Line2D.Double(m.vs().cx() + l[0], m.vs().cy() + l[1], m.vs().cx() + l[2], m.vs().cy() + l[3]));
        }

        // Nombres bajo cada escudo: un mismo tamaño en los dos y como mucho dos líneas.
        String nombreM = d.nombreMutxamel();
        String nombreR = rival != null ? d.nombreRival() : null;
        double maximo = nombreMutxamel.h() * 0.36;

        double tamano = maximo;
        Ajuste ajusteM = ajustarLineas(g, luz, nombreM, 2, nombreMutxamel.w(), nombreMutxamel.h(), maximo, 1.04);
        tamano = Math.min(tamano, ajusteM.fuente().getSize2D());

        Ajuste ajusteR = null;

        if (nombreR != null) {
            ajusteR = ajustarLineas(g, luz, nombreR, 2, nombreRival.w(), nombreRival.h(), maximo, 1.04);
            tamano = Math.min(tamano, ajusteR.fuente().getSize2D());
        }

        Font comun = luz.deriveFont((float) tamano);

        lineas(g, new Ajuste(comun, partir(g, comun, nombreM, nombreMutxamel.w()), 1.04), nombreMutxamel, 0, false);

        if (ajusteR != null) {
            lineas(g, new Ajuste(comun, partir(g, comun, nombreR, nombreRival.w()), 1.04), nombreRival, 0, false);
        }
    }

    /** El escudo encajado en su caja (sin deformarlo) con una sombra suave debajo. */
    private static void dibujarEscudo(Graphics2D g, BufferedImage escudo, Rect caja) {

        double escala = Math.min(caja.w() / escudo.getWidth(), caja.h() / escudo.getHeight());
        int ancho = Math.max(1, (int) Math.round(escudo.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(escudo.getHeight() * escala));
        int x = (int) Math.round(caja.cx() - ancho / 2.0);
        int y = (int) Math.round(caja.cy() - alto / 2.0);

        BufferedImage escalado = escalar(escudo, ancho, alto);

        int radio = Math.max(6, (int) (caja.h() * 0.035));
        BufferedImage sombra = sombra(escalado, radio);

        java.awt.Composite anterior = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.drawImage(sombra, x - radio * 2, y - radio * 2 + radio, null);
        g.setComposite(anterior);

        g.drawImage(escalado, x, y, null);
    }

    /** La silueta del escudo en negro y difuminada. */
    static BufferedImage sombra(BufferedImage escudo, int radio) {

        int margen = radio * 2;
        BufferedImage silueta = new BufferedImage(escudo.getWidth() + margen * 2, escudo.getHeight() + margen * 2,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D gs = silueta.createGraphics();

        try {
            gs.drawImage(escudo, margen, margen, null);
            gs.setComposite(AlphaComposite.SrcIn);
            gs.setColor(Color.BLACK);
            gs.fillRect(0, 0, silueta.getWidth(), silueta.getHeight());
        } finally {
            gs.dispose();
        }

        int lado = radio * 2 + 1;
        float[] datos = new float[lado];
        java.util.Arrays.fill(datos, 1f / lado);

        BufferedImage horizontal = new ConvolveOp(new Kernel(lado, 1, datos), ConvolveOp.EDGE_NO_OP, null)
                .filter(silueta, null);

        return new ConvolveOp(new Kernel(1, lado, datos), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null);
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

    // ---- racha, hashtags y QR ----------------------------------------------------------------------

    private void pintarInferior(Graphics2D g, Medidas m, Datos d, int ancho) {

        boolean hayRacha = d.racha() != null && !d.racha().isEmpty();
        boolean hayIzquierda = hayRacha || d.hashtags();
        boolean hayQr = d.qr();

        if (!hayIzquierda && !hayQr) {
            return;
        }

        // Lo que quede se centra en la fila, con el mismo hueco que entre el bloque y el QR de la referencia.
        double hueco = m.qr().x() - (m.racha().x() + m.racha().w());
        double total = (hayIzquierda ? m.racha().w() : 0) + (hayIzquierda && hayQr ? hueco : 0)
                + (hayQr ? m.qr().w() : 0);
        double x = ancho / 2.0 - total / 2;

        if (hayIzquierda) {
            pintarRachaYHashtags(g, new Rect(x, m.racha().y(), m.racha().w(), m.racha().h()), d, hayRacha);
            x += m.racha().w() + hueco;
        }

        if (hayQr) {
            pintarQr(g, new Rect(x, m.qr().y(), m.qr().w(), m.qr().h()), d.textoQr());
        }
    }

    private void pintarRachaYHashtags(Graphics2D g, Rect caja, Datos d, boolean hayRacha) {

        double k = caja.h() / 135.0;
        boolean hayHashtags = d.hashtags();

        // Alturas (desde arriba de la caja) del rótulo, los círculos y los hashtags cuando salen los tres.
        double rotuloY = 14 * k;
        double circulosY = 64 * k;
        double hashtagsY = 118 * k;

        if (hayRacha && !hayHashtags) {
            // Solo la racha: centrada en vertical (el grupo ocupa de 6k a 87k).
            double desplazamiento = caja.h() / 2 - 46.5 * k;
            rotuloY += desplazamiento;
            circulosY += desplazamiento;
        } else if (!hayRacha) {
            hashtagsY = caja.h() / 2;
        }

        g.setColor(BLANCO);

        if (hayRacha) {

            Font rotulo = medio.deriveFont(Map.of(TextAttribute.SIZE, (float) (23 * k), TextAttribute.TRACKING, 0.14f));
            centrarEnPunto(g, rotulo, d.textoRacha(), caja.cx(), caja.y() + rotuloY);

            double diametro = 46 * k;
            double paso = 56 * k;
            int n = d.racha().size();
            double inicio = caja.cx() - ((n - 1) * paso) / 2;
            Font letra = extraNegrita.deriveFont((float) (24 * k));

            for (int i = 0; i < n; i++) {

                String resultado = d.racha().get(i);
                double cx = inicio + i * paso;
                double cy = caja.y() + circulosY;

                g.setColor(colorResultado(resultado));
                g.fill(new Ellipse2D.Double(cx - diametro / 2, cy - diametro / 2, diametro, diametro));

                g.setColor(BLANCO);
                centrarEnPunto(g, letra, resultado, cx, cy);
            }
        }

        if (hayHashtags) {
            Font fuente = ajustar(g, extraNegrita, (float) (60 * k), caja.w(), caja.h() * 0.4,
                    "#somMTX #mareaAzul #MutxamelCF");
            g.setColor(BLANCO);
            centrarEnPunto(g, fuente, "#somMTX #mareaAzul #MutxamelCF", caja.cx(), caja.y() + hashtagsY);
        }
    }

    static Color colorResultado(String resultado) {
        return switch (resultado) {
            case "G" -> VERDE;
            case "P" -> ROJO;
            default -> GRIS;
        };
    }

    private void pintarQr(Graphics2D g, Rect caja, String texto) {

        double lado = caja.h() * 0.805;
        double x = caja.cx() - lado / 2;

        g.setColor(BLANCO);
        g.fill(new RoundRectangle2D.Double(x, caja.y(), lado, lado, lado * 0.12, lado * 0.12));

        int interior = (int) Math.round(lado * 0.86);
        BufferedImage qr = escalar(imagenQr(), interior, interior);
        g.drawImage(qr, (int) Math.round(caja.cx() - interior / 2.0), (int) Math.round(caja.y() + (lado - interior) / 2),
                null);

        double alturaTexto = caja.h() - lado;
        Font fuente = ajustar(g, medio, 60, caja.w(), alturaTexto * 0.7, texto);
        g.setColor(BLANCO);
        centrarEnPunto(g, fuente, texto, caja.cx(), caja.y() + lado + alturaTexto / 2);
    }

    /** El QR de {@link #URL_APP} en blanco y negro, sin margen (el margen es el fondo blanco). */
    static BufferedImage imagenQr() {

        try {

            BitMatrix matriz = new QRCodeWriter().encode(URL_APP, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));

            int modulos = matriz.getWidth();
            int ampliacion = Math.max(1, 600 / modulos);
            BufferedImage imagen = new BufferedImage(modulos * ampliacion, modulos * ampliacion,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagen.createGraphics();

            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
                g.setColor(Color.BLACK);

                for (int y = 0; y < modulos; y++) {
                    for (int x = 0; x < modulos; x++) {
                        if (matriz.get(x, y)) {
                            g.fillRect(x * ampliacion, y * ampliacion, ampliacion, ampliacion);
                        }
                    }
                }
            } finally {
                g.dispose();
            }

            return imagen;

        } catch (WriterException e) {
            throw new IllegalStateException("No se ha podido generar el QR", e);
        }
    }

    // ---- patrocinadores ----------------------------------------------------------------------------

    private void pintarPatrocinadores(Graphics2D g, Medidas m) {

        List<Patrocinador> lista = PatrocinadoresClub.paraCartel();

        if (lista.isEmpty()) {
            return;
        }

        Rect caja = m.patrocinadores();
        int filas = (lista.size() + PATROCINADORES_POR_FILA - 1) / PATROCINADORES_POR_FILA;
        double anchoCelda = caja.w() / PATROCINADORES_POR_FILA;
        double altoCelda = caja.h() / Math.max(2, filas);
        double y0 = caja.y() + (caja.h() - altoCelda * filas) / 2;

        for (int fila = 0; fila < filas; fila++) {

            int desde = fila * PATROCINADORES_POR_FILA;
            int hasta = Math.min(lista.size(), desde + PATROCINADORES_POR_FILA);
            double anchoFila = anchoCelda * (hasta - desde);
            double x0 = caja.cx() - anchoFila / 2;

            for (int i = desde; i < hasta; i++) {

                BufferedImage logo = logo(lista.get(i).recursoLogoCartel());

                if (logo != null) {
                    Rect celda = new Rect(x0 + (i - desde) * anchoCelda, y0 + fila * altoCelda, anchoCelda, altoCelda);
                    dibujarContenido(g, logo, celda, PADDING_LOGO);
                }
            }
        }
    }

    // ---- utilidades de dibujo ----------------------------------------------------------------------

    /** La imagen encajada (contain) y centrada en la caja, sin deformarla. */
    private static void dibujarContenido(Graphics2D g, BufferedImage imagen, Rect caja, double padding) {

        double cajaW = caja.w() - padding * 2;
        double cajaH = caja.h() - padding * 2;
        double escala = Math.min(cajaW / imagen.getWidth(), cajaH / imagen.getHeight());
        int ancho = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(imagen.getHeight() * escala));

        g.drawImage(escalar(imagen, ancho, alto), (int) Math.round(caja.cx() - ancho / 2.0),
                (int) Math.round(caja.cy() - alto / 2.0), null);
    }

    private BufferedImage logo(String recurso) {

        if (recurso == null) {
            return null;
        }

        return logos.computeIfAbsent(recurso, ImagenesRedesRenderer::imagen);
    }

    /** Reduce por pasos (a lo sumo a la mitad cada vez) y siempre en ARGB: sin dientes de sierra y con transparencia. */
    static BufferedImage escalar(BufferedImage origen, int ancho, int alto) {

        BufferedImage actual = origen;
        int anchoActual = origen.getWidth();
        int altoActual = origen.getHeight();

        if (anchoActual == ancho && altoActual == alto && origen.getType() == BufferedImage.TYPE_INT_ARGB) {
            return origen;
        }

        boolean primera = true;

        while (primera || anchoActual != ancho || altoActual != alto) {

            primera = false;

            anchoActual = anchoActual > ancho ? Math.max(ancho, anchoActual / 2) : ancho;
            altoActual = altoActual > alto ? Math.max(alto, altoActual / 2) : alto;

            BufferedImage paso = new BufferedImage(anchoActual, altoActual, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = paso.createGraphics();

            try {
                ImagenesRedesRenderer.configurar(g);
                g.drawImage(actual, 0, 0, anchoActual, altoActual, null);
            } finally {
                g.dispose();
            }

            actual = paso;
        }

        return actual;
    }

    private static Rectangle2D limites(Graphics2D g, Font fuente, String texto) {
        if (texto == null || texto.isEmpty()) {
            return new Rectangle2D.Double();
        }

        return new TextLayout(texto, fuente, g.getFontRenderContext()).getBounds();
    }

    /** El mayor tamaño (hasta {@code maximo}) con el que todos los textos caben en ancho × alto (límites visuales). */
    static Font ajustar(Graphics2D g, Font base, float maximo, double ancho, double alto, String... textos) {

        for (float tamano = maximo; tamano > 8f; tamano -= 0.5f) {

            Font candidata = base.deriveFont(tamano);
            boolean cabe = true;

            for (String texto : textos) {

                Rectangle2D limites = limites(g, candidata, texto);

                if (limites.getWidth() > ancho || limites.getHeight() > alto) {
                    cabe = false;
                    break;
                }
            }

            if (cabe) {
                return candidata;
            }
        }

        return base.deriveFont(8f);
    }

    /** Dibuja el texto con el centro de sus límites visuales en el centro de la caja. */
    private static void centrarEnCaja(Graphics2D g, Font fuente, String texto, Rect caja) {
        centrarEnPunto(g, fuente, texto, caja.cx(), caja.cy());
    }

    /** Dibuja el texto con el centro de sus límites visuales en ese punto. */
    private static void centrarEnPunto(Graphics2D g, Font fuente, String texto, double cx, double cy) {

        if (texto == null || texto.isEmpty()) {
            return;
        }

        TextLayout layout = new TextLayout(texto, fuente, g.getFontRenderContext());
        Rectangle2D b = layout.getBounds();

        layout.draw(g, (float) (cx - (b.getX() + b.getWidth() / 2)), (float) (cy - (b.getY() + b.getHeight() / 2)));
    }

    /** Un texto partido en líneas con su fuente y el paso entre líneas (en veces el tamaño). */
    record Ajuste(Font fuente, List<String> lineas, double paso) {
    }

    /**
     * El mayor tamaño (hasta {@code maximo}) con el que el texto, partido por palabras, cabe en {@code maxLineas}
     * líneas dentro de la caja.
     */
    static Ajuste ajustarLineas(Graphics2D g, Font base, String texto, int maxLineas, double ancho, double alto,
            double maximo, double paso) {

        for (double tamano = maximo; tamano > 8; tamano -= 0.5) {

            Font fuente = base.deriveFont((float) tamano);
            List<String> lineas = partir(g, fuente, texto, ancho);
            boolean cabe = lineas.size() <= maxLineas && lineas.size() * tamano * paso <= alto;

            for (String linea : lineas) {
                if (cabe && limites(g, fuente, linea).getWidth() > ancho) {
                    cabe = false;
                }
            }

            if (cabe) {
                return new Ajuste(fuente, lineas, paso);
            }
        }

        Font minima = base.deriveFont(8f);

        return new Ajuste(minima, partir(g, minima, texto, ancho), paso);
    }

    /** Reparte el texto en líneas por palabras para que ninguna pase de {@code ancho} (salvo una palabra sola). */
    static List<String> partir(Graphics2D g, Font fuente, String texto, double ancho) {

        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();

        for (String palabra : texto.trim().split("\\s+")) {

            String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;

            if (actual.length() > 0 && limites(g, fuente, prueba).getWidth() > ancho) {
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

    /**
     * Pinta las líneas dentro de la caja. {@code alineacion}: -1 izquierda, 0 centro, 1 derecha;
     * {@code centradoVertical}: el bloque centrado en la caja (si no, pegado arriba).
     */
    private static void lineas(Graphics2D g, Ajuste ajuste, Rect caja, int alineacion, boolean centradoVertical) {

        Font fuente = ajuste.fuente();
        double paso = fuente.getSize2D() * ajuste.paso();
        double alturaMayuscula = limites(g, fuente, "H").getHeight();
        double arriba = centradoVertical ? caja.cy() - ajuste.lineas().size() * paso / 2 : caja.y();

        for (int i = 0; i < ajuste.lineas().size(); i++) {

            String linea = ajuste.lineas().get(i);

            if (linea.isEmpty()) {
                continue;
            }

            TextLayout layout = new TextLayout(linea, fuente, g.getFontRenderContext());
            Rectangle2D b = layout.getBounds();

            double x = switch (alineacion) {
                case -1 -> caja.x() - b.getX();
                case 1 -> caja.x() + caja.w() - (b.getX() + b.getWidth());
                default -> caja.cx() - (b.getX() + b.getWidth() / 2);
            };
            double baseline = arriba + i * paso + (paso + alturaMayuscula) / 2;

            layout.draw(g, (float) x, (float) baseline);
        }
    }
}

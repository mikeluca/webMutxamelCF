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

    /**
     * Como se dimensionan y colocan los patrocinadores de abajo: el tamano de cada logo sale de su proporcion
     * (ancho = k*raiz(a), alto = k/raiz(a), limitados a [hmax] de alto y [wmax] de ancho), las filas se llenan de
     * izquierda a derecha hasta [ANCHO_UTIL_PATROCINADORES], y el bloque se ancla abajo a [margenInferior] del final.
     */
    record Patrocinio(double k, double hmax, double wmax, double huecoH, double huecoV, double margenInferior) {
    }

    /** El ancho util de una fila de patrocinadores (de x=40 a x=1040, centrada en el lienzo). */
    static final double ANCHO_UTIL_PATROCINADORES = 1000;

    /** El hueco minimo entre dos bloques seguidos (nombres, fila de racha/QR y patrocinadores). */
    static final double HUECO_MINIMO_BLOQUES = 8;

    /**
     * Las cajas de cada elemento. En [racha] y [qr] solo importan el ancho y el alto: su posicion vertical se calcula
     * (centrada entre los nombres y los patrocinadores, que se anclan abajo).
     */
    record Medidas(Rect ayuntamiento, Rect jornada, Rect competicion, Rect titulo, Rect fecha, Rect hora, Rect campo,
            Rect escudoIzquierda, Rect vs, Rect escudoDerecha, Rect nombreIzquierda, Rect nombreDerecha, Rect racha,
            Rect qr, Patrocinio patrocinio, double escudoReferencia) {
    }

    static final Medidas PUBLICACION = new Medidas(r(40, 34, 185, 92), r(384, 53, 236, 54), r(779, 37, 261, 86),
            r(55, 146, 971, 155), r(80, 339, 202, 90), r(451, 362, 124, 45), r(745, 317, 255, 135),
            r(174, 474, 262, 262), r(485, 558, 110, 95), r(644, 474, 262, 262), r(60, 748, 460, 98),
            r(560, 748, 460, 98), r(167, 0, 572, 135), r(773, 0, 140, 139), new Patrocinio(150, 112, 300, 40, 18, 26),
            300);

    static final Medidas HISTORIA = new Medidas(r(40, 70, 221, 110), r(381, 98, 236, 54), r(736, 75, 304, 100),
            r(21, 265, 1037, 166), r(80, 537, 233, 102), r(438, 563, 143, 51), r(707, 512, 293, 153),
            r(135, 745, 310, 310), r(485, 853, 110, 95), r(635, 745, 310, 310), r(60, 1067, 460, 108),
            r(560, 1067, 460, 108), r(130, 0, 646, 140), r(810, 0, 140, 157), new Patrocinio(200, 150, 330, 50, 34, 50),
            330);

    static Medidas medidas(Formato formato) {
        return formato == Formato.HISTORIA ? HISTORIA : PUBLICACION;
    }

    // ---- patrocinadores: tamanos y filas ---------------------------------------------------------------

    /** El tamano de un logo. */
    record Tamano(double ancho, double alto) {
    }

    /**
     * El tamano de un logo de proporcion [aspecto] (ancho/alto de su contenido no transparente) que iguala el peso
     * visual: ancho = k*raiz(a) y alto = k/raiz(a); si el alto pasa de [hmax], alto = hmax y ancho = hmax*a; si el
     * ancho pasa de [wmax], ancho = wmax y alto = wmax/a.
     */
    static Tamano tamanoLogo(double aspecto, double k, double hmax, double wmax) {

        double ancho = k * Math.sqrt(aspecto);
        double alto = k / Math.sqrt(aspecto);

        if (alto > hmax) {
            alto = hmax;
            ancho = hmax * aspecto;
        }

        if (ancho > wmax) {
            ancho = wmax;
            alto = wmax / aspecto;
        }

        return new Tamano(ancho, alto);
    }

    /** Un logo colocado: su posicion en la lista de patrocinadores y su caja. */
    record Colocado(int indice, Rect caja) {
    }

    /** Los logos colocados, cuantos hay en cada fila y la caja que ocupa todo el bloque. */
    record Bloque(Rect caja, List<Colocado> logos, List<Integer> logosPorFila) {
    }

    /**
     * Coloca los logos (por su proporcion, en el orden dado): las filas se llenan de izquierda a derecha sin pasar de
     * {@link #ANCHO_UTIL_PATROCINADORES}, cada una centrada en el lienzo y con sus logos centrados en vertical entre
     * ellos, y el bloque se ancla abajo a {@code margenInferior} del final.
     */
    static Bloque colocarPatrocinadores(List<Double> aspectos, Patrocinio p, double anchoLienzo, double altoLienzo) {

        List<List<Integer>> filas = new ArrayList<>();
        List<Tamano> tamanos = new ArrayList<>();
        List<Integer> actual = new ArrayList<>();
        double anchoActual = 0;

        for (int i = 0; i < aspectos.size(); i++) {

            Tamano t = tamanoLogo(aspectos.get(i), p.k(), p.hmax(), p.wmax());
            tamanos.add(t);

            double conEste = actual.isEmpty() ? t.ancho() : anchoActual + p.huecoH() + t.ancho();

            if (!actual.isEmpty() && conEste > ANCHO_UTIL_PATROCINADORES) {
                filas.add(actual);
                actual = new ArrayList<>();
                conEste = t.ancho();
            }

            actual.add(i);
            anchoActual = conEste;
        }

        if (!actual.isEmpty()) {
            filas.add(actual);
        }

        double altoTotal = 0;
        List<Double> altosFila = new ArrayList<>();

        for (List<Integer> fila : filas) {
            double alto = fila.stream().mapToDouble(i -> tamanos.get(i).alto()).max().orElse(0);
            altosFila.add(alto);
            altoTotal += alto;
        }

        altoTotal += p.huecoV() * Math.max(0, filas.size() - 1);

        double abajo = altoLienzo - p.margenInferior();
        double y = abajo - altoTotal;
        double anchoBloque = 0;
        List<Colocado> colocados = new ArrayList<>();
        List<Integer> porFila = new ArrayList<>();

        for (int f = 0; f < filas.size(); f++) {

            List<Integer> fila = filas.get(f);
            double anchoFila = p.huecoH() * (fila.size() - 1)
                    + fila.stream().mapToDouble(i -> tamanos.get(i).ancho()).sum();
            double x = anchoLienzo / 2 - anchoFila / 2;

            anchoBloque = Math.max(anchoBloque, anchoFila);
            porFila.add(fila.size());

            for (int i : fila) {

                Tamano t = tamanos.get(i);
                colocados.add(new Colocado(i, new Rect(x, y + (altosFila.get(f) - t.alto()) / 2, t.ancho(), t.alto())));
                x += t.ancho() + p.huecoH();
            }

            y += altosFila.get(f) + p.huecoV();
        }

        return new Bloque(new Rect(anchoLienzo / 2 - anchoBloque / 2, abajo - altoTotal, anchoBloque, altoTotal),
                colocados, porFila);
    }

    /** El bloque de patrocinadores del cartel de ese formato con los logos reales (sus proporciones recortadas). */
    static Bloque bloquePatrocinadores(Formato formato) {

        List<Double> aspectos = new ArrayList<>();

        for (Patrocinador p : PatrocinadoresClub.paraCartel()) {
            BufferedImage logo = logoRecortado(p.recursoLogoCartel());
            aspectos.add((double) logo.getWidth() / logo.getHeight());
        }

        return colocarPatrocinadores(aspectos, medidas(formato).patrocinio(), formato.ancho(), formato.alto());
    }

    /**
     * La caja de la fila de racha + hashtags y QR: centrada en vertical entre los nombres de los escudos y los
     * patrocinadores (que se anclan abajo). Su alto es el del mas alto de los que salen.
     */
    static Rect cajaFilaInferior(Formato formato, boolean izquierda, boolean qr) {

        Medidas m = medidas(formato);
        double alto = Math.max(izquierda ? m.racha().h() : 0, qr ? m.qr().h() : 0);
        double arriba = m.nombreIzquierda().y() + m.nombreIzquierda().h();
        double abajo = bloquePatrocinadores(formato).caja().y();
        double y = arriba + (abajo - arriba - alto) / 2;

        return new Rect(m.racha().x(), y, m.qr().x() + m.qr().w() - m.racha().x(), alto);
    }

    // ---- diseño ----------------------------------------------------------------------------------

    private static final double COMPRESION_TITULO = 0.56;
    // El paso entre líneas del texto de datos y el borde derecho al que puede llegar el campo.
    private static final double PASO_LINEAS = 1.04;
    private static final double MARGEN_DERECHO = 1040;
    // El título manda el ancho; la altura (con la tilde de la Ó) puede pasar algo de la caja, como en la referencia.
    private static final double ALTURA_TITULO_MAXIMA = 1.12;

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

        Medidas m = medidas(formato);
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
            pintarInferior(g, m, formato, datos, ancho);
            pintarPatrocinadores(g, formato);

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

            String[] fijas = d.campo().split("\n");

            if (fijas.length > 1) {
                // Líneas fijas (el campo de casa): un tamaño con el que todas caben, centradas en su caja.
                double maximo = campo.h() / 3 / PASO_LINEAS;
                Font fuente = ajustar(g, luz, (float) maximo, campo.w(), campo.h() / 3 * PASO_LINEAS, fijas);
                lineas(g, new Ajuste(fuente, List.of(fijas), PASO_LINEAS), campo, -1, true);
            } else {
                // Fuera: el bloque se ancla arriba, en la línea de la fecha (nunca por encima de su caja), y la caja llega
                // hasta el margen derecho.
                double arribaFecha = fecha.cy() - 2 * fuenteFecha.getSize2D() * PASO_LINEAS / 2;
                double arriba = Math.max(campo.y(), arribaFecha);
                Rect caja = new Rect(campo.x(), arriba, MARGEN_DERECHO - campo.x(), campo.y() + campo.h() - arriba);

                lineas(g, ajustarCampoFuera(g, luz, d.campo(), caja.w(), caja.h(), campo.h() / 3 / PASO_LINEAS), caja,
                        -1, false);
            }
        }
    }

    /**
     * El campo de fuera con el menor número de líneas posible: prueba 1, 2 y 3 líneas (repartidas por palabras con la
     * longitud equilibrada) y se queda con la de menos líneas cuyo tamaño de letra sea al menos el 85 % del mayor
     * posible; si ninguna llega, la de mayor tamaño.
     *
     * @param maximo el tamaño de letra más grande que se admite
     */
    static Ajuste ajustarCampoFuera(Graphics2D g, Font base, String texto, double ancho, double alto,
            double maximo) {

        Ajuste[] opciones = new Ajuste[3];
        double mejor = 0;

        for (int lineas = 1; lineas <= 3; lineas++) {

            opciones[lineas - 1] = ajustarEnLineas(g, base, texto, lineas, ancho, alto, maximo);

            if (opciones[lineas - 1] != null) {
                mejor = Math.max(mejor, opciones[lineas - 1].fuente().getSize2D());
            }
        }

        Ajuste elegida = null;

        for (Ajuste opcion : opciones) {
            if (opcion != null && opcion.fuente().getSize2D() >= mejor * 0.85) {
                elegida = opcion;
                break;
            }
        }

        if (elegida == null) {
            // Nada cabe en los tamaños admitidos (una palabra enorme): la mínima, partida como se pueda.
            Font minima = base.deriveFont(8f);

            return new Ajuste(minima, partir(g, minima, texto, ancho), PASO_LINEAS);
        }

        return elegida;
    }

    /** El mayor tamaño con el que el texto cabe repartido en exactamente [lineas] líneas; null si no cabe (o no hay palabras). */
    private static Ajuste ajustarEnLineas(Graphics2D g, Font base, String texto, int lineas, double ancho,
            double alto, double maximo) {

        String[] palabras = texto.trim().split("\s+");

        if (palabras.length < lineas) {
            return null;
        }

        for (double tamano = maximo; tamano > 8; tamano -= 0.5) {

            if (lineas * tamano * PASO_LINEAS > alto) {
                continue;
            }

            Font fuente = base.deriveFont((float) tamano);
            List<String> repartidas = repartirEquilibrado(g, fuente, palabras, lineas);

            if (repartidas.stream().allMatch(l -> limites(g, fuente, l).getWidth() <= ancho)) {
                return new Ajuste(fuente, repartidas, PASO_LINEAS);
            }
        }

        return null;
    }

    /**
     * Reparte las palabras en [lineas] líneas seguidas buscando que la más ancha sea lo más estrecha posible (a igualdad,
     * la que deja las líneas más parecidas): "LES COSTERES" / "GATA" y no "LES" / "COSTERES GATA".
     */
    static List<String> repartirEquilibrado(Graphics2D g, Font fuente, String[] palabras, int lineas) {

        int n = palabras.length;

        if (lineas == 1 || n < lineas) {
            return List.of(String.join(" ", palabras));
        }

        List<String> mejor = null;
        double mejorMaximo = Double.MAX_VALUE;
        double mejorSuma = Double.MAX_VALUE;

        // Primer corte en a; con tres líneas, segundo corte en b (con dos líneas solo hay un corte).
        for (int a = 1; a < n; a++) {

            int primeroB = lineas == 2 ? n : a + 1;
            int ultimoB = lineas == 2 ? n : n - 1;

            for (int b = primeroB; b <= ultimoB; b++) {

                List<String> candidata = new ArrayList<>();
                candidata.add(unir(palabras, 0, a));

                if (lineas == 2) {
                    candidata.add(unir(palabras, a, n));
                } else {
                    candidata.add(unir(palabras, a, b));
                    candidata.add(unir(palabras, b, n));
                }

                double maximo = 0;
                double suma = 0;

                for (String linea : candidata) {
                    double ancho = limites(g, fuente, linea).getWidth();
                    maximo = Math.max(maximo, ancho);
                    suma += ancho * ancho;
                }

                if (maximo < mejorMaximo - 0.01 || (Math.abs(maximo - mejorMaximo) <= 0.01 && suma < mejorSuma)) {
                    mejor = candidata;
                    mejorMaximo = maximo;
                    mejorSuma = suma;
                }
            }
        }

        return mejor == null ? List.of(String.join(" ", palabras)) : mejor;
    }

    private static String unir(String[] palabras, int desde, int hasta) {
        return String.join(" ", java.util.Arrays.copyOfRange(palabras, desde, hasta));
    }

    // ---- escudos, VS y nombres -------------------------------------------------------------------

    private void pintarEnfrentamiento(Graphics2D g, Medidas m, Datos d) {

        BufferedImage mutxamel = escudoClub;
        // El escudo del rival sin su margen transparente (como el del Mutxamel): ocupa su hueco entero.
        BufferedImage leido = d.rivalPropio() ? null : leerEscudo(d.escudoRival());
        BufferedImage rival = d.rivalPropio() ? escudoClub : leido == null ? null : ImagenesRedesRenderer.recortarTransparencia(leido);

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

        double f = m.escudoIzquierda().h() / m.escudoReferencia();

        for (double[] l0 : DIAGONALES) {
            double[] l = { l0[0] * f, l0[1] * f, l0[2] * f, l0[3] * f };
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

    private void pintarInferior(Graphics2D g, Medidas m, Formato formato, Datos d, int ancho) {

        boolean hayRacha = d.racha() != null && !d.racha().isEmpty();
        boolean hayIzquierda = hayRacha || d.hashtags();
        boolean hayQr = d.qr();

        if (!hayIzquierda && !hayQr) {
            return;
        }

        // La fila va centrada en vertical entre los nombres y los patrocinadores; lo que quede se centra en horizontal.
        Rect fila = cajaFilaInferior(formato, hayIzquierda, hayQr);

        double hueco = m.qr().x() - (m.racha().x() + m.racha().w());
        double total = (hayIzquierda ? m.racha().w() : 0) + (hayIzquierda && hayQr ? hueco : 0)
                + (hayQr ? m.qr().w() : 0);
        double x = ancho / 2.0 - total / 2;

        if (hayIzquierda) {
            pintarRachaYHashtags(g, new Rect(x, fila.y() + (fila.h() - m.racha().h()) / 2, m.racha().w(), m.racha().h()),
                    d, hayRacha);
            x += m.racha().w() + hueco;
        }

        if (hayQr) {
            pintarQr(g, new Rect(x, fila.y() + (fila.h() - m.qr().h()) / 2, m.qr().w(), m.qr().h()), d.textoQr());
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

    private void pintarPatrocinadores(Graphics2D g, Formato formato) {

        List<Patrocinador> lista = PatrocinadoresClub.paraCartel();

        for (Colocado colocado : bloquePatrocinadores(formato).logos()) {

            BufferedImage logo = logoRecortado(lista.get(colocado.indice()).recursoLogoCartel());
            Rect caja = colocado.caja();

            g.drawImage(escalar(logo, (int) Math.round(caja.w()), (int) Math.round(caja.h())),
                    (int) Math.round(caja.x()), (int) Math.round(caja.y()), null);
        }
    }

    private static final Map<String, BufferedImage> LOGOS_RECORTADOS = new ConcurrentHashMap<>();

    /** El logo sin su margen transparente (su proporcion es la del contenido visible). */
    static BufferedImage logoRecortado(String recurso) {
        return LOGOS_RECORTADOS.computeIfAbsent(recurso,
                r -> ImagenesRedesRenderer.recortarTransparencia(ImagenesRedesRenderer.imagen(r)));
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

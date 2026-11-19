package com.mikedev.mutxamelcf.serviceimpl;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Ajuste;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Rect;

/**
 * Dibuja en servidor (Java2D) el cartel "Próximo partido del Filial" en PNG (publicación 1080×1350 o historia
 * 1080×1920). Diseño y medidas: docs/diseno-cartel-filial. La foto de fondo va arriba a lo ancho con un degradado azul
 * que pinta el servidor (así cualquier foto que suba el club queda igual) y todo lo demás es texto blanco sobre el azul.
 * Reutiliza los helpers de dibujo del cartel del Primer Equipo. Sin nombres ni fotos de jugadores.
 */
@Component
public class CartelFilialRenderer {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** El azul del cartel (#004AAB). */
    static final Color AZUL = new Color(0x004AAB);

    /** El degradado de la foto: transparente hasta el 55 % de su altura y azul opaco desde el 66 %. */
    static final double DEGRADADO_DESDE = 0.55;
    static final double DEGRADADO_HASTA = 0.66;

    /** La foto ocupa siempre 1080×1350 arriba; en historia, por debajo, azul liso. */
    static final int ANCHO_FOTO = 1080;
    static final int ALTO_FOTO = 1350;

    /** Lo ancho que puede ser la línea de nombres. */
    static final double ANCHO_NOMBRES = 1000;

    /** Con menos de este tamaño (la parte del máximo), la línea de nombres se parte en dos. */
    static final double TAMANO_MINIMO_UNA_LINEA = 0.70;

    private static final Color BLANCO = Color.WHITE;
    private static final Color VERDE = new Color(0x2E9E4F);
    private static final Color GRIS = new Color(0x8A949B);
    private static final Color ROJO = new Color(0xD64545);

    // ---- medidas (docs/diseno-cartel-filial/medidas.md) ---------------------------------------------

    private static Rect r(double x, double y, double w, double h) {
        return new Rect(x, y, w, h);
    }

    record Medidas(Rect logo, Rect escudoIzquierda, Rect vs, Rect escudoDerecha, Rect nombres, Rect fecha, Rect hora,
            Rect campo, Rect hashtags, Rect racha, Rect qr, boolean columna) {
    }

    static final Medidas PUBLICACION = new Medidas(r(369, 58, 343, 120), r(203, 790, 190, 190), r(453, 790, 173, 190),
            r(687, 790, 190, 190), r(0, 1000, 1080, 74), r(0, 1086, 1080, 61), r(0, 1146, 1080, 61),
            r(0, 1207, 1080, 61), r(0, 1278, 1080, 34), r(26, 1240, 204, 70), r(907, 1186, 133, 134), false);

    static final Medidas HISTORIA = new Medidas(r(326, 80, 428, 150), r(125, 960, 240, 240), r(435, 965, 210, 230),
            r(715, 960, 240, 240), r(0, 1230, 1080, 78), r(0, 1320, 1080, 72), r(0, 1391, 1080, 72),
            r(0, 1463, 1080, 72), r(137, 1760, 622, 41), r(327, 1664, 244, 78), r(810, 1654, 133, 156), true);

    static Medidas medidas(Formato formato) {
        return formato == Formato.HISTORIA ? HISTORIA : PUBLICACION;
    }

    /** El hueco libre a cada lado del texto central en publicación (la racha y el QR están en las esquinas). */
    static final double MARGEN_ENTRE_TEXTO_Y_ESQUINAS = 16;

    // ---- datos -------------------------------------------------------------------------------------------

    /**
     * Todo lo que pinta el cartel, ya resuelto (textos en el idioma elegido y en mayúsculas).
     *
     * @param nombreMutxamel   "MUTXAMEL C.F. “B”"
     * @param nombreRival      en mayúsculas
     * @param escudoRival      PNG del rival o null (rivalPropio: se pinta el del Mutxamel)
     * @param logoCompeticion  recurso del logo o null
     * @param textoCompeticion texto en lugar del logo, o null
     * @param diaFecha         "DOMINGO 01/11/2026"
     * @param hora             "19:00H" (vacío si el partido no tiene hora)
     * @param campo            el campo en una sola línea ("CAMP NOU LOS OLMOS" en casa)
     * @param racha            G, E o P del más antiguo al más reciente; null o vacía = sin racha
     */
    public record Datos(boolean local, String nombreMutxamel, String nombreRival, byte[] escudoRival,
            boolean rivalPropio, String logoCompeticion, String textoCompeticion, String diaFecha, String hora,
            String campo, String textoRacha, List<String> racha, boolean hashtags, boolean qr, String textoQr) {
    }

    // ---- disposición (lo que se calcula antes de dibujar) ----------------------------------------------------

    /** Un texto ya colocado: su fuente, su centro y los límites visuales que ocupa. */
    record Texto(String texto, Font fuente, double cx, double cy, Rect visual) {
    }

    /**
     * Todo lo colocado. Los límites visuales sirven para dibujar y para comprobar que nada se solapa.
     *
     * @param nombres líneas de la línea de nombres (una, o dos si no cabía) con su fuente
     * @param racha   la caja de la racha (null si no sale)
     * @param qr      la caja del QR (null si no sale)
     */
    record Disposicion(Rect logo, Rect escudoIzquierda, Rect vs, Rect escudoDerecha, Ajuste nombres,
            Rect nombresVisual, Texto fecha, Texto hora, Texto campo, Texto hashtags, Rect racha, Rect qr) {
    }

    private final Font barlow;
    private final Font inter;
    private final Font interExtraNegrita;
    private final BufferedImage escudoClub;
    private static final Map<String, BufferedImage> LOGOS = new ConcurrentHashMap<>();

    public CartelFilialRenderer() {
        this.barlow = ImagenesRedesRenderer.fuente("BarlowCondensed-ExtraBoldItalic.ttf");
        this.inter = ImagenesRedesRenderer.fuente("Inter-Bold.otf");
        this.interExtraNegrita = ImagenesRedesRenderer.fuente("Inter-ExtraBold.otf");
        this.escudoClub = ImagenesRedesRenderer.recortarTransparencia(ImagenesRedesRenderer.imagen("/redes/escudo.png"));
    }

    /** El PNG del cartel. {@code fondo} es el JPG o PNG de la foto (el de la tabla o el de por defecto). */
    public byte[] renderizar(Formato formato, Datos datos, byte[] fondo) {

        int ancho = formato.ancho();
        int alto = formato.alto();
        Medidas m = medidas(formato);

        BufferedImage lienzo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();

        try {
            ImagenesRedesRenderer.configurar(g);

            pintarFondo(g, leerFondo(fondo), ancho, alto);

            Disposicion d = disponer(g, formato, datos);

            pintarLogo(g, d, datos);
            pintarEscudos(g, d, datos);
            pintarTextos(g, d);
            pintarOpcionales(g, m, d, datos);

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

    // ---- fondo ---------------------------------------------------------------------------------------------

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

    /**
     * Azul liso en todo el lienzo; la foto arriba a lo ancho (escalada para cubrir 1080×1350 y recortada al centro);
     * y encima el degradado del servidor: transparente hasta el 55 % de la altura de la foto y azul opaco desde el 66 %
     * (y por debajo de la foto, azul liso).
     */
    static void pintarFondo(Graphics2D g, BufferedImage foto, int ancho, int alto) {

        g.setColor(AZUL);
        g.fillRect(0, 0, ancho, alto);

        double escala = Math.max((double) ANCHO_FOTO / foto.getWidth(), (double) ALTO_FOTO / foto.getHeight());
        int anchoEscalado = (int) Math.max(ANCHO_FOTO, Math.ceil(foto.getWidth() * escala));
        int altoEscalado = (int) Math.max(ALTO_FOTO, Math.ceil(foto.getHeight() * escala));

        BufferedImage escalada = CartelPrimerEquipoRenderer.escalar(foto, anchoEscalado, altoEscalado);

        // La foto se recorta a su rectángulo (los lados y el alto sobrantes se descartan).
        java.awt.Shape anterior = g.getClip();
        g.setClip(0, 0, ANCHO_FOTO, ALTO_FOTO);
        g.drawImage(escalada, (ANCHO_FOTO - anchoEscalado) / 2, (ALTO_FOTO - altoEscalado) / 2, null);
        g.setClip(anterior);

        double desde = ALTO_FOTO * DEGRADADO_DESDE;
        double hasta = ALTO_FOTO * DEGRADADO_HASTA;

        g.setPaint(new GradientPaint(0, (float) desde, new Color(AZUL.getRed(), AZUL.getGreen(), AZUL.getBlue(), 0),
                0, (float) hasta, AZUL));
        g.fillRect(0, (int) Math.floor(desde), ancho, ALTO_FOTO - (int) Math.floor(desde));
        g.setPaint(null);
    }

    // ---- disposición ------------------------------------------------------------------------------------

    /** Coloca todo lo que se dibuja y mide lo que ocupa cada texto. */
    Disposicion disponer(Graphics2D g, Formato formato, Datos d) {

        Medidas m = medidas(formato);

        // Nombres: "MUTXAMEL C.F. “B” - RIVAL" (o al revés fuera), en una línea o, si no cabe, en dos.
        String local = d.local() ? d.nombreMutxamel() : d.nombreRival();
        String visitante = d.local() ? d.nombreRival() : d.nombreMutxamel();
        Ajuste nombres = ajustarNombres(g, barlow, local, visitante,
                m.nombres().h() * (m.columna() ? TAMANO_NOMBRES_HISTORIA : TAMANO_NOMBRES_PUBLICACION));
        Rect nombresVisual = visualDeLineas(g, nombres, m.nombres());

        // Fecha, hora y campo: Inter Bold del mismo tamaño base; el campo se reduce si no cabe.
        double base = m.fecha().h() * TAMANO_DATOS;
        double anchoCentral = anchoCentral(formato, m);

        Texto fecha = texto(g, d.diaFecha(), base, ANCHO_NOMBRES, m.fecha());
        Texto hora = texto(g, d.hora(), base, anchoCentral, m.hora());
        Texto campo = texto(g, d.campo(), base, anchoCentral, m.campo());

        boolean hayRacha = d.racha() != null && !d.racha().isEmpty();
        Rect racha = null;
        Rect qr = null;
        Texto hashtags = null;

        if (!m.columna()) {
            // Publicación: cada cosa en su sitio (racha abajo a la izquierda, QR a la derecha, hashtags bajo el campo).
            racha = hayRacha ? m.racha() : null;
            qr = d.qr() ? m.qr() : null;
            hashtags = d.hashtags()
                    ? texto(g, TextoPublicacionRedes.HASHTAGS, m.hashtags().h() * TAMANO_HASHTAGS, anchoCentral, m.hashtags())
                    : null;
        } else {
            // Historia: racha y hashtags en columna, con el QR a su derecha, y lo que quede centrado en el lienzo.
            double ancho = Math.max(d.hashtags() ? m.hashtags().w() : 0, hayRacha ? m.racha().w() : 0);
            double hueco = m.qr().x() - (m.hashtags().x() + m.hashtags().w());
            double total = ancho + (ancho > 0 && d.qr() ? hueco : 0) + (d.qr() ? m.qr().w() : 0);
            double x0 = formato.ancho() / 2.0 - total / 2;
            double centroColumna = x0 + ancho / 2;

            // La columna ocupa de la racha a los hashtags; con uno solo, ese se centra en la misma franja.
            double arriba = m.racha().y();
            double abajo = m.hashtags().y() + m.hashtags().h();
            double centroY = (arriba + abajo) / 2;

            if (hayRacha) {
                double y = d.hashtags() ? m.racha().y() : centroY - m.racha().h() / 2;
                racha = r(centroColumna - m.racha().w() / 2, y, m.racha().w(), m.racha().h());
            }

            if (d.hashtags()) {
                double y = hayRacha ? m.hashtags().y() : centroY - m.hashtags().h() / 2;
                hashtags = texto(g, TextoPublicacionRedes.HASHTAGS, m.hashtags().h() * TAMANO_HASHTAGS_HISTORIA, m.hashtags().w(),
                        r(centroColumna - m.hashtags().w() / 2, y, m.hashtags().w(), m.hashtags().h()));
            }

            if (d.qr()) {
                qr = r(x0 + ancho + (ancho > 0 ? hueco : 0), m.qr().y(), m.qr().w(), m.qr().h());
            }
        }

        return new Disposicion(m.logo(), m.escudoIzquierda(), m.vs(), m.escudoDerecha(), nombres, nombresVisual, fecha,
                hora, campo, hashtags, racha, qr);
    }

    /**
     * El ancho que puede tener el texto central. En publicación la racha (izquierda) y el QR (derecha) están en las
     * esquinas, a la altura de la hora, el campo y los hashtags: el texto no puede llegar a ellos.
     */
    private static double anchoCentral(Formato formato, Medidas m) {

        if (m.columna()) {
            return ANCHO_NOMBRES;
        }

        double centro = formato.ancho() / 2.0;
        double mitad = Math.min(centro - (m.racha().x() + m.racha().w()), m.qr().x() - centro)
                - MARGEN_ENTRE_TEXTO_Y_ESQUINAS;

        return mitad * 2;
    }

    /** Un texto centrado en su caja con la mayor letra (hasta {@code maximo}) con la que cabe en {@code anchoMax}. */
    private Texto texto(Graphics2D g, String texto, double maximo, double anchoMax, Rect caja) {

        Font fuente = CartelPrimerEquipoRenderer.ajustar(g, inter, (float) maximo, anchoMax, maximo * 2, texto.isEmpty() ? "-" : texto);
        Rectangle2D b = CartelPrimerEquipoRenderer.limites(g, fuente, texto);
        Rect visual = new Rect(caja.cx() - b.getWidth() / 2, caja.cy() - b.getHeight() / 2, b.getWidth(), b.getHeight());

        return new Texto(texto, fuente, caja.cx(), caja.cy(), visual);
    }

    /**
     * La línea de nombres en Barlow: la mayor letra (hasta {@code maximo}) con la que cabe en una línea de
     * {@link #ANCHO_NOMBRES}; si hace falta bajar de {@link #TAMANO_MINIMO_UNA_LINEA} del máximo, se parte por el guion
     * en dos líneas ("local" y "- visitante"), cada una con su tamaño máximo que quepa.
     */
    static Ajuste ajustarNombres(Graphics2D g, Font base, String local, String visitante, double maximo) {

        String completa = local + " - " + visitante;

        for (double tamano = maximo; tamano >= maximo * TAMANO_MINIMO_UNA_LINEA - 1e-9; tamano -= 0.5) {

            Font fuente = base.deriveFont((float) tamano);

            if (CartelPrimerEquipoRenderer.limites(g, fuente, completa).getWidth() <= ANCHO_NOMBRES) {
                return new Ajuste(fuente, List.of(completa), 1.0);
            }
        }

        List<String> lineas = List.of(local, "- " + visitante);

        for (double tamano = maximo * TAMANO_MINIMO_UNA_LINEA; tamano > 8; tamano -= 0.5) {

            Font fuente = base.deriveFont((float) tamano);
            boolean caben = lineas.stream()
                    .allMatch(l -> CartelPrimerEquipoRenderer.limites(g, fuente, l).getWidth() <= ANCHO_NOMBRES);

            if (caben) {
                return new Ajuste(fuente, lineas, 0.98);
            }
        }

        return new Ajuste(base.deriveFont(8f), lineas, 0.98);
    }

    /** El tamaño máximo de la letra de los nombres, como parte del alto de su caja (el de la referencia). */
    static final double TAMANO_NOMBRES_PUBLICACION = 0.94;
    static final double TAMANO_NOMBRES_HISTORIA = 1.0;

    /** El tamaño de la letra de la fecha, la hora y el campo, como parte del alto de su caja. */
    static final double TAMANO_DATOS = 0.725;

    /** El tamaño de la letra de los hashtags, como parte del alto de su caja. */
    static final double TAMANO_HASHTAGS = 0.82;
    static final double TAMANO_HASHTAGS_HISTORIA = 0.9;

    /** Los límites visuales (aproximados con el ancho de la línea más ancha y el alto del bloque) de unas líneas. */
    private static Rect visualDeLineas(Graphics2D g, Ajuste ajuste, Rect caja) {

        double ancho = ajuste.lineas().stream()
                .mapToDouble(l -> CartelPrimerEquipoRenderer.limites(g, ajuste.fuente(), l).getWidth()).max().orElse(0);
        double alto = ajuste.lineas().size() * ajuste.fuente().getSize2D() * ajuste.paso();

        return new Rect(caja.cx() - ancho / 2, caja.cy() - alto / 2, ancho, alto);
    }

    // ---- dibujo ----------------------------------------------------------------------------------------------

    private void pintarLogo(Graphics2D g, Disposicion d, Datos datos) {

        if (datos.logoCompeticion() != null) {
            BufferedImage logo = logo(datos.logoCompeticion());

            if (logo != null) {
                CartelPrimerEquipoRenderer.dibujarContenido(g, logo, d.logo(), 0);
                return;
            }
        }

        if (datos.textoCompeticion() != null && !datos.textoCompeticion().isBlank()) {
            g.setColor(BLANCO);
            Ajuste ajuste = CartelPrimerEquipoRenderer.ajustarLineas(g, barlow, datos.textoCompeticion(), 2,
                    d.logo().w(), d.logo().h(), d.logo().h() * 0.7, 1.0);
            CartelPrimerEquipoRenderer.lineas(g, ajuste, d.logo(), 0, true);
        }
    }

    private void pintarEscudos(Graphics2D g, Disposicion d, Datos datos) {

        BufferedImage leido = datos.rivalPropio() ? null : CartelPrimerEquipoRenderer.leerEscudo(datos.escudoRival());
        BufferedImage rival = datos.rivalPropio() ? escudoClub
                : leido == null ? null : ImagenesRedesRenderer.recortarTransparencia(leido);

        Rect cajaMutxamel = datos.local() ? d.escudoIzquierda() : d.escudoDerecha();
        Rect cajaRival = datos.local() ? d.escudoDerecha() : d.escudoIzquierda();

        CartelPrimerEquipoRenderer.dibujarEscudo(g, escudoClub, cajaMutxamel);

        g.setColor(BLANCO);

        if (rival != null) {
            CartelPrimerEquipoRenderer.dibujarEscudo(g, rival, cajaRival);
        } else {
            // Rival sin escudo: su nombre en grande en su hueco (hasta tres líneas).
            Ajuste grande = CartelPrimerEquipoRenderer.ajustarLineas(g, barlow, datos.nombreRival(), 3, cajaRival.w(),
                    cajaRival.h(), 90, 1.0);
            CartelPrimerEquipoRenderer.lineas(g, grande, cajaRival, 0, true);
        }

        // VS
        Font fuenteVs = CartelPrimerEquipoRenderer.ajustar(g, barlow, 600, d.vs().w(), d.vs().h(), "VS");
        CartelPrimerEquipoRenderer.centrarEnCaja(g, fuenteVs, "VS", d.vs());
    }

    private void pintarTextos(Graphics2D g, Disposicion d) {

        g.setColor(BLANCO);

        // Nombres
        Rect cajaNombres = new Rect(0, d.nombresVisual().cy() - d.nombresVisual().h() / 2, 1080,
                d.nombresVisual().h());
        CartelPrimerEquipoRenderer.lineas(g, d.nombres(), cajaNombres, 0, true);

        for (Texto t : new Texto[] { d.fecha(), d.hora(), d.campo() }) {
            CartelPrimerEquipoRenderer.centrarEnPunto(g, t.fuente(), t.texto(), t.cx(), t.cy());
        }
    }

    private void pintarOpcionales(Graphics2D g, Medidas m, Disposicion d, Datos datos) {

        g.setColor(BLANCO);

        if (d.hashtags() != null) {
            Texto t = d.hashtags();
            CartelPrimerEquipoRenderer.centrarEnPunto(g, t.fuente(), t.texto(), t.cx(), t.cy());
        }

        if (d.racha() != null) {
            pintarRacha(g, d.racha(), datos.textoRacha(), datos.racha());
        }

        if (d.qr() != null) {
            CartelPrimerEquipoRenderer.pintarQr(g, d.qr(), datos.textoQr(), inter);
        }
    }

    /** "ÚLTIMOS 5" y debajo los círculos G, E o P (los que haya, centrados), con los colores de la app. */
    private void pintarRacha(Graphics2D g, Rect caja, String rotulo, List<String> racha) {

        double paso = caja.w() / 5;
        double diametro = paso * 0.92;
        double n = racha.size();

        Font fuenteRotulo = inter.deriveFont(Map.of(TextAttribute.SIZE, (float) (caja.h() * 0.27),
                TextAttribute.TRACKING, 0.14f));
        g.setColor(BLANCO);
        CartelPrimerEquipoRenderer.centrarEnPunto(g, fuenteRotulo, rotulo, caja.cx(), caja.y() + caja.h() * 0.17);

        double cy = caja.y() + caja.h() - diametro / 2;
        double inicio = caja.cx() - (n - 1) * paso / 2;
        Font letra = interExtraNegrita.deriveFont((float) (diametro * 0.46));

        for (int i = 0; i < racha.size(); i++) {

            String resultado = racha.get(i);
            double cx = inicio + i * paso;

            g.setColor(colorResultado(resultado));
            g.fill(new Ellipse2D.Double(cx - diametro / 2, cy - diametro / 2, diametro, diametro));
            g.setColor(BLANCO);
            CartelPrimerEquipoRenderer.centrarEnPunto(g, letra, resultado, cx, cy);
        }
    }

    static Color colorResultado(String resultado) {
        return switch (resultado) {
            case "G" -> VERDE;
            case "P" -> ROJO;
            default -> GRIS;
        };
    }

    private static BufferedImage logo(String recurso) {
        return LOGOS.computeIfAbsent(recurso,
                r -> ImagenesRedesRenderer.recortarTransparencia(ImagenesRedesRenderer.imagen(r)));
    }
}

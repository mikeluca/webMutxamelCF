package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Datos;
import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Disposicion;
import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Medidas;
import com.mikedev.mutxamelcf.serviceimpl.CartelFilialRenderer.Texto;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Ajuste;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Rect;

/** La línea de nombres, la disposición de los textos y los opcionales del cartel del Filial. */
class CartelFilialRendererTest {

    private static final String MUTXAMEL = "MUTXAMEL C.F. “B”";

    private final CartelFilialRenderer renderer = new CartelFilialRenderer();

    private static Graphics2D grafico() {

        Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
        ImagenesRedesRenderer.configurar(g);

        return g;
    }

    private static Font barlow() {
        return ImagenesRedesRenderer.fuente("BarlowCondensed-ExtraBoldItalic.ttf");
    }

    private static double ancho(Graphics2D g, Ajuste ajuste, int linea) {
        return CartelPrimerEquipoRenderer.limites(g, ajuste.fuente(), ajuste.lineas().get(linea)).getWidth();
    }

    private static Datos datos(boolean local, String rival, String campo, boolean racha, boolean qr, boolean hashtags) {

        return new Datos(local, MUTXAMEL, rival, null, false, "/redes/logos/competiciones/segona-ffcv.png", null,
                "DOMINGO 22/11/2026", "19:00H", campo, "ÚLTIMOS 5", racha ? List.of("G", "G", "P", "E", "G") : List.of(),
                hashtags, qr, "Descarga la app");
    }

    // ---- la línea de nombres --------------------------------------------------------------------------------

    @Test
    void unNombreCortoCabeEnUnaLineaConElTamanoMaximo() {

        Graphics2D g = grafico();
        Ajuste ajuste = CartelFilialRenderer.ajustarNombres(g, barlow(), MUTXAMEL, "PEGO C.F.", 70);

        assertThat(ajuste.lineas()).containsExactly(MUTXAMEL + " - PEGO C.F.");
        assertThat(ajuste.fuente().getSize2D()).isEqualTo(70f);
        assertThat(ancho(g, ajuste, 0)).isLessThanOrEqualTo(1000);
    }

    @Test
    void fueraElRivalVaPrimeroYElMutxamelDespues() {

        Graphics2D g = grafico();
        Ajuste ajuste = CartelFilialRenderer.ajustarNombres(g, barlow(), "PEGO C.F.", MUTXAMEL, 70);

        assertThat(ajuste.lineas()).containsExactly("PEGO C.F. - " + MUTXAMEL);
    }

    @Test
    void segunCreceElNombreSeReduceLaLetraYAlFinalSePartePorElGuion() {

        Graphics2D g = grafico();
        double maximo = 70;
        boolean vistaUnaLineaAlMaximo = false;
        boolean vistaUnaLineaReducida = false;
        boolean vistaDosLineas = false;
        String rival = "ALICANTE";

        for (int i = 0; i < 12; i++) {

            Ajuste ajuste = CartelFilialRenderer.ajustarNombres(g, barlow(), MUTXAMEL, rival, maximo);

            // Siempre cabe: ninguna línea pasa de 1000 px
            for (int linea = 0; linea < ajuste.lineas().size(); linea++) {
                assertThat(ancho(g, ajuste, linea)).as(rival).isLessThanOrEqualTo(1000);
            }

            if (ajuste.lineas().size() == 1) {
                if (ajuste.fuente().getSize2D() == maximo) {
                    vistaUnaLineaAlMaximo = true;
                } else {
                    vistaUnaLineaReducida = true;
                    // Nunca baja del 70 % mientras sea una sola línea
                    assertThat(ajuste.fuente().getSize2D()).isGreaterThanOrEqualTo((float) (maximo * 0.70 - 0.5));
                }
            } else {
                vistaDosLineas = true;
                // El local en la primera línea y "- visitante" en la segunda
                assertThat(ajuste.lineas()).containsExactly(MUTXAMEL, "- " + rival);
            }

            rival += " ALICANTE";
        }

        assertThat(vistaUnaLineaAlMaximo).isTrue();
        assertThat(vistaUnaLineaReducida).isTrue();
        assertThat(vistaDosLineas).isTrue();
    }

    @Test
    void unaVezPartidaFueraElRivalEsLaPrimeraLineaYElMutxamelLaSegunda() {

        Graphics2D g = grafico();
        String rival = "CLUB DEPORTIVO ALICANTE ATLETICO BALOMPIE DE LA COMUNIDAD";
        Ajuste ajuste = CartelFilialRenderer.ajustarNombres(g, barlow(), rival, MUTXAMEL, 70);

        assertThat(ajuste.lineas()).containsExactly(rival, "- " + MUTXAMEL);
    }

    // ---- los textos del centro y los opcionales no se solapan ---------------------------------------------------

    private static boolean seCruzan(Rect a, Rect b) {
        return a != null && b != null && a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h()
                && b.y() < a.y() + a.h();
    }

    private static List<Rect> textosCentrales(Disposicion d) {

        List<Rect> cajas = new ArrayList<>();
        cajas.add(d.nombresVisual());

        for (Texto t : new Texto[] { d.fecha(), d.hora(), d.campo(), d.hashtags() }) {
            if (t != null) {
                cajas.add(t.visual());
            }
        }

        return cajas;
    }

    private static final String[] CAMPOS = { "CAMP NOU LOS OLMOS", "LES COSTERES GATA", "PEDRO FERRÁNDIZ GRAN ALACANT",
            "CIUDAD DEPORTIVA MUNICIPAL DE SANT JOAN D'ALACANT Y VILLAJOYOSA" };

    @Test
    void enPublicacionLaRachaYElQrNoSeSolapanConElTextoCentralConElCampoMasLargo() {

        Graphics2D g = grafico();

        for (String campo : CAMPOS) {
            for (boolean racha : new boolean[] { true, false }) {
                for (boolean qr : new boolean[] { true, false }) {
                    for (boolean hashtags : new boolean[] { true, false }) {

                        Disposicion d = renderer.disponer(g, Formato.PUBLICACION,
                                datos(true, "PEGO C.F.", campo, racha, qr, hashtags));
                        String caso = campo + " racha=" + racha + " qr=" + qr + " hashtags=" + hashtags;

                        for (Rect texto : textosCentrales(d)) {
                            assertThat(seCruzan(texto, d.racha())).as(caso + " racha").isFalse();
                            assertThat(seCruzan(texto, d.qr())).as(caso + " qr").isFalse();
                        }

                        // Y los textos del centro no se pisan entre sí
                        List<Rect> cajas = textosCentrales(d);

                        for (int i = 0; i < cajas.size(); i++) {
                            for (int j = i + 1; j < cajas.size(); j++) {
                                assertThat(seCruzan(cajas.get(i), cajas.get(j))).as(caso + " " + i + "/" + j).isFalse();
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void elCampoLargoSeReduceParaNoLlegarALaRachaNiAlQr() {

        Graphics2D g = grafico();
        Disposicion corto = renderer.disponer(g, Formato.PUBLICACION, datos(true, "PEGO C.F.", CAMPOS[0], true, true, true));
        Disposicion largo = renderer.disponer(g, Formato.PUBLICACION, datos(true, "PEGO C.F.", CAMPOS[3], true, true, true));

        assertThat(largo.campo().fuente().getSize2D()).isLessThan(corto.campo().fuente().getSize2D());
        // Entre la racha (hasta x=230) y el QR (desde x=907), centrado
        assertThat(largo.campo().visual().x()).isGreaterThan(230);
        assertThat(largo.campo().visual().x() + largo.campo().visual().w()).isLessThan(907);
        assertThat(largo.campo().visual().x() + largo.campo().visual().w() / 2).isCloseTo(540, within(0.5));
    }

    @Test
    void enHistoriaNingunElementoSeSolapaConLosOpcionales() {

        Graphics2D g = grafico();

        for (String campo : CAMPOS) {
            for (boolean racha : new boolean[] { true, false }) {
                for (boolean qr : new boolean[] { true, false }) {
                    for (boolean hashtags : new boolean[] { true, false }) {

                        Disposicion d = renderer.disponer(g, Formato.HISTORIA,
                                datos(false, "PEGO C.F.", campo, racha, qr, hashtags));
                        String caso = campo + " racha=" + racha + " qr=" + qr + " hashtags=" + hashtags;
                        List<Rect> cajas = textosCentrales(d);

                        if (d.racha() != null) {
                            cajas.add(d.racha());
                        }

                        if (d.qr() != null) {
                            cajas.add(d.qr());
                        }

                        for (int i = 0; i < cajas.size(); i++) {
                            for (int j = i + 1; j < cajas.size(); j++) {
                                assertThat(seCruzan(cajas.get(i), cajas.get(j))).as(caso + " " + i + "/" + j).isFalse();
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- opcionales: sin hueco -----------------------------------------------------------------------------------

    @Test
    void enHistoriaConTodoLaColumnaYElQrOcupanElMismoAnchoQueLaReferenciaYEstanCentrados() {

        Disposicion d = renderer.disponer(grafico(), Formato.HISTORIA, datos(true, "PEGO C.F.", CAMPOS[0], true, true, true));

        // Referencia: columna de x=137 a 759 y QR de 810 a 943: de 137 a 943, centrado en 540
        double izquierda = d.hashtags().visual().x() - 0;
        double derecha = d.qr().x() + d.qr().w();

        assertThat(d.qr().x()).isCloseTo(810, within(1.0));
        assertThat(derecha).isCloseTo(943, within(1.0));
        assertThat((137 + 943) / 2.0).isEqualTo(540);
        assertThat(izquierda).isLessThan(d.qr().x());
        assertThat(d.racha().cx()).isCloseTo(448, within(1.0));
    }

    @Test
    void enHistoriaSinQrLaColumnaSeCentraEnElLienzo() {

        Disposicion d = renderer.disponer(grafico(), Formato.HISTORIA, datos(true, "PEGO C.F.", CAMPOS[0], true, false, true));

        assertThat(d.qr()).isNull();
        assertThat(d.racha().cx()).isCloseTo(540, within(0.5));
        assertThat(d.hashtags().cx()).isCloseTo(540, within(0.5));
    }

    @Test
    void enHistoriaSoloElQrOSoloLaRachaOSoloLosHashtagsSeCentran() {

        Graphics2D g = grafico();

        Disposicion soloQr = renderer.disponer(g, Formato.HISTORIA, datos(true, "X", CAMPOS[0], false, true, false));
        assertThat(soloQr.qr().cx()).isCloseTo(540, within(0.5));
        assertThat(soloQr.racha()).isNull();
        assertThat(soloQr.hashtags()).isNull();

        Disposicion soloRacha = renderer.disponer(g, Formato.HISTORIA, datos(true, "X", CAMPOS[0], true, false, false));
        assertThat(soloRacha.racha().cx()).isCloseTo(540, within(0.5));
        // Sin hashtags la racha se centra en la franja de la columna (de 1664 a 1801)
        assertThat(soloRacha.racha().cy()).isCloseTo((1664 + 1801) / 2.0, within(0.5));

        Disposicion soloHashtags = renderer.disponer(g, Formato.HISTORIA, datos(true, "X", CAMPOS[0], false, false, true));
        assertThat(soloHashtags.hashtags().cx()).isCloseTo(540, within(0.5));
        assertThat(soloHashtags.hashtags().cy()).isCloseTo((1664 + 1801) / 2.0, within(0.5));
    }

    @Test
    void enPublicacionCadaOpcionalVaEnSuSitioYSinOpcionalesNoHayNada() {

        Graphics2D g = grafico();
        Medidas m = CartelFilialRenderer.PUBLICACION;

        Disposicion todo = renderer.disponer(g, Formato.PUBLICACION, datos(true, "X", CAMPOS[0], true, true, true));

        assertThat(todo.racha()).isEqualTo(m.racha());
        assertThat(todo.qr()).isEqualTo(m.qr());
        assertThat(todo.hashtags().cy()).isCloseTo(m.hashtags().cy(), within(0.5));

        Disposicion nada = renderer.disponer(g, Formato.PUBLICACION, datos(true, "X", CAMPOS[0], false, false, false));

        assertThat(nada.racha()).isNull();
        assertThat(nada.qr()).isNull();
        assertThat(nada.hashtags()).isNull();
    }

    @Test
    void sinHoraNoHayTextoDeHora() {

        Datos sinHora = new Datos(true, MUTXAMEL, "X", null, false, null, null, "DOMINGO 22/11/2026", "", CAMPOS[0],
                "ÚLTIMOS 5", List.of(), false, false, "Descarga la app");

        Disposicion d = renderer.disponer(grafico(), Formato.PUBLICACION, sinHora);

        assertThat(d.hora().visual().w()).isZero();
    }

    // ---- dibujo ---------------------------------------------------------------------------------------------------

    private static BufferedImage leer(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private byte[] fondoLiso() throws Exception {

        BufferedImage imagen = new BufferedImage(1079, 1350, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);

        return salida.toByteArray();
    }

    private static int claros(BufferedImage imagen, Rect caja) {

        int total = 0;

        for (int y = (int) caja.y(); y < caja.y() + caja.h(); y++) {
            for (int x = (int) caja.x(); x < caja.x() + caja.w(); x++) {
                int rgb = imagen.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) > 220 && ((rgb >> 8) & 0xFF) > 220 && (rgb & 0xFF) > 220) {
                    total++;
                }
            }
        }

        return total;
    }

    @Test
    void unRivalSinEscudoPintaSuNombreEnGrandeEnSuHuecoYElDelMutxamelSigueSuEscudo() throws Exception {

        Datos sinEscudo = datos(true, "ALICANTE COTY FC", CAMPOS[0], false, false, false);
        BufferedImage imagen = leer(renderer.renderizar(Formato.PUBLICACION, sinEscudo, fondoLiso()));

        // Hueco del rival (derecha): su nombre grande en blanco
        assertThat(claros(imagen, CartelFilialRenderer.PUBLICACION.escudoDerecha())).isGreaterThan(1500);
        // El escudo del Mutxamel está a la izquierda
        assertThat(claros(imagen, CartelFilialRenderer.PUBLICACION.escudoIzquierda())).isGreaterThan(5000);
    }

    @Test
    void fueraLosLadosSeIntercambian() throws Exception {

        Datos fuera = datos(false, "ALICANTE COTY FC", CAMPOS[0], false, false, false);
        BufferedImage imagen = leer(renderer.renderizar(Formato.PUBLICACION, fuera, fondoLiso()));

        // El nombre del rival (sin escudo) está ahora a la izquierda
        assertThat(claros(imagen, CartelFilialRenderer.PUBLICACION.escudoIzquierda())).isGreaterThan(1500);
        assertThat(claros(imagen, CartelFilialRenderer.PUBLICACION.escudoDerecha())).isGreaterThan(5000);
    }

    @Test
    void elDegradadoEmpiezaAlCincuentaYCincoPorCientoYEsOpacoAlSesentaYSeisPorCiento() {

        assertThat(CartelFilialRenderer.DEGRADADO_DESDE).isEqualTo(0.55);
        assertThat(CartelFilialRenderer.DEGRADADO_HASTA).isEqualTo(0.66);
        assertThat(CartelFilialRenderer.AZUL.getRGB() & 0xFFFFFF).isEqualTo(0x004AAB);
        assertThat(CartelFilialRenderer.ALTO_FOTO).isEqualTo(1350);
    }

    @Test
    void lasMedidasSonLasDeMedidasMd() {

        Medidas pub = CartelFilialRenderer.PUBLICACION;
        Medidas his = CartelFilialRenderer.HISTORIA;

        assertThat(pub.logo()).isEqualTo(new Rect(369, 58, 343, 120));
        assertThat(pub.escudoIzquierda()).isEqualTo(new Rect(203, 790, 190, 190));
        assertThat(pub.vs()).isEqualTo(new Rect(453, 790, 173, 190));
        assertThat(pub.escudoDerecha()).isEqualTo(new Rect(687, 790, 190, 190));
        assertThat(pub.qr()).isEqualTo(new Rect(907, 1186, 133, 134));
        assertThat(his.logo()).isEqualTo(new Rect(326, 80, 428, 150));
        assertThat(his.escudoIzquierda()).isEqualTo(new Rect(125, 960, 240, 240));
        assertThat(his.hashtags()).isEqualTo(new Rect(137, 1760, 622, 41));
        assertThat(his.qr()).isEqualTo(new Rect(810, 1654, 133, 156));
    }
}

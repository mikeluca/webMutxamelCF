package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.model.PatrocinadoresClub;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Bloque;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Formato;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Medidas;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Rect;
import com.mikedev.mutxamelcf.serviceimpl.CartelPrimerEquipoRenderer.Tamano;

/** El tamano de cada logo de patrocinador, su reparto en filas y que ningun bloque del cartel se solape. */
class CartelPatrocinadoresTamanoTest {

    private static Tamano tamano(double aspecto, Formato formato) {

        var p = CartelPrimerEquipoRenderer.medidas(formato).patrocinio();

        return CartelPrimerEquipoRenderer.tamanoLogo(aspecto, p.k(), p.hmax(), p.wmax());
    }

    // ---- tamano de un logo -----------------------------------------------------------------------------------

    @Test
    void losParametrosSonLosDelEncargo() {

        var pub = CartelPrimerEquipoRenderer.PUBLICACION.patrocinio();
        var his = CartelPrimerEquipoRenderer.HISTORIA.patrocinio();

        assertThat(List.of(pub.k(), pub.hmax(), pub.wmax(), pub.huecoH(), pub.huecoV(), pub.margenInferior()))
                .containsExactly(150d, 112d, 300d, 40d, 18d, 26d);
        assertThat(List.of(his.k(), his.hmax(), his.wmax(), his.huecoH(), his.huecoV(), his.margenInferior()))
                .containsExactly(200d, 150d, 330d, 50d, 34d, 50d);
    }

    @Test
    void unLogoCuadradoTopaConElAltoMaximo() {

        // K*raiz(1) = 150 de alto, pero HMAX es 112
        assertThat(tamano(1, Formato.PUBLICACION)).isEqualTo(new Tamano(112, 112));
        // En historia K = 200 y HMAX = 150
        assertThat(tamano(1, Formato.HISTORIA)).isEqualTo(new Tamano(150, 150));
    }

    @Test
    void unLogoMuyAlargadoComoLaBodegaTopaConElAnchoMaximo() {

        // Proporcion 5: ancho 150*raiz(5)=335 > 300 -> ancho 300 y alto 300/5
        Tamano pub = tamano(5, Formato.PUBLICACION);

        assertThat(pub.ancho()).isEqualTo(300);
        assertThat(pub.alto()).isCloseTo(60, within(1e-9));

        // Historia: 200*raiz(5)=447 > 330 -> ancho 330 y alto 66
        Tamano his = tamano(5, Formato.HISTORIA);

        assertThat(his.ancho()).isEqualTo(330);
        assertThat(his.alto()).isCloseTo(66, within(1e-9));
    }

    @Test
    void unLogoAltoTopaConElAltoMaximoYSuAnchoSaleDeLaProporcion() {

        // Proporcion 0,5: alto 150/raiz(0,5)=212 > 112 -> alto 112 y ancho 112*0,5
        Tamano pub = tamano(0.5, Formato.PUBLICACION);

        assertThat(pub.alto()).isEqualTo(112);
        assertThat(pub.ancho()).isCloseTo(56, within(1e-9));

        Tamano his = tamano(0.5, Formato.HISTORIA);

        assertThat(his.alto()).isEqualTo(150);
        assertThat(his.ancho()).isCloseTo(75, within(1e-9));
    }

    @Test
    void sinTopesIgualaElPesoVisualAreaConstante() {

        // Proporcion 3 en publicacion: 150*raiz(3) x 150/raiz(3) = 259,8 x 86,6 (area K^2 = 22500)
        Tamano t = tamano(3, Formato.PUBLICACION);

        assertThat(t.ancho()).isCloseTo(150 * Math.sqrt(3), within(1e-9));
        assertThat(t.alto()).isCloseTo(150 / Math.sqrt(3), within(1e-9));
        assertThat(t.ancho() * t.alto()).isCloseTo(150 * 150, within(1e-6));

        // Ni el alto ni el ancho superan nunca sus topes y la proporcion se conserva
        for (double a : new double[] { 0.2, 0.5, 1, 2, 3, 5, 8, 12 }) {
            for (Formato f : Formato.values()) {
                var p = CartelPrimerEquipoRenderer.medidas(f).patrocinio();
                Tamano x = tamano(a, f);

                assertThat(x.alto()).isLessThanOrEqualTo(p.hmax() + 1e-9);
                assertThat(x.ancho()).isLessThanOrEqualTo(p.wmax() + 1e-9);
                assertThat(x.ancho() / x.alto()).isCloseTo(a, within(1e-6));
            }
        }
    }

    // ---- reparto en filas -------------------------------------------------------------------------------------

    @Test
    void conLosSieteActualesSalenTresMasCuatroEnPublicacion() {

        Bloque bloque = CartelPrimerEquipoRenderer.bloquePatrocinadores(Formato.PUBLICACION);

        assertThat(PatrocinadoresClub.paraCartel()).hasSize(7);
        assertThat(bloque.logosPorFila()).containsExactly(3, 4);
    }

    @Test
    void conLosSieteActualesSalenDosTresDosEnHistoria() {

        Bloque bloque = CartelPrimerEquipoRenderer.bloquePatrocinadores(Formato.HISTORIA);

        assertThat(bloque.logosPorFila()).containsExactly(2, 3, 2);
    }

    @Test
    void losLogosSeLlenanEnSuOrdenCartel() {

        for (Formato formato : Formato.values()) {

            Bloque bloque = CartelPrimerEquipoRenderer.bloquePatrocinadores(formato);

            for (int i = 0; i < bloque.logos().size(); i++) {
                assertThat(bloque.logos().get(i).indice()).isEqualTo(i);
            }
        }
    }

    @Test
    void cadaFilaMideComoMuchoElAnchoUtilYEstaCentradaConSuSeparacion() {

        for (Formato formato : Formato.values()) {

            var p = CartelPrimerEquipoRenderer.medidas(formato).patrocinio();
            Bloque bloque = CartelPrimerEquipoRenderer.bloquePatrocinadores(formato);
            int indice = 0;

            for (int tamanoFila : bloque.logosPorFila()) {

                var fila = bloque.logos().subList(indice, indice + tamanoFila);
                double izquierda = fila.get(0).caja().x();
                double derecha = fila.get(fila.size() - 1).caja().x() + fila.get(fila.size() - 1).caja().w();

                assertThat(derecha - izquierda).as(formato + " fila").isLessThanOrEqualTo(1000 + 1e-9);
                // Centrada en el lienzo
                assertThat((izquierda + derecha) / 2).isCloseTo(540, within(1e-6));

                // Separacion horizontal entre logos seguidos
                for (int i = 1; i < fila.size(); i++) {
                    double hueco = fila.get(i).caja().x() - (fila.get(i - 1).caja().x() + fila.get(i - 1).caja().w());

                    assertThat(hueco).isCloseTo(p.huecoH(), within(1e-6));
                }

                // Centrados en vertical entre ellos: todos comparten el centro de la fila
                double centro = fila.get(0).caja().cy();

                for (var colocado : fila) {
                    assertThat(colocado.caja().cy()).isCloseTo(centro, within(1e-6));
                }

                indice += tamanoFila;
            }
        }
    }

    @Test
    void laSeparacionVerticalEntreFilasYElAnclajeAbajo() {

        for (Formato formato : Formato.values()) {

            var p = CartelPrimerEquipoRenderer.medidas(formato).patrocinio();
            Bloque bloque = CartelPrimerEquipoRenderer.bloquePatrocinadores(formato);

            // El borde inferior del bloque queda a margenInferior del final (26 y 50)
            assertThat(bloque.caja().y() + bloque.caja().h()).isCloseTo(formato.alto() - p.margenInferior(),
                    within(1e-6));

            // Entre el punto mas bajo de una fila y el mas alto de la siguiente hay huecoV
            int indice = 0;
            double finAnterior = Double.NaN;

            for (int tamanoFila : bloque.logosPorFila()) {

                var fila = bloque.logos().subList(indice, indice + tamanoFila);
                double abajo = fila.stream().mapToDouble(c -> c.caja().y() + c.caja().h()).max().orElseThrow();
                double alturaFila = fila.stream().mapToDouble(c -> c.caja().h()).max().orElseThrow();
                double arribaFila = fila.get(0).caja().cy() - alturaFila / 2;

                if (!Double.isNaN(finAnterior)) {
                    assertThat(arribaFila - finAnterior).as(formato + " entre filas").isCloseTo(p.huecoV(),
                            within(1e-6));
                }

                finAnterior = fila.get(0).caja().cy() + alturaFila / 2;
                indice += tamanoFila;
                assertThat(abajo).isLessThanOrEqualTo(formato.alto() - p.margenInferior() + 1e-6);
            }
        }
    }

    @Test
    void conPocosLogosUnaSolaFilaYSinLogosNadaDeBloque() {

        var p = CartelPrimerEquipoRenderer.PUBLICACION.patrocinio();

        Bloque dos = CartelPrimerEquipoRenderer.colocarPatrocinadores(List.of(3.0, 1.0), p, 1080, 1350);
        Bloque ninguno = CartelPrimerEquipoRenderer.colocarPatrocinadores(List.of(), p, 1080, 1350);

        assertThat(dos.logosPorFila()).containsExactly(2);
        assertThat(ninguno.logos()).isEmpty();
        assertThat(ninguno.logosPorFila()).isEmpty();
    }

    @Test
    void unaFilaQueNoCabeBajaElSiguienteLogoALaFilaDeAbajo() {

        var p = CartelPrimerEquipoRenderer.PUBLICACION.patrocinio();

        // Cuatro logos muy alargados (300 de ancho): caben tres con huecos de 40 y el cuarto baja
        Bloque bloque = CartelPrimerEquipoRenderer.colocarPatrocinadores(List.of(9.0, 9.0, 9.0, 9.0), p, 1080, 1350);

        assertThat(bloque.logosPorFila()).containsExactly(3, 1);
    }

    // ---- sin solapes --------------------------------------------------------------------------------------------

    @Test
    void ningunBloqueSeSolapaEnLosDosFormatosConYSinOpcionales() {

        double hueco = CartelPrimerEquipoRenderer.HUECO_MINIMO_BLOQUES;

        for (Formato formato : Formato.values()) {

            Medidas m = CartelPrimerEquipoRenderer.medidas(formato);
            Rect patrocinadores = CartelPrimerEquipoRenderer.bloquePatrocinadores(formato).caja();
            double finNombres = m.nombreIzquierda().y() + m.nombreIzquierda().h();
            double finEscudos = m.escudoIzquierda().y() + m.escudoIzquierda().h();

            // Los escudos, los nombres y los patrocinadores van uno debajo de otro con su hueco
            assertThat(m.escudoIzquierda().y()).isGreaterThan(m.campo().y() + m.campo().h());
            assertThat(finEscudos + hueco).as(formato + " escudos/nombres").isLessThanOrEqualTo(m.nombreIzquierda().y());
            assertThat(finNombres + hueco).as(formato + " nombres/patrocinadores")
                    .isLessThanOrEqualTo(patrocinadores.y());

            for (boolean izquierda : new boolean[] { true, false }) {
                for (boolean qr : new boolean[] { true, false }) {

                    Rect fila = CartelPrimerEquipoRenderer.cajaFilaInferior(formato, izquierda, qr);
                    String caso = formato + " racha/hashtags=" + izquierda + " qr=" + qr;

                    // La fila de racha y QR no toca ni los nombres de arriba ni los patrocinadores de abajo
                    assertThat(fila.y() - finNombres).as(caso + " nombres").isGreaterThanOrEqualTo(hueco);
                    assertThat(patrocinadores.y() - (fila.y() + fila.h())).as(caso + " patrocinadores")
                            .isGreaterThanOrEqualTo(hueco);
                    assertThat(fila.y() + fila.h()).isLessThan(formato.alto());
                }
            }

            // Los patrocinadores no se salen del ancho util ni del lienzo
            assertThat(patrocinadores.x()).isGreaterThanOrEqualTo(40 - 1e-9);
            assertThat(patrocinadores.x() + patrocinadores.w()).isLessThanOrEqualTo(1040 + 1e-9);
            assertThat(patrocinadores.y() + patrocinadores.h()).isLessThan(formato.alto());
        }
    }

    @Test
    void losEscudosSonMasPequenosParaHacerSitio() {

        assertThat(CartelPrimerEquipoRenderer.PUBLICACION.escudoIzquierda().w()).isEqualTo(262);
        assertThat(CartelPrimerEquipoRenderer.PUBLICACION.escudoDerecha().h()).isEqualTo(262);
        assertThat(CartelPrimerEquipoRenderer.HISTORIA.escudoIzquierda().w()).isEqualTo(310);
        assertThat(CartelPrimerEquipoRenderer.HISTORIA.escudoDerecha().h()).isEqualTo(310);
    }
}

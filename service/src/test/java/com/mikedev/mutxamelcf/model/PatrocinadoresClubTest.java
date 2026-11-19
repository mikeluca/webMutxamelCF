package com.mikedev.mutxamelcf.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PatrocinadoresClubTest {

    @Test
    void cadaLogoDeCartelExisteEnResources() {

        for (Patrocinador p : PatrocinadoresClub.LISTA) {

            if (p.logoCartel() != null) {
                assertThat(new ClassPathResource(p.recursoLogoCartel().substring(1)).exists())
                        .as(p.nombre() + " -> " + p.recursoLogoCartel()).isTrue();
            }
        }
    }

    @Test
    void mercurySoloSaleEnCarteles() {

        Patrocinador mercury = PatrocinadoresClub.LISTA.stream().filter(p -> p.nombre().equals("Mercury")).findFirst()
                .orElseThrow();

        assertThat(mercury.enWeb()).isFalse();
        assertThat(mercury.url()).isNull();
        assertThat(mercury.imagen()).isNull();
        assertThat(mercury.imagenUrl()).isNull();
        assertThat(mercury.logoCartel()).isEqualTo("mercury.png");
        assertThat(PatrocinadoresClub.paraWeb()).extracting(Patrocinador::nombre).doesNotContain("Mercury");
        assertThat(PatrocinadoresClub.paraCartel()).extracting(Patrocinador::nombre).contains("Mercury");
    }

    @Test
    void todosLosDemasSalenEnLaWebConSuImagen() {

        assertThat(PatrocinadoresClub.paraWeb()).hasSize(7).allSatisfy(p -> {
            assertThat(p.enWeb()).isTrue();
            assertThat(p.imagen()).isNotNull();
        });
    }

    @Test
    void elAyuntamientoEsElDestacadoDeArribaYNoEstaEnElBloqueDeAbajo() {

        Patrocinador destacado = PatrocinadoresClub.destacadoCartel();

        assertThat(destacado.nombre()).isEqualTo("Ayto Mutxamel");
        assertThat(destacado.logoCartel()).isEqualTo("ayuntamiento-blanco.png");
        assertThat(destacado.recursoLogoCartel()).isEqualTo("/redes/logos/ayuntamiento-blanco.png");
        assertThat(PatrocinadoresClub.paraCartel()).extracting(Patrocinador::nombre).doesNotContain("Ayto Mutxamel");
    }

    @Test
    void elBloqueDeAbajoTieneElOrdenDelCartelIndependienteDelDeLaWeb() {

        assertThat(PatrocinadoresClub.paraCartel()).extracting(Patrocinador::nombre).containsExactly(
                "La bodega de Elias", "Frutas y verduras Juanete", "Mercury", "Unlimited Developments", "Aresala",
                "AA Energy", "Dental Company Mutxamel");
        assertThat(PatrocinadoresClub.paraWeb()).extracting(Patrocinador::nombre).containsExactly(
                "Frutas y verduras Juanete", "Unlimited Developments", "Ayto Mutxamel", "La bodega de Elias",
                "Aresala", "AA Energy", "Dental Company Mutxamel");
    }

    @Test
    void losLogosDelCartelSonLosDeCadaPatrocinador() {

        assertThat(PatrocinadoresClub.paraCartel()).extracting(Patrocinador::logoCartel).containsExactly("la-bodega.png",
                "frutas-juanete.png", "mercury.png", "unlimited.png", "aresala.png", "aa-energy.png",
                "dental-company.png");
    }
}

package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.FondoCartel;

/** El fondo de los carteles: guardar, sustituir, leer y borrar, con H2 en modo Oracle. */
class ImagenRedesFondoDaoH2Test {

    private ImagenRedesFondoDaoImpl dao;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crearImagenesRedesFondos(jdbc);
        dao = new ImagenRedesFondoDaoImpl(jdbc);
    }

    @Test
    void sinFilaDevuelveNull() {
        assertThat(dao.obtener("PRIMER_EQUIPO")).isNull();
    }

    @Test
    void guardaYLeeLaImagenConSusDatos() {
        dao.guardar("PRIMER_EQUIPO", new byte[] { 1, 2, 3 }, "image/png", 1080, 1350, "admin");

        FondoCartel fondo = dao.obtener("PRIMER_EQUIPO");

        assertThat(fondo.imagen()).containsExactly(1, 2, 3);
        assertThat(fondo.tipoContenido()).isEqualTo("image/png");
        assertThat(fondo.ancho()).isEqualTo(1080);
        assertThat(fondo.alto()).isEqualTo(1350);
        assertThat(fondo.usuarioActualizo()).isEqualTo("admin");
        assertThat(fondo.fechaActualizacion()).isNotNull();
        assertThat(dao.obtener("FILIAL")).isNull();
    }

    @Test
    void guardarDeNuevoSustituyeElAnterior() {
        dao.guardar("PRIMER_EQUIPO", new byte[] { 1 }, "image/png", 1080, 1350, "a");
        dao.guardar("PRIMER_EQUIPO", new byte[] { 9, 9 }, "image/jpeg", 2000, 2500, "b");

        FondoCartel fondo = dao.obtener("PRIMER_EQUIPO");

        assertThat(fondo.imagen()).containsExactly(9, 9);
        assertThat(fondo.tipoContenido()).isEqualTo("image/jpeg");
        assertThat(fondo.ancho()).isEqualTo(2000);
        assertThat(fondo.usuarioActualizo()).isEqualTo("b");
    }

    @Test
    void borrarRestauraElPorDefecto() {
        dao.guardar("PRIMER_EQUIPO", new byte[] { 1 }, "image/png", 1080, 1350, null);

        assertThat(dao.borrar("PRIMER_EQUIPO")).isTrue();
        assertThat(dao.obtener("PRIMER_EQUIPO")).isNull();
        assertThat(dao.borrar("PRIMER_EQUIPO")).isFalse();
    }

    @Test
    void laTablaSoloAceptaLosCartelesYTiposPrevistos() {
        assertThatThrownBy(() -> dao.guardar("OTRO", new byte[] { 1 }, "image/png", 1080, 1350, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> dao.guardar("FILIAL", new byte[] { 1 }, "image/gif", 1080, 1350, null))
                .isInstanceOf(Exception.class);
    }
}

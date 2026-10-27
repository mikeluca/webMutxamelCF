package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado.Estado;
import com.mikedev.mutxamelcf.model.Temporada;

/**
 * Categoría por edad: edad = año de inicio de la temporada − año de nacimiento. Rangos de la temporada 2026-27:
 * Escuelita 0-5, Prebenjamín 6-7, Benjamín 8-9, Alevín 10-11, Infantil 12-13, Cadete 14-15, Juvenil 16-18.
 */
class CategoriaPorEdadImplTest {

    private CategoriaEdadDao dao;
    private CategoriaPorEdadImpl servicio;
    private Temporada temporada2026;

    @BeforeEach
    void setUp() {
        dao = mock(CategoriaEdadDao.class);
        when(dao.obtenerTodas()).thenReturn(List.of(
                new CategoriaEdad("Escuelita", 0, 5, 1),
                new CategoriaEdad("Prebenjamín", 6, 7, 2),
                new CategoriaEdad("Benjamín", 8, 9, 3),
                new CategoriaEdad("Alevín", 10, 11, 4),
                new CategoriaEdad("Infantil", 12, 13, 5),
                new CategoriaEdad("Cadete", 14, 15, 6),
                new CategoriaEdad("Juvenil", 16, 18, 7)));
        servicio = new CategoriaPorEdadImpl(dao);

        temporada2026 = new Temporada();
        temporada2026.setNombre("2026-27");
        temporada2026.setFechaInicio(Date.valueOf(LocalDate.of(2026, 9, 1)));
    }

    @ParameterizedTest(name = "nacido en {0} -> {1}")
    @CsvSource({
            "2021, Escuelita",
            "2020, Prebenjamín",
            "2019, Prebenjamín",
            "2018, Benjamín",
            "2017, Benjamín",
            "2016, Alevín",
            "2013, Infantil",
            "2011, Cadete",
            "2010, Juvenil",
            "2008, Juvenil" })
    void limitesDeLaTemporada2026_27(int anio, String esperada) {
        CategoriaPorEdadResultado resultado = servicio.categoriaPara(anio, temporada2026);

        assertThat(resultado.estado()).isEqualTo(Estado.CATEGORIA);
        assertThat(resultado.categoria()).isEqualTo(esperada);
        assertThat(resultado.edad()).isEqualTo(2026 - anio);
    }

    @Test
    void nacidoEn2007EstaFueraDeRangoPorqueSuperaLaEdadMaxima() {
        CategoriaPorEdadResultado resultado = servicio.categoriaPara(2007, temporada2026);

        assertThat(resultado.tieneCategoria()).isFalse();
        assertThat(resultado.estado()).isEqualTo(Estado.SUPERA_EDAD_MAXIMA);
        assertThat(resultado.categoria()).isNull();
        assertThat(resultado.edad()).isEqualTo(19);
    }

    @Test
    void elMismoAnioCambiaDeCategoriaConLaTemporada() {
        Temporada siguiente = new Temporada();
        siguiente.setFechaInicio(Date.valueOf(LocalDate.of(2027, 9, 1)));

        assertThat(servicio.categoriaPara(2011, temporada2026).categoria()).isEqualTo("Cadete");
        assertThat(servicio.categoriaPara(2011, siguiente).categoria()).isEqualTo("Juvenil");
    }

    @Test
    void sinAnioDeNacimientoNoHayCategoria() {
        CategoriaPorEdadResultado resultado = servicio.categoriaPara(null, temporada2026);

        assertThat(resultado.estado()).isEqualTo(Estado.SIN_ANIO_NACIMIENTO);
        assertThat(resultado.tieneCategoria()).isFalse();
        assertThat(resultado.edad()).isNull();
    }

    @Test
    void masPequenoQueLaEdadMinimaDeTodasLasCategorias() {
        // 2030 en la temporada 2026: edad -4
        assertThat(servicio.categoriaPara(2030, temporada2026).estado()).isEqualTo(Estado.POR_DEBAJO_DEL_MINIMO);
    }

    @Test
    void unHuecoEntreRangosEsSinCategoria() {
        when(dao.obtenerTodas()).thenReturn(List.of(
                new CategoriaEdad("Alevín", 10, 11, 1), new CategoriaEdad("Cadete", 14, 15, 2)));

        // edad 12: entre las dos
        assertThat(servicio.categoriaPara(2014, temporada2026).estado()).isEqualTo(Estado.SIN_CATEGORIA);
    }

    @Test
    void sinCategoriasConfiguradasNoHayCategoria() {
        when(dao.obtenerTodas()).thenReturn(List.of());

        assertThat(servicio.categoriaPara(2015, temporada2026).estado()).isEqualTo(Estado.SIN_CATEGORIA);
    }

    @Test
    void siDosRangosSeSolapanManda_elDeMenorOrden() {
        when(dao.obtenerTodas()).thenReturn(List.of(
                new CategoriaEdad("Juvenil", 15, 18, 7), new CategoriaEdad("Cadete", 14, 15, 6)));

        assertThat(servicio.categoriaPara(2011, temporada2026).categoria()).isEqualTo("Cadete");
    }

    @Test
    void elAnioDeInicioSaleDeFechaInicio() {
        Temporada julio = new Temporada();
        julio.setFechaInicio(Date.valueOf(LocalDate.of(2026, 7, 1)));

        assertThat(servicio.anioInicio(julio)).isEqualTo(2026);
        assertThatThrownBy(() -> servicio.anioInicio(new Temporada())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardarValidaLosDatos() {
        assertThatThrownBy(() -> servicio.guardar(new CategoriaEdad(" ", 1, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.guardar(new CategoriaEdad("Cadete", 15, 14, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.guardar(new CategoriaEdad("Cadete", null, 14, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        servicio.guardar(new CategoriaEdad(" Cadete ", 14, 15, 6));

        verify(dao).guardar(org.mockito.ArgumentMatchers.argThat(c -> "Cadete".equals(c.getCategoria())));
    }
}

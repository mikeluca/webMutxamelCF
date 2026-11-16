package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.ClubRivalDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.ClasificacionFilaPublicDTO;
import com.mikedev.mutxamelcf.model.ClubRival;

/** El escudo de cada fila de la clasificación pública: por el código de club o, sin él, por alias. */
class ClasificacionServiceImplTest {

    private ClasificacionDao clasificacionDao;
    private ClubRivalDao clubRivalDao;
    private ClasificacionServiceImpl service;

    @BeforeEach
    void setUp() {
        clasificacionDao = mock(ClasificacionDao.class);
        clubRivalDao = mock(ClubRivalDao.class);
        service = new ClasificacionServiceImpl(clasificacionDao, clubRivalDao, 3010);
    }

    private static Clasificacion fila(int posicion, String nombre, boolean nuestro, Integer codigo, Long clubId,
            Long escudoVersion) {
        Clasificacion c = new Clasificacion();
        c.setEquipoId(1L);
        c.setCompeticion("Primera FFCV, Grup 4");
        c.setPosicion(posicion);
        c.setNombreEquipo(nombre);
        c.setEsNuestro(nuestro);
        c.setCodigoClub(codigo);
        c.setClubRivalId(clubId);
        c.setEscudoVersion(escudoVersion);
        return c;
    }

    private static ClubRival club(long id, boolean escudo, long version) {
        ClubRival club = new ClubRival();
        club.setId(id);
        club.setTieneEscudo(escudo);
        club.setEscudoVersion(version);
        return club;
    }

    @Test
    void cadaFilaLlevaLaUrlDelEscudoDeSuClubSoloSiLoTieneYNuncaLaNuestra() {
        when(clasificacionDao.obtenerPorEquipo(1L)).thenReturn(List.of(
                fila(1, "Monnegre", false, 6574, 11L, 2L),
                fila(2, "Mutxamel C.F.", true, 3010, null, null),
                fila(3, "Mutxamel C.F.", false, 3010, null, null),
                fila(4, "Elx C.F.", false, 1111, 12L, null),
                fila(5, "Mutxamel CF B", false, 9999, 13L, 4L)));

        List<ClasificacionFilaPublicDTO> tabla = service.obtenerPublicaPorEquipo(1L).clasificacion();

        assertThat(tabla).extracting(ClasificacionFilaPublicDTO::escudoUrl).containsExactly(
                "/api/public/escudos-rivales/11?v=2", null, null, null, null);
        // Con código no hace falta mirar los alias: ninguna consulta de clubes.
        verify(clubRivalDao, never()).listar();
        verify(clubRivalDao, never()).mapaAlias();
    }

    @Test
    void lasFilasAntiguasSinCodigoSeBuscanPorAliasConUnaSolaConsultaDeClubes() {
        when(clasificacionDao.obtenerPorEquipo(1L)).thenReturn(List.of(
                fila(1, "C.D. Alicante 'A'", false, null, null, null),
                fila(2, "Villena C.F.", false, null, null, null),
                fila(3, "Altea", false, null, null, null),
                fila(4, "Mutxamel C.F.", true, null, null, null)));
        when(clubRivalDao.listar()).thenReturn(List.of(club(21, true, 7), club(22, false, 1)));
        when(clubRivalDao.mapaAlias()).thenReturn(Map.of("CD ALICANTE", 21L, "VILLENA CF", 22L));

        List<ClasificacionFilaPublicDTO> tabla = service.obtenerPublicaPorEquipo(1L).clasificacion();

        assertThat(tabla).extracting(ClasificacionFilaPublicDTO::escudoUrl).containsExactly(
                "/api/public/escudos-rivales/21?v=7", null, null, null);
        verify(clubRivalDao).listar();
        verify(clubRivalDao).mapaAlias();
    }

    @Test
    void esPropioMarcaNuestraFilaYLosFilialesPorCodigoOPorNombreYNuncaUnaRival() {
        when(clasificacionDao.obtenerPorEquipo(1L)).thenReturn(List.of(
                fila(1, "Monnegre", false, 6574, 11L, 2L),
                fila(2, "Mutxamel C.F.", true, 3010, null, null),
                fila(3, "Nombre cualquiera", false, 3010, null, null),
                fila(4, "C.F. Mutxamel 'B'", false, 9999, null, null),
                fila(5, "Atlético Mutxamel", false, 7777, 12L, 3L),
                fila(6, "Sin código", false, null, null, null)));
        when(clubRivalDao.listar()).thenReturn(List.of());
        when(clubRivalDao.mapaAlias()).thenReturn(Map.of());

        List<ClasificacionFilaPublicDTO> tabla = service.obtenerPublicaPorEquipo(1L).clasificacion();

        // Nuestra, filial por código, filial por nombre: propias y sin escudoUrl. Las rivales, no.
        assertThat(tabla).extracting(ClasificacionFilaPublicDTO::esPropio)
                .containsExactly(false, true, true, true, false, false);
        assertThat(tabla.get(2).escudoUrl()).isNull();
        assertThat(tabla.get(3).escudoUrl()).isNull();
        assertThat(tabla.get(0).escudoUrl()).isEqualTo("/api/public/escudos-rivales/11?v=2");
    }

    @Test
    void sinClasificacionNoHayNada() {
        when(clasificacionDao.obtenerPorEquipo(1L)).thenReturn(List.of());

        assertThat(service.obtenerPublicaPorEquipo(1L)).isNull();
    }
}

package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoClasificacionPrevia;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.PrevisualizacionClasificaciones;
import com.mikedev.mutxamelcf.model.ResultadoImportacionClasificaciones;

class ImportacionClasificacionesServiceImplTest {

    private static final long PRIMER_EQUIPO = 1L;
    private static final long FILIAL = 2L;
    private static final long JUVENIL_A = 3L;

    private ClasificacionDao clasificacionDao;
    private EquipoFfcvDao equipoFfcvDao;
    private EquipoDao equipoDao;

    private ImportacionClasificacionesServiceImpl service;

    @BeforeEach
    void setUp() {
        clasificacionDao = mock(ClasificacionDao.class);
        equipoFfcvDao = mock(EquipoFfcvDao.class);
        equipoDao = mock(EquipoDao.class);

        service = new ImportacionClasificacionesServiceImpl(clasificacionDao, equipoFfcvDao, equipoDao, 3010);

        List<Equipo> equipos = List.of(
                equipo(PRIMER_EQUIPO, "Primer Equipo"),
                equipo(FILIAL, "Filial"),
                equipo(JUVENIL_A, "Juvenil A"));

        when(equipoDao.obtenerActivos()).thenReturn(equipos);
        for (Equipo equipo : equipos) {
            when(equipoDao.obtenerEquipoPorId(equipo.getId())).thenReturn(equipo);
        }

        when(equipoFfcvDao.listarTodos()).thenReturn(List.of(
                new EquipoFfcv(10L, PRIMER_EQUIPO, "Primera FFCV, Grup 4", null),
                new EquipoFfcv(11L, FILIAL, "Segona FFCV, Grup 7", "B"),
                new EquipoFfcv(12L, JUVENIL_A, "Segona FFCV Juvenil, Grup 7", "A")));

        when(clasificacionDao.obtenerPorEquipo(anyLong())).thenReturn(List.of());
    }

    /**
     * Primer Equipo: en el archivo, con Monnegre y Benifutsal en su mismo grupo.
     * Filial: su grupo no está en el archivo.
     * Juvenil A: en el archivo, jornada 10.
     * Benjamín C: club 3010 en un grupo sin relación. Y un grupo ajeno sin el club.
     */
    private byte[] archivo() {
        return new ClasificacionesXlsx()
                .fila("Primera FFCV", "Grup - 4", "C.F. Monnegre de Mutxamel", "-", 6574, 3, 1, 9, 3, 3, 0, 0, 8, 1,
                        "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 2, 6, 3, 2, 0, 1, 5, 3, "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Club Mutxamel Benifutsal", "-", 6606, 3, 3, 4, 3, 1, 1, 1, 4, 4,
                        "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Elx C.F.", "-", 1111, 3, 4, 0, 3, 0, 0, 3, 1, 9, "10-10-2026")
                .fila("Segona FFCV Juvenil\t", "Grup - 7", "Mutxamel C.F.", "A", 3010, 10, 1, 24, 10, 8, 0, 2, 30,
                        10, "11-10-2026")
                .fila("Segona FFCV Juvenil\t", "Grup - 7", "Villena C.F.", "-", 2222, 10, 2, 20, 10, 6, 2, 2, 25, 12,
                        "11-10-2026")
                .fila("Segona FFCV Benjamí 1r. any Alacant  ", "F.C. - Grup 6 ", "Mutxamel C.F.", "C", 3010, 4, 3, 5,
                        4, 1, 2, 1, 6, 6, "11-10-2026")
                .fila("Segona FFCV Benjamí 1r. any Alacant  ", "F.C. - Grup 6 ", "Altea", "-", 3333, 4, 1, 12, 4, 4,
                        0, 0, 12, 1, "11-10-2026")
                .fila("Tercera FFCV", "Grup - 1", "Otro Club", "-", 4444, 2, 1, 6, 2, 2, 0, 0, 4, 0, "11-10-2026")
                .bytes();
    }

    @Test
    void vistaPreviaConUnEquipoEncontradoUnoNoEncontradoYUnoConJornadaAntigua() {

        // Juvenil A tenía guardada la jornada 12 y el archivo trae la 10.
        Clasificacion guardada = new Clasificacion();
        guardada.setEquipoId(JUVENIL_A);
        guardada.setCompeticion("Segona FFCV Juvenil, Grup 7");
        guardada.setJornada(12);
        guardada.setPosicion(1);
        guardada.setNombreEquipo("Mutxamel C.F.");
        guardada.setLetra("A");
        guardada.setEsNuestro(true);
        guardada.setPuntos(30);
        guardada.setPj(12);
        when(clasificacionDao.obtenerPorEquipo(JUVENIL_A)).thenReturn(List.of(guardada));

        PrevisualizacionClasificaciones previa = service.previsualizar(archivo());

        assertThat(previa.filasLeidas()).isEqualTo(9);
        assertThat(previa.competiciones()).isEqualTo(4);
        assertThat(previa.errores()).isEmpty();

        EquipoClasificacionPrevia primer = equipoPrevia(previa, PRIMER_EQUIPO);
        EquipoClasificacionPrevia filial = equipoPrevia(previa, FILIAL);
        EquipoClasificacionPrevia juvenil = equipoPrevia(previa, JUVENIL_A);

        // Encontrado: tabla completa del grupo, solo el club 3010 marcado como nuestro.
        assertThat(primer.encontrado()).isTrue();
        assertThat(primer.competicion()).isEqualTo("Primera FFCV, Grup 4");
        assertThat(primer.jornada()).isEqualTo(3);
        assertThat(primer.posicion()).isEqualTo(2);
        assertThat(primer.puntos()).isEqualTo(6);
        assertThat(primer.pj()).isEqualTo(3);
        assertThat(primer.cambia()).isTrue();
        assertThat(primer.cambios()).containsExactly("Primera importación");
        assertThat(primer.marcadoPorDefecto()).isTrue();
        assertThat(primer.tabla()).hasSize(4);
        assertThat(primer.tabla()).extracting(Clasificacion::getPosicion).containsExactly(1, 2, 3, 4);
        assertThat(primer.tabla()).filteredOn(Clasificacion::isEsNuestro)
                .extracting(Clasificacion::getNombreEquipo).containsExactly("Mutxamel C.F.");
        assertThat(primer.tabla()).allMatch(c -> c.getEquipoId() == PRIMER_EQUIPO);

        // No encontrado: su grupo no viene en el archivo.
        assertThat(filial.encontrado()).isFalse();
        assertThat(filial.importable()).isFalse();
        assertThat(filial.marcadoPorDefecto()).isFalse();
        assertThat(filial.motivo()).contains("no aparece");
        assertThat(filial.tabla()).isEmpty();

        // Fichero antiguo: aviso y desmarcado por defecto (pero sigue pudiéndose marcar).
        assertThat(juvenil.encontrado()).isTrue();
        assertThat(juvenil.antiguo()).isTrue();
        assertThat(juvenil.marcadoPorDefecto()).isFalse();
        assertThat(juvenil.importable()).isTrue();
        assertThat(juvenil.jornada()).isEqualTo(10);
        assertThat(juvenil.jornadaGuardada()).isEqualTo(12);
        assertThat(juvenil.cambios()).anyMatch(c -> c.startsWith("Jornada: 12"));

        // El Benjamín C tiene el club 3010 pero ningún mapeo: se lista aparte.
        assertThat(previa.sinMapeo()).hasSize(1);
        assertThat(previa.sinMapeo().get(0).competicion())
                .isEqualTo("Segona FFCV Benjamí 1r. any Alacant, F.C. - Grup 6");
        assertThat(previa.sinMapeo().get(0).letra()).isEqualTo("C");

        // La vista previa no escribe nada.
        verify(clasificacionDao, never()).eliminarPorEquipo(anyLong());
        verify(clasificacionDao, never()).insertarTodas(any());
    }

    @Test
    void sinCambiosSiLaTablaGuardadaEsIgualALaDelArchivo() {

        PrevisualizacionClasificaciones primera = service.previsualizar(archivo());
        List<Clasificacion> tabla = equipoPrevia(primera, PRIMER_EQUIPO).tabla();

        when(clasificacionDao.obtenerPorEquipo(PRIMER_EQUIPO)).thenReturn(tabla);

        EquipoClasificacionPrevia segunda = equipoPrevia(service.previsualizar(archivo()), PRIMER_EQUIPO);

        assertThat(segunda.cambia()).isFalse();
        assertThat(segunda.cambios()).isEmpty();
        assertThat(segunda.antiguo()).isFalse();
    }

    @Test
    void confirmarSustituyeLaTablaSoloDeLosEquiposMarcados() {

        PrevisualizacionClasificaciones previa = service.previsualizar(archivo());

        ResultadoImportacionClasificaciones resultado = service.confirmar("admin", previa, List.of(PRIMER_EQUIPO));

        assertThat(resultado.importados()).isEqualTo(1);
        assertThat(resultado.filas()).isEqualTo(4);
        assertThat(resultado.equipos()).containsExactly("Primer Equipo");

        // Primero se borra la tabla del equipo y luego se inserta la nueva.
        InOrder orden = Mockito.inOrder(clasificacionDao);
        orden.verify(clasificacionDao).eliminarPorEquipo(PRIMER_EQUIPO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Clasificacion>> insertadas = ArgumentCaptor.forClass(List.class);
        orden.verify(clasificacionDao).insertarTodas(insertadas.capture());

        assertThat(insertadas.getValue()).hasSize(4);
        assertThat(insertadas.getValue()).allMatch(c -> c.getEquipoId() == PRIMER_EQUIPO);
        assertThat(insertadas.getValue()).allMatch(c -> c.getFechaImportacion() != null);
        assertThat(insertadas.getValue()).filteredOn(Clasificacion::isEsNuestro).hasSize(1);

        // Los demás equipos (sin marcar) no se tocan.
        verify(clasificacionDao, never()).eliminarPorEquipo(FILIAL);
        verify(clasificacionDao, never()).eliminarPorEquipo(JUVENIL_A);
    }

    @Test
    void confirmarRechazaEquiposQueNoEstanEnLaVistaPreviaOSinNingunoMarcado() {

        PrevisualizacionClasificaciones previa = service.previsualizar(archivo());

        assertThatThrownBy(() -> service.confirmar("admin", previa, List.of(99L)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.confirmar("admin", previa, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.confirmar("admin", previa, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(clasificacionDao, never()).eliminarPorEquipo(anyLong());
    }

    @Test
    void unEquipoNoEncontradoMarcadoNoBorraNada() {

        PrevisualizacionClasificaciones previa = service.previsualizar(archivo());

        ResultadoImportacionClasificaciones resultado = service.confirmar("admin", previa, List.of(FILIAL));

        assertThat(resultado.importados()).isZero();
        assertThat(resultado.avisos()).hasSize(1);
        verify(clasificacionDao, never()).eliminarPorEquipo(anyLong());
        verify(clasificacionDao, never()).insertarTodas(any());
    }

    @Test
    void siElGrupoTieneFilasIlegiblesNoSePuedeImportar() {

        byte[] xlsx = new ClasificacionesXlsx()
                .fila("Primera FFCV", "Grup - 4", "Mutxamel C.F.", "-", 3010, 3, 2, 6, 3, 2, 0, 1, 5, 3, "10-10-2026")
                .fila("Primera FFCV", "Grup - 4", "Rival", "-", 1111, 3, "x", 0, 3, 0, 0, 3, 1, 9, "10-10-2026")
                .bytes();

        PrevisualizacionClasificaciones previa = service.previsualizar(xlsx);

        EquipoClasificacionPrevia primer = equipoPrevia(previa, PRIMER_EQUIPO);

        assertThat(previa.errores()).hasSize(1);
        assertThat(primer.importable()).isFalse();
        assertThat(primer.motivo()).contains("ilegibles");
    }

    private static EquipoClasificacionPrevia equipoPrevia(PrevisualizacionClasificaciones previa, long equipoId) {
        return previa.equipos().stream().filter(e -> e.equipoId() == equipoId).findFirst().orElseThrow();
    }

    private static Equipo equipo(long id, String nombre) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre(nombre);
        return equipo;
    }

}

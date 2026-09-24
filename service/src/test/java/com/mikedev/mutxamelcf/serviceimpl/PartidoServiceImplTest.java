package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.model.ResultadoDTO;

@ExtendWith(MockitoExtension.class)
class PartidoServiceImplTest {

    @Mock
    private PartidoDao partidoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private EquipoDao equipoDao;

    private PartidoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartidoServiceImpl(partidoDao, equipoGestionDao, equipoDao);
    }

    private static PartidoGuardarRequest requestValido() {
        return new PartidoGuardarRequest(1L, "Rival CF", LocalDate.of(2026, 3, 1), "18:00", "Campo Municipal", null);
    }

    private static Equipo equipo(Long id) {
        Equipo equipo = new Equipo();
        equipo.setId(id);
        equipo.setNombre("Senior A");
        equipo.setCategoria("SENIOR");
        equipo.setDeporte("F");
        return equipo;
    }

    // ---------- crear ----------

    @Test
    void crearLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.crear(null, requestValido())).isInstanceOf(SecurityException.class);
    }

    @Test
    void crearLanzaExcepcionSiFaltaLaPeticion() {
        assertThatThrownBy(() -> service.crear(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearLanzaExcepcionSiFaltaElEquipo() {
        PartidoGuardarRequest request = new PartidoGuardarRequest(null, "Rival CF", null, null, null, null);

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipo es obligatorio");
    }

    @Test
    void crearLanzaExcepcionSiFaltaElRival() {
        PartidoGuardarRequest request = new PartidoGuardarRequest(1L, "  ", null, null, null, null);

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rival es obligatorio");
    }

    @Test
    void crearLanzaExcepcionSiElResultadoTieneFormatoInvalido() {
        PartidoGuardarRequest request = new PartidoGuardarRequest(1L, "Rival CF", null, null, null, "no-valido");

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultado");
    }

    @Test
    void crearLanzaExcepcionSiElEquipoNoExiste() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipo no existe");
    }

    @Test
    void crearLanzaExcepcionSiElUsuarioNoPuedeGestionarElEquipo() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(SecurityException.class);
    }

    @Test
    void crearGuardaElPartidoYDevuelveElDTO() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(equipo(1L));

        Partido guardado = new Partido();
        guardado.setId(5L);
        guardado.setEquipoId(1L);
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> {
            Partido partido = invocation.getArgument(0);
            partido.setId(5L);
            return partido;
        });

        PartidoDTO resultado = service.crear(1L, requestValido());

        assertThat(resultado.getId()).isEqualTo(5L);
        assertThat(resultado.getEquipo()).isEqualTo("Senior A");
        assertThat(resultado.getRival()).isEqualTo("Rival CF");
        assertThat(resultado.getDiaFormateado()).isEqualTo("01/03/2026");
    }

    @Test
    void crearAceptaResultadoConFormatoValido() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartidoGuardarRequest request = new PartidoGuardarRequest(1L, "Rival CF", null, null, null, "2-1");

        PartidoDTO resultado = service.crear(1L, request);

        assertThat(resultado.getResultado()).isEqualTo("2-1");
    }

    @Test
    void crearPermiteCrearSiNoHayPartidosPreviosDelEquipo() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of());
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.crear(1L, requestValido())).isNotNull();
    }

    @Test
    void crearPermiteCrearSiElUltimoPartidoTieneResultado() {
        Partido anterior = new Partido();
        anterior.setResultado("1-0");

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(anterior));
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.crear(1L, requestValido())).isNotNull();
    }

    @Test
    void crearRechazaSiElUltimoPartidoNoTieneResultado() {
        Partido anterior = new Partido();
        anterior.setResultado(null);

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(anterior));

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tenga el resultado puesto");

        verify(partidoDao, never()).crear(any());
    }

    @Test
    void crearRechazaSiElUltimoPartidoTieneResultadoEnBlanco() {
        Partido anterior = new Partido();
        anterior.setResultado("  ");

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(anterior));

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearComoAdminRechazaSiElUltimoPartidoNoTieneResultado() {
        Partido anterior = new Partido();
        anterior.setResultado(null);

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(anterior));

        assertThatThrownBy(() -> service.crearComoAdmin(requestValido())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tenga el resultado puesto");

        verify(partidoDao, never()).crear(any());
    }

    @Test
    void crearComoAdminPermiteCrearSiElUltimoPartidoTieneResultado() {
        Partido anterior = new Partido();
        anterior.setResultado("1-0");

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(anterior));
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.crearComoAdmin(requestValido())).isNotNull();
    }

    // ---------- actualizar ----------

    @Test
    void actualizarLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.actualizar(null, 5L, requestValido()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void actualizarLanzaExcepcionSiFaltaElId() {
        assertThatThrownBy(() -> service.actualizar(1L, null, requestValido()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.actualizar(1L, 5L, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void actualizarLanzaExcepcionSiCambiaElEquipo() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(2L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);

        assertThatThrownBy(() -> service.actualizar(1L, 5L, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cambiar el equipo");
    }

    @Test
    void actualizarLanzaExcepcionSiElUsuarioNoPuedeGestionarElEquipo() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.actualizar(1L, 5L, requestValido()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void actualizarModificaLosCamposYFijaElUsuarioQueActualiza() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(equipo(1L));

        PartidoDTO resultado = service.actualizar(1L, 5L, requestValido());

        assertThat(resultado.getRival()).isEqualTo("Rival CF");
        assertThat(existente.getUsuarioActualizoId()).isEqualTo(1L);
        assertThat(existente.getFechaActualizacion()).isNotNull();
        verify(partidoDao).actualizar(existente);
    }

    // ---------- crearComoAdmin ----------

    @Test
    void crearComoAdminNoCompruebaPermisosDeGestion() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(partidoDao.crear(any(Partido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearComoAdmin(requestValido());

        verify(equipoGestionDao, never()).puedeGestionarEquipo(any(), any());
    }

    @Test
    void crearComoAdminLanzaExcepcionSiElEquipoNoExiste() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.crearComoAdmin(requestValido()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearComoAdminLanzaExcepcionSiFaltaLaPeticion() {
        assertThatThrownBy(() -> service.crearComoAdmin(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- actualizarComoAdmin ----------

    @Test
    void actualizarComoAdminNoCompruebaPermisosDeGestion() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);

        service.actualizarComoAdmin(5L, requestValido());

        verify(equipoGestionDao, never()).puedeGestionarEquipo(any(), any());
        assertThat(existente.getUsuarioActualizoId()).isNull();
    }

    @Test
    void actualizarComoAdminLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.actualizarComoAdmin(5L, requestValido()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarComoAdminLanzaExcepcionSiFaltaElId() {
        assertThatThrownBy(() -> service.actualizarComoAdmin(null, requestValido()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarComoAdminLanzaExcepcionSiCambiaElEquipo() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(2L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);

        assertThatThrownBy(() -> service.actualizarComoAdmin(5L, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cambiar el equipo");
    }

    // ---------- eliminar ----------

    @Test
    void eliminarLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.eliminar(null, 5L)).isInstanceOf(SecurityException.class);
    }

    @Test
    void eliminarLanzaExcepcionSiFaltaElId() {
        assertThatThrownBy(() -> service.eliminar(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.eliminar(1L, 5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarLanzaExcepcionSiElUsuarioNoPuedeGestionarElEquipo() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(1L, 5L)).isInstanceOf(SecurityException.class);

        verify(partidoDao, never()).eliminar(any());
    }

    @Test
    void eliminarBorraElPartidoCuandoElUsuarioPuedeGestionarElEquipo() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);

        service.eliminar(1L, 5L);

        verify(partidoDao).eliminar(5L);
    }

    @Test
    void eliminarComoAdminLanzaExcepcionSiFaltaElId() {
        assertThatThrownBy(() -> service.eliminarComoAdmin(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarComoAdminLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.eliminarComoAdmin(5L)).isInstanceOf(IllegalArgumentException.class);

        verify(partidoDao, never()).eliminar(any());
    }

    @Test
    void eliminarComoAdminBorraElPartidoSinComprobarPermisos() {
        Partido existente = new Partido();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(partidoDao.obtenerPorId(5L)).thenReturn(existente);

        service.eliminarComoAdmin(5L);

        verify(partidoDao).eliminar(5L);
        verify(equipoGestionDao, never()).puedeGestionarEquipo(any(), any());
    }

    // ---------- lecturas ----------

    @Test
    void obtenerUltimosPorEquipoDelegaEnElDao() {
        Partido partido = new Partido();
        partido.setId(1L);
        partido.setEquipoId(1L);
        when(partidoDao.obtenerUltimosPorEquipo(1L, 5)).thenReturn(List.of(partido));

        List<PartidoDTO> resultado = service.obtenerUltimosPorEquipo(1L, 5);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void obtenerUltimosPorEquipoNombreDevuelveVacioSiNoExisteElEquipo() {
        when(equipoDao.obtenerEquipoPorNombre("Senior A")).thenReturn(null);

        assertThat(service.obtenerUltimosPorEquipoNombre("Senior A", 5)).isEmpty();
    }

    @Test
    void obtenerUltimosPorEquipoNombreResuelveElEquipoPorNombre() {
        when(equipoDao.obtenerEquipoPorNombre("Senior A")).thenReturn(equipo(1L));

        Partido partido = new Partido();
        partido.setId(1L);
        partido.setEquipoId(1L);
        when(partidoDao.obtenerUltimosPorEquipo(1L, 5)).thenReturn(List.of(partido));

        List<PartidoDTO> resultado = service.obtenerUltimosPorEquipoNombre("Senior A", 5);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void obtenerPorEquipoDevuelveElHistoricoCompleto() {
        Partido partido = new Partido();
        partido.setId(1L);
        partido.setEquipoId(1L);
        when(partidoDao.obtenerPorEquipo(1L)).thenReturn(List.of(partido));

        assertThat(service.obtenerPorEquipo(1L)).hasSize(1);
    }

    @Test
    void obtenerResultadosDevuelveUnaEntradaPorCadaEquipoDelDeporte() {
        Equipo equipoA = equipo(1L);
        Equipo equipoB = equipo(2L);

        Partido partido = new Partido();
        partido.setId(1L);
        partido.setEquipoId(1L);
        partido.setRival("Rival CF");
        partido.setResultado("2-1");

        when(equipoDao.obtenerTodosPorDeporte("F")).thenReturn(List.of(equipoA, equipoB));
        when(partidoDao.obtenerMasRelevantePorEquipo(1L)).thenReturn(partido);
        when(partidoDao.obtenerMasRelevantePorEquipo(2L)).thenReturn(null);

        List<ResultadoDTO> resultados = service.obtenerResultados("F");

        assertThat(resultados).hasSize(2);

        assertThat(resultados.get(0).getEquipo()).isEqualTo("Senior A");
        assertThat(resultados.get(0).getCategoria()).isEqualTo("SENIOR");
        assertThat(resultados.get(0).getRival()).isEqualTo("Rival CF");
    }

    @Test
    void obtenerResultadosIncluyePlaceholderConRivalNuloParaEquiposSinPartidos() {
        Equipo equipoSinPartidos = equipo(2L);

        when(equipoDao.obtenerTodosPorDeporte("F")).thenReturn(List.of(equipoSinPartidos));
        when(partidoDao.obtenerMasRelevantePorEquipo(2L)).thenReturn(null);

        List<ResultadoDTO> resultados = service.obtenerResultados("F");

        assertThat(resultados).hasSize(1);
        ResultadoDTO placeholder = resultados.get(0);

        assertThat(placeholder.getEquipo()).isEqualTo("Senior A");
        assertThat(placeholder.getCategoria()).isEqualTo("SENIOR");
        assertThat(placeholder.getRival()).isNull();
        assertThat(placeholder.getResultado()).isNull();
        assertThat(placeholder.getDia()).isNull();
        assertThat(placeholder.getDiaFormateado()).isNull();
        assertThat(placeholder.getHora()).isNull();
        assertThat(placeholder.getCampo()).isNull();
    }

    @Test
    void obtenerResultadosDevuelveListaVaciaSiNoHayEquiposDeEseDeporte() {
        when(equipoDao.obtenerTodosPorDeporte("FS")).thenReturn(List.of());

        assertThat(service.obtenerResultados("FS")).isEmpty();
    }

    @Test
    void obtenerResultadoPrimerEquipoDevuelveNullSiNoHayPartido() {
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(null);

        assertThat(service.obtenerResultadoPrimerEquipo()).isNull();
    }

    @Test
    void obtenerResultadoPrimerEquipoUsaElCriterioLiteralDeEquipoYCategoria() {
        Partido partido = new Partido();
        partido.setRival("Rival CF");
        partido.setResultado("1-0");

        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partido);

        ResultadoDTO resultado = service.obtenerResultadoPrimerEquipo();

        assertThat(resultado.getEquipo()).isEqualTo("Mutxamel CF");
        assertThat(resultado.getCategoria()).isEqualTo("Primer Equipo");
        assertThat(resultado.getRival()).isEqualTo("Rival CF");
    }

    @Test
    void obtenerResultadoPrimerEquipoDejaDiaFormateadoVacioCuandoNoHayFecha() {
        Partido partido = new Partido();
        partido.setDia(null);

        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).thenReturn(partido);

        ResultadoDTO resultado = service.obtenerResultadoPrimerEquipo();

        assertThat(resultado.getDiaFormateado()).isEmpty();
        assertThat(resultado.getDia()).isNull();
    }
}

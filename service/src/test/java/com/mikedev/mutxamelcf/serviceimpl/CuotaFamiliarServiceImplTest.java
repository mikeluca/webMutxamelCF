package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaFamiliarResponse;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;

@ExtendWith(MockitoExtension.class)
class CuotaFamiliarServiceImplTest {

    @Mock
    private PerfilAppDao perfilAppDao;

    @Mock
    private CuotaJugadorService cuotaJugadorService;

    @Mock
    private ConceptoPagoService conceptoPagoService;

    @Mock
    private PagoService pagoService;

    private CuotaFamiliarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuotaFamiliarServiceImpl(
                perfilAppDao, cuotaJugadorService, conceptoPagoService, pagoService);
    }

    private Jugador jugador(long id, String nombre, String apellidos) {
        Jugador jugador = new Jugador();
        jugador.setId(id);
        jugador.setNombre(nombre);
        jugador.setApellidos(apellidos);
        return jugador;
    }

    private CuotaJugadorDTO cuota(long id, long jugadorId, long conceptoId, String estado, Date fechaLimite) {
        return cuota(id, jugadorId, conceptoId, estado, fechaLimite, null);
    }

    private CuotaJugadorDTO cuota(
            long id, long jugadorId, long conceptoId, String estado, Date fechaLimite, String periodo) {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(id);
        cuota.setJugadorId(jugadorId);
        cuota.setConceptoPagoId(conceptoId);
        cuota.setEstado(estado);
        cuota.setImporte(BigDecimal.TEN);
        cuota.setPeriodo(periodo);
        cuota.setFechaLimite(fechaLimite);
        return cuota;
    }

    private ConceptoPagoDTO concepto(long id, String nombre) {
        ConceptoPagoDTO concepto = new ConceptoPagoDTO();
        concepto.setId(id);
        concepto.setNombre(nombre);
        return concepto;
    }

    @Test
    void devuelveLasCuotasDeTodosLosJugadoresVinculadosConSuConcepto() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(
                jugador(1L, "Juan", "Perez"),
                jugador(2L, "Ana", "Gomez")));

        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PAGADO", null)));
        when(cuotaJugadorService.obtenerPorJugador(2L)).thenReturn(List.of(
                cuota(11L, 2L, 200L, "PENDIENTE", null)));

        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));
        when(conceptoPagoService.obtenerPorId(200L)).thenReturn(concepto(200L, "Equipación"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getJugadorNombre()).isEqualTo("Juan Perez");
        assertThat(resultado.get(0).getConcepto()).isEqualTo("Mensualidad");
        assertThat(resultado.get(1).getJugadorNombre()).isEqualTo("Ana Gomez");
        assertThat(resultado.get(1).getConcepto()).isEqualTo("Equipación");
    }

    @Test
    void marcaVencidaUnaCuotaPendienteConFechaLimitePasada() {
        Date ayer = Date.from(java.time.Instant.now().minus(1, ChronoUnit.DAYS));

        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PENDIENTE", ayer)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).isVencida()).isTrue();
    }

    @Test
    void unaCuotaPagadaConFechaLimitePasadaNoSeMarcaVencida() {
        Date ayer = Date.from(java.time.Instant.now().minus(1, ChronoUnit.DAYS));

        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PAGADO", ayer)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).isVencida()).isFalse();
    }

    @Test
    void elEstadoDevueltoEsElYaGuardadoEnLaCuota() {
        // La columna ESTADO ya se sincroniza correctamente al registrar
        // un pago (CuotaJugadorDaoImpl.actualizarEstado): el servicio no
        // debe recalcularla a partir de los pagos, solo devolverla.
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PARCIAL", null)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).getEstado()).isEqualTo("PARCIAL");
    }

    @Test
    void formateaElPeriodoComoMesYAnioEnEspanol() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PENDIENTE", null, "2026-09")));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Cuota F8"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).getPeriodoFormateado()).isEqualTo("Septiembre / 2026");
    }

    @Test
    void sinPeriodoNoDevuelvePeriodoFormateado() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PENDIENTE", null, null)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Matrícula"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).getPeriodoFormateado()).isNull();
    }

    @Test
    void incluyeLosPagosRegistradosParaLaCuota() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PAGADO", null)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));

        PagoDTO pago = new PagoDTO();
        pago.setImporte(BigDecimal.TEN);
        pago.setFechaPago(Date.from(java.time.Instant.parse("2026-09-15T00:00:00Z")));
        pago.setMetodoPago("Transferencia");

        when(pagoService.obtenerPorCuota(10L)).thenReturn(List.of(pago));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).getPagos()).hasSize(1);
        assertThat(resultado.get(0).getPagos().get(0).getMetodoPago()).isEqualTo("Transferencia");
        assertThat(resultado.get(0).getPagos().get(0).getImporte()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void sinPagosDevuelveListaDePagosVacia() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of(jugador(1L, "Juan", "Perez")));
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of(
                cuota(10L, 1L, 100L, "PENDIENTE", null)));
        when(conceptoPagoService.obtenerPorId(100L)).thenReturn(concepto(100L, "Mensualidad"));

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado.get(0).getPagos()).isEmpty();
    }

    @Test
    void sinJugadoresVinculadosDevuelveListaVacia() {
        when(perfilAppDao.obtenerJugadoresPorUsuario(7)).thenReturn(List.of());

        List<CuotaFamiliarResponse> resultado = service.obtenerCuotasDeMisJugadores(7);

        assertThat(resultado).isEmpty();
    }
}

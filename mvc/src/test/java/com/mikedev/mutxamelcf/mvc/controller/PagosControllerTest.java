package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.BecadoService;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagosControllerTest {

    private TemporadaService temporadaService;
    private ConceptoPagoService conceptoPagoService;
    private CuotaJugadorService cuotaJugadorService;
    private PagoService pagoService;
    private BecadoService becadoService;
    private JugadorService jugadorService;
    private EquipoService equipoService;
    private PagosController controller;

    @BeforeEach
    void setUp() {
        temporadaService = mock(TemporadaService.class);
        conceptoPagoService = mock(ConceptoPagoService.class);
        cuotaJugadorService = mock(CuotaJugadorService.class);
        pagoService = mock(PagoService.class);
        becadoService = mock(BecadoService.class);
        jugadorService = mock(JugadorService.class);
        equipoService = mock(EquipoService.class);
        controller = new PagosController(temporadaService, conceptoPagoService, cuotaJugadorService, pagoService,
                becadoService, jugadorService, equipoService);
    }

    private static RedirectAttributes redirectAttributes() {
        return new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
    }

    private static TemporadaDTO temporada(Long id, String nombre) {
        TemporadaDTO temporada = new TemporadaDTO();
        temporada.setId(id);
        temporada.setNombre(nombre);
        return temporada;
    }

    private static ConceptoPagoDTO concepto(Long id, Long temporadaId, String nombre, BigDecimal importe,
            Integer activo) {
        ConceptoPagoDTO concepto = new ConceptoPagoDTO();
        concepto.setId(id);
        concepto.setTemporadaId(temporadaId);
        concepto.setNombre(nombre);
        concepto.setImporte(importe);
        concepto.setActivo(activo);
        return concepto;
    }

    private static CuotaJugadorDTO cuota(Long id, Long jugadorId, Long conceptoPagoId, String periodo,
            BigDecimal importe) {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(id);
        cuota.setJugadorId(jugadorId);
        cuota.setConceptoPagoId(conceptoPagoId);
        cuota.setPeriodo(periodo);
        cuota.setImporte(importe);
        return cuota;
    }

    private static JugadorDTO jugador(Long id, String equipo) {
        JugadorDTO jugador = new JugadorDTO();
        jugador.setId(id);
        jugador.setEquipo(equipo);
        return jugador;
    }

    // ---- dashboard ----

    @Test
    void dashboardConstruyeElResumenDeCuotasYPagos() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerTemporadaActiva()).thenReturn(temporada);

        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of(concepto));

        CuotaJugadorDTO cuota = cuota(100L, 5L, 10L, "2025-09", new BigDecimal("30.00"));
        when(cuotaJugadorService.obtenerPorTemporada(1L)).thenReturn(List.of(cuota));

        JugadorDTO jugador = jugador(5L, "Alevin A");
        when(jugadorService.obtenerTodos()).thenReturn(List.of(jugador));

        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(100L);
        pago.setImporte(new BigDecimal("10.00"));
        when(pagoService.obtenerPorCuotas(List.of(100L))).thenReturn(List.of(pago));

        Model model = new ExtendedModelMap();
        String vista = controller.dashboard(null, model);

        assertEquals("admin/pagos", vista);
        assertEquals(new BigDecimal("30.00"), model.getAttribute("totalPrevisto"));
        assertEquals(new BigDecimal("10.00"), model.getAttribute("totalPagado"));
        assertEquals(new BigDecimal("20.00"), model.getAttribute("totalPendiente"));
        assertEquals(1L, model.getAttribute("cuotasPendientes"));
        assertEquals(1, ((List<?>) model.getAttribute("cuotas")).size());
        assertEquals(1, ((java.util.Collection<?>) model.getAttribute("resumenEquipos")).size());
    }

    @Test
    void dashboardSinTemporadasDejaListasVacias() {
        when(temporadaService.obtenerTodos()).thenReturn(List.of());
        when(temporadaService.obtenerTemporadaActiva()).thenReturn(null);
        when(jugadorService.obtenerTodos()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.dashboard(null, model);

        assertTrue(((List<?>) model.getAttribute("conceptos")).isEmpty());
        assertEquals(BigDecimal.ZERO, model.getAttribute("totalPrevisto"));
    }

    @Test
    void dashboardIgnoraCuotasConConceptoInexistente() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerTemporadaActiva()).thenReturn(temporada);
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of());
        CuotaJugadorDTO cuotaSinConcepto = cuota(100L, 5L, 999L, "2025-09", new BigDecimal("30.00"));
        when(cuotaJugadorService.obtenerPorTemporada(1L)).thenReturn(List.of(cuotaSinConcepto));
        when(jugadorService.obtenerTodos()).thenReturn(List.of());
        when(pagoService.obtenerPorCuotas(any())).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.dashboard(1L, model);

        assertTrue(((List<?>) model.getAttribute("cuotas")).isEmpty());
    }

    // ---- listados simples ----

    @Test
    void temporadasRellenaElModelo() {
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada(1L, "2025/2026")));

        Model model = new ExtendedModelMap();
        assertEquals("admin/pagos-temporadas", controller.temporadas(model));
        assertEquals(1, ((List<?>) model.getAttribute("temporadas")).size());
    }

    @Test
    void conceptosCalculaLosPeriodosDeLaTemporadaPorNombre() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String vista = controller.conceptos(1L, model);

        assertEquals("admin/pagos-conceptos", vista);
        assertEquals("2025-09", model.getAttribute("periodoInicioTemporada"));
        assertEquals("2026-06", model.getAttribute("periodoFinTemporada"));
    }

    @Test
    void asignarCuotasFiltraJugadoresPorEquipo() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        when(jugadorService.obtenerTodos()).thenReturn(
                List.of(jugador(1L, "Alevin A"), jugador(2L, "Alevin B")));
        when(equipoService.obtenerCategorias()).thenReturn(List.of("Alevin"));
        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of());
        when(conceptoPagoService.obtenerTodos()).thenReturn(List.of());
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of());
        when(cuotaJugadorService.obtenerTodos()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String vista = controller.asignarCuotas(1L, "Alevin A", model);

        assertEquals("admin/pagos-asignar", vista);
        assertEquals(1, ((List<?>) model.getAttribute("jugadoresDisponibles")).size());
    }

    @Test
    void asignarCuotaJugadorRedirigeConParametros() {
        assertEquals("redirect:/admin/pagos/asignar-cuotas?temporadaId=1&equipo=Alevin",
                controller.asignarCuotaJugador(1L, "Alevin"));
        assertEquals("redirect:/admin/pagos/asignar-cuotas", controller.asignarCuotaJugador(null, null));
    }

    // ---- temporadas CRUD ----

    @Test
    void guardarTemporadaDelegaEnElServicioYRedirige() {
        RedirectAttributes redirect = redirectAttributes();
        String vista = controller.guardarTemporada(null, "2025/2026", "2025-09-01", "2026-06-30", 1, redirect);

        assertEquals("redirect:/admin/pagos/temporadas", vista);
        verify(temporadaService).guardarTemporada(any());
    }

    @Test
    void borrarTemporadaDelegaEnElServicio() {
        RedirectAttributes redirect = redirectAttributes();
        controller.borrarTemporada(1L, redirect);
        verify(temporadaService).eliminar(1L);
    }

    // ---- conceptos CRUD ----

    @Test
    void guardarConceptoDelegaEnElServicioYRedirigeConTemporada() {
        RedirectAttributes redirect = redirectAttributes();
        String vista = controller.guardarConcepto(null, 1L, "Cuota", "desc", new BigDecimal("30"), 1, redirect);

        assertEquals("redirect:/admin/pagos/conceptos?temporadaId=1", vista);
        verify(conceptoPagoService).guardarConceptoPago(any());
    }

    @Test
    void borrarConceptoDelegaEnElServicio() {
        RedirectAttributes redirect = redirectAttributes();
        controller.borrarConcepto(1L, 1L, redirect);
        verify(conceptoPagoService).eliminar(1L);
    }

    // ---- guardarCuota (masivo por categoria) ----

    @Test
    void guardarCuotaRechazaConceptoInvalido() {
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(null);
        RedirectAttributes redirect = redirectAttributes();

        String vista = controller.guardarCuota(10L, "Alevin", null, 1L, null, null, null, null, redirect);

        assertEquals("redirect:/admin/pagos/asignar-cuotas?temporadaId=1", vista);
        assertEquals("El concepto de pago no existe o no tiene importe.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    @Test
    void guardarCuotaCreaCuotasMensualesParaCadaJugadorYPeriodo() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        when(jugadorService.obtenerJugadoresPorCategoria("Alevin"))
                .thenReturn(List.of(jugador(1L, "Alevin A")));
        when(cuotaJugadorService.guardarCuota(any())).thenReturn(true);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuota(10L, "Alevin", null, 1L, null, null, null, null, redirect);

        verify(cuotaJugadorService, org.mockito.Mockito.atLeastOnce()).guardarCuota(any());
    }

    @Test
    void guardarCuotaNoCreaCuotasDuplicadas() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Matricula", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        when(jugadorService.obtenerJugadoresPorCategoria("Alevin"))
                .thenReturn(List.of(jugador(1L, "Alevin A")));
        when(cuotaJugadorService.obtenerTodos()).thenReturn(List.of(cuota(1L, 1L, 10L, null, new BigDecimal("30"))));

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuota(10L, "Alevin", null, 1L, null, null, null, null, redirect);

        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    // ---- guardarCuotaJugador (individual) ----

    @Test
    void guardarCuotaJugadorRechazaSiElJugadorNoExiste() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of(concepto));
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(null);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuotaJugador(5L, 1L, 10L, null, null, null, redirect);

        assertEquals("El jugador o el concepto de pago no existe.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    @Test
    void guardarCuotaJugadorCreaLaCuotaUnicaCuandoElConceptoNoEsMensual() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Matricula", new BigDecimal("50.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of(concepto));
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(jugador(5L, "Alevin A"));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada(1L, "2025/2026"));
        when(cuotaJugadorService.guardarCuota(any())).thenReturn(true);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuotaJugador(5L, 1L, 10L, null, null, null, redirect);

        verify(cuotaJugadorService).guardarCuota(any());
    }

    // ---- editarCuota ----

    @Test
    void editarCuotaMarcaErrorSiLaCuotaNoExiste() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(null);
        RedirectAttributes redirect = redirectAttributes();

        controller.editarCuota(1L, 10L, "2025-09", null, null, "categoria", 1L, null, redirect);

        assertEquals("La cuota o el concepto no existe.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    @Test
    void editarCuotaActualizaLaCuotaCuandoExiste() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, "2025-09", new BigDecimal("20")));
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto(10L, 1L, "Cuota", new BigDecimal("30"), 1));
        RedirectAttributes redirect = redirectAttributes();

        controller.editarCuota(1L, 10L, "2025-10", null, null, "categoria", 1L, null, redirect);

        verify(cuotaJugadorService).guardarCuota(any());
        assertEquals("Cuota actualizada correctamente.", redirect.getFlashAttributes().get("mensaje"));
    }

    // ---- borrarCuota / borrarCuotasSeleccionadas ----

    @Test
    void borrarCuotaDelegaEnElServicio() {
        RedirectAttributes redirect = redirectAttributes();
        controller.borrarCuota(1L, "categoria", 1L, null, redirect);
        verify(cuotaJugadorService).eliminar(1L);
    }

    @Test
    void borrarCuotasSeleccionadasSinSeleccionMarcaError() {
        RedirectAttributes redirect = redirectAttributes();
        controller.borrarCuotasSeleccionadas(null, 1L, null, redirect);

        assertEquals("No hay cuotas seleccionadas para borrar.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).eliminarEnLote(any());
    }

    @Test
    void borrarCuotasSeleccionadasEliminaLasIndicadas() {
        RedirectAttributes redirect = redirectAttributes();
        controller.borrarCuotasSeleccionadas(List.of(1L, 2L), 1L, null, redirect);

        verify(cuotaJugadorService).eliminarEnLote(List.of(1L, 2L));
    }

    // ---- registrarPago ----

    @Test
    void registrarPagoRechazaImporteMayorQueElPendiente() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO);
        RedirectAttributes redirect = redirectAttributes();

        String vista = controller.registrarPago(null, 1L, new BigDecimal("50"), "2026-01-01", "Efectivo", null, null,
                redirect);

        assertEquals("redirect:/admin/pagos", vista);
        assertEquals("El importe supera el saldo pendiente o la cuota no existe.",
                redirect.getFlashAttributes().get("error"));
        verify(pagoService, never()).guardarPago(any());
    }

    @Test
    void registrarPagoGuardaYActualizaElEstadoDeLaCuota() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO);
        RedirectAttributes redirect = redirectAttributes();

        String vista = controller.registrarPago(null, 1L, new BigDecimal("20"), "2026-01-01", "Efectivo", null, null,
                redirect);

        assertEquals("redirect:/admin/pagos", vista);
        verify(pagoService).guardarPago(any());
        verify(cuotaJugadorService).actualizarEstado(1L);
    }

    // ---- registrarPagoAjax ----

    @Test
    void registrarPagoAjaxDevuelveBadRequestSiElImporteEsInvalido() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO);

        ResponseEntity<Map<String, Object>> response = controller.registrarPagoAjax(1L, new BigDecimal("50"),
                "2026-01-01", "Efectivo");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void registrarPagoAjaxIgnoraFalloDeSincronizacionDeEstado() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO, new BigDecimal("20"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fallo"))
                .when(cuotaJugadorService).actualizarEstado(1L);

        ResponseEntity<Map<String, Object>> response = controller.registrarPagoAjax(1L, new BigDecimal("20"),
                "2026-01-01", "Efectivo");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pagoService).guardarPago(any());
    }

    @Test
    void registrarPagoAjaxDevuelveOkCuandoTieneExito() {
        when(cuotaJugadorService.obtenerPorId(1L)).thenReturn(cuota(1L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO, new BigDecimal("20"));

        ResponseEntity<Map<String, Object>> response = controller.registrarPagoAjax(1L, new BigDecimal("20"),
                "2026-01-01", "Efectivo");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("20"), response.getBody().get("pagado"));
    }

    // ---- editarPagoAjax ----

    @Test
    void editarPagoAjaxDevuelveBadRequestSiElPagoNoExiste() {
        when(pagoService.obtenerPorId(1L)).thenReturn(null);
        when(cuotaJugadorService.obtenerPorId(9L)).thenReturn(cuota(9L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(9L)).thenReturn(BigDecimal.ZERO);

        ResponseEntity<Map<String, Object>> response = controller.editarPagoAjax(1L, 9L, new BigDecimal("10"),
                "2026-01-01", "Efectivo");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void editarPagoAjaxActualizaElPagoCuandoEsValido() {
        PagoDTO pago = new PagoDTO();
        pago.setId(1L);
        pago.setCuotaJugadorId(9L);
        pago.setImporte(new BigDecimal("10"));
        when(pagoService.obtenerPorId(1L)).thenReturn(pago);
        when(cuotaJugadorService.obtenerPorId(9L)).thenReturn(cuota(9L, 5L, 9L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(9L)).thenReturn(new BigDecimal("10"), new BigDecimal("15"));

        ResponseEntity<Map<String, Object>> response = controller.editarPagoAjax(1L, 9L, new BigDecimal("15"),
                "2026-01-01", "Efectivo");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pagoService).guardarPago(pago);
        verify(cuotaJugadorService).actualizarEstado(9L);
    }

    // ---- borrarPagoAjax ----

    @Test
    void borrarPagoAjaxDevuelveNotFoundSiNoExiste() {
        when(pagoService.obtenerPorId(1L)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.borrarPagoAjax(1L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(pagoService, never()).eliminar(anyLong());
    }

    @Test
    void borrarPagoAjaxEliminaYActualizaLaCuota() {
        PagoDTO pago = new PagoDTO();
        pago.setId(1L);
        pago.setCuotaJugadorId(9L);
        when(pagoService.obtenerPorId(1L)).thenReturn(pago);
        when(cuotaJugadorService.obtenerPorId(9L)).thenReturn(cuota(9L, 5L, 10L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(9L)).thenReturn(new BigDecimal("5"));

        ResponseEntity<Map<String, Object>> response = controller.borrarPagoAjax(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pagoService).eliminar(1L);
        verify(cuotaJugadorService).actualizarEstado(9L);
    }

    // ---- marcarJugadorBecado ----

    @Test
    void marcarJugadorBecadoMarcaErrorSiElJugadorNoExiste() {
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(null);
        RedirectAttributes redirect = redirectAttributes();

        controller.marcarJugadorBecado(5L, 1L, redirect);

        assertEquals("El jugador seleccionado no existe.", redirect.getFlashAttributes().get("error"));
        verify(becadoService, never()).marcarCuotasComoBecadas(any());
    }

    @Test
    void marcarJugadorBecadoIndicaCuandoYaEstaTodoPagado() {
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(jugador(5L, "Alevin A"));
        when(becadoService.marcarCuotasComoBecadas(5L)).thenReturn(0);
        RedirectAttributes redirect = redirectAttributes();

        controller.marcarJugadorBecado(5L, 1L, redirect);

        assertEquals("El jugador ya tiene todas sus cuotas pagadas.", redirect.getFlashAttributes().get("mensaje"));
    }

    @Test
    void marcarJugadorBecadoCapturaErroresInesperados() {
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(jugador(5L, "Alevin A"));
        when(becadoService.marcarCuotasComoBecadas(5L)).thenThrow(new RuntimeException("fallo"));
        RedirectAttributes redirect = redirectAttributes();

        String vista = controller.marcarJugadorBecado(5L, 1L, redirect);

        assertEquals("redirect:/admin/pagos?temporadaId=1", vista);
        assertEquals("No se han podido actualizar las cuotas del jugador.", redirect.getFlashAttributes().get("error"));
    }

    // ---- borrarPago ----

    @Test
    void borrarPagoActualizaLaCuotaSiElPagoExistia() {
        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(9L);
        when(pagoService.obtenerPorId(1L)).thenReturn(pago);
        RedirectAttributes redirect = redirectAttributes();

        controller.borrarPago(1L, redirect);

        verify(pagoService).eliminar(1L);
        verify(cuotaJugadorService).actualizarEstado(9L);
    }

    @Test
    void borrarPagoNoActualizaLaCuotaSiElPagoNoExistia() {
        when(pagoService.obtenerPorId(1L)).thenReturn(null);
        RedirectAttributes redirect = redirectAttributes();

        controller.borrarPago(1L, redirect);

        verify(cuotaJugadorService, never()).actualizarEstado(any());
    }

    @Test
    void dashboardAgrupaJugadoresSinEquipoBajoSinEquipo() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerTemporadaActiva()).thenReturn(temporada);
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of(concepto));
        CuotaJugadorDTO cuota = cuota(100L, 5L, 10L, "2025-09", new BigDecimal("30.00"));
        when(cuotaJugadorService.obtenerPorTemporada(1L)).thenReturn(List.of(cuota));
        when(jugadorService.obtenerTodos()).thenReturn(List.of(jugador(5L, null)));
        when(pagoService.obtenerPorCuotas(any())).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.dashboard(null, model);

        java.util.Collection<?> resumenEquipos = (java.util.Collection<?>) model.getAttribute("resumenEquipos");
        assertEquals(1, resumenEquipos.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> equipo = (Map<String, Object>) resumenEquipos.iterator().next();
        assertEquals("Sin equipo", equipo.get("nombre"));
    }

    @Test
    void asignarCuotasDedupicaEquiposConElMismoNombre() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        when(jugadorService.obtenerTodos()).thenReturn(List.of(jugador(1L, "Alevin A")));
        when(equipoService.obtenerTodos()).thenReturn(
                List.of(equipoConNombreYOrden("Alevin A", "1"), equipoConNombreYOrden("Alevin A", "2")));
        when(equipoService.obtenerCategorias()).thenReturn(List.of());
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of());
        when(conceptoPagoService.obtenerTodos()).thenReturn(List.of());
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of());
        when(cuotaJugadorService.obtenerTodos()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.asignarCuotas(1L, null, model);

        assertEquals(List.of("Alevin A"), model.getAttribute("equipos"));
    }

    private static EquipoDTO equipoConNombreYOrden(String nombre, String orden) {
        EquipoDTO equipo = new EquipoDTO();
        equipo.setNombre(nombre);
        equipo.setOrden(orden);
        return equipo;
    }

    @Test
    void asignarCuotasSinTemporadaDevuelveCuotasAsignadasVacias() {
        when(temporadaService.obtenerTodos()).thenReturn(List.of());
        when(temporadaService.obtenerPorId(null)).thenReturn(null);
        when(jugadorService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerCategorias()).thenReturn(List.of());
        when(conceptoPagoService.obtenerTodos()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.asignarCuotas(null, null, model);

        assertTrue(((List<?>) model.getAttribute("cuotasAsignadas")).isEmpty());
    }

    @Test
    void asignarCuotasAgrupaLasCuotasAsignadasPorJugador() {
        TemporadaDTO temporada = temporada(1L, "2025/2026");
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);
        JugadorDTO jugador = jugador(5L, "Alevin A");
        when(jugadorService.obtenerTodos()).thenReturn(List.of(jugador));
        when(equipoService.obtenerTodos()).thenReturn(List.of());
        when(equipoService.obtenerCategorias()).thenReturn(List.of());
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota", new BigDecimal("30"), 1);
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of(concepto));
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of(concepto));
        when(conceptoPagoService.obtenerTodos()).thenReturn(List.of(concepto));
        CuotaJugadorDTO cuota = cuota(100L, 5L, 10L, "2025-09", new BigDecimal("30"));
        when(cuotaJugadorService.obtenerTodos()).thenReturn(List.of(cuota));

        Model model = new ExtendedModelMap();
        controller.asignarCuotas(1L, null, model);

        assertEquals(1, ((List<?>) model.getAttribute("cuotasAsignadas")).size());
        assertEquals(1, ((List<?>) model.getAttribute("cuotasPorJugador")).size());
    }

    @Test
    void guardarCuotaRechazaSiNoHayMesesEnElRangoDeLaTemporada() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        TemporadaDTO temporada = temporada(1L, "9999/9999");
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuota(10L, "Alevin", null, 1L, null, null, null, null, redirect);

        assertEquals("Selecciona al menos un mes para asignar la cuota.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    @Test
    void guardarCuotaFiltraPorEquipoCuandoSeIndica() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Matricula", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada(1L, "2025/2026"));
        when(jugadorService.obtenerJugadoresPorCategoria("Alevin"))
                .thenReturn(List.of(jugador(1L, "Alevin A"), jugador(2L, "Alevin B")));
        when(cuotaJugadorService.guardarCuota(any())).thenReturn(true);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuota(10L, "Alevin", "Alevin A", 1L, null, null, null, null, redirect);

        org.mockito.ArgumentCaptor<CuotaJugadorDTO> captor = org.mockito.ArgumentCaptor.forClass(CuotaJugadorDTO.class);
        verify(cuotaJugadorService).guardarCuota(captor.capture());
        assertEquals(1L, captor.getValue().getJugadorId());
    }

    @Test
    void guardarCuotaJugadorRechazaSiNoHayMesesEnElRangoDeLaTemporada() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        when(conceptoPagoService.obtenerActivosPorTemporada(1L)).thenReturn(List.of(concepto));
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(jugador(5L, "Alevin A"));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada(1L, "9999/9999"));

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuotaJugador(5L, 1L, 10L, null, null, null, redirect);

        assertEquals("Selecciona un rango de meses válido.", redirect.getFlashAttributes().get("error"));
        verify(cuotaJugadorService, never()).guardarCuota(any());
    }

    @Test
    void guardarCuotaOmiteCuotasYaExistentesYCreaElResto() {
        ConceptoPagoDTO concepto = concepto(10L, 1L, "Cuota mensual", new BigDecimal("30.00"), 1);
        when(conceptoPagoService.obtenerPorId(10L)).thenReturn(concepto);
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada(1L, "2025/2026"));
        when(jugadorService.obtenerJugadoresPorCategoria("Alevin"))
                .thenReturn(List.of(jugador(1L, "Alevin A")));
        when(cuotaJugadorService.obtenerTodos())
                .thenReturn(List.of(cuota(1L, 1L, 10L, "2025-09", new BigDecimal("30"))));
        when(cuotaJugadorService.guardarCuota(any())).thenReturn(true);

        RedirectAttributes redirect = redirectAttributes();
        controller.guardarCuota(10L, "Alevin", null, 1L, null, null, null, null, redirect);

        verify(cuotaJugadorService, never()).guardarCuota(argThat(c -> "2025-09".equals(c.getPeriodo())));
        verify(cuotaJugadorService, org.mockito.Mockito.atLeastOnce())
                .guardarCuota(argThat(c -> !"2025-09".equals(c.getPeriodo())));
    }

    @Test
    void obtenerPeriodosTemporadaUsaElAnioDeFechaInicioSiElNombreNoTieneElPatron() throws Exception {
        TemporadaDTO temporada = temporada(1L, "Temporada actual");
        temporada.setFechaInicio(new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2025-09-01"));
        when(conceptoPagoService.obtenerPorTemporada(1L)).thenReturn(List.of());
        when(temporadaService.obtenerTodos()).thenReturn(List.of(temporada));
        when(temporadaService.obtenerPorId(1L)).thenReturn(temporada);

        Model model = new ExtendedModelMap();
        controller.conceptos(1L, model);

        assertEquals("2025-09", model.getAttribute("periodoInicioTemporada"));
        assertEquals("2026-06", model.getAttribute("periodoFinTemporada"));
    }

    @Test
    void asignarCuotaJugadorRedirigeConTemporadaYEquipo() {
        assertEquals("redirect:/admin/pagos/asignar-cuotas?temporadaId=1&equipo=Alevin%20A",
                controller.asignarCuotaJugador(1L, "Alevin%20A"));
    }

    @Test
    void registrarPagoConEdicionDeUnPagoExistenteDescuentaSuImporteActual() {
        PagoDTO pagoActual = new PagoDTO();
        pagoActual.setId(1L);
        pagoActual.setCuotaJugadorId(9L);
        pagoActual.setImporte(new BigDecimal("10"));
        when(pagoService.obtenerPorId(1L)).thenReturn(pagoActual);
        when(cuotaJugadorService.obtenerPorId(9L)).thenReturn(cuota(9L, 5L, 10L, null, new BigDecimal("20")));
        when(pagoService.obtenerTotalPagado(9L)).thenReturn(new BigDecimal("10"));

        RedirectAttributes redirect = redirectAttributes();
        String vista = controller.registrarPago(1L, 9L, new BigDecimal("20"), "2026-01-01", "Efectivo", null, null,
                redirect);

        assertEquals("redirect:/admin/pagos", vista);
        org.mockito.ArgumentCaptor<PagoDTO> captor = org.mockito.ArgumentCaptor.forClass(PagoDTO.class);
        verify(pagoService).guardarPago(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(new BigDecimal("20"), captor.getValue().getImporte());
    }

    @Test
    void marcarJugadorBecadoIndicaCuantasCuotasSeMarcaronComoPagadas() {
        when(jugadorService.obtenerJugadorPorId(5L)).thenReturn(jugador(5L, "Alevin A"));
        when(becadoService.marcarCuotasComoBecadas(5L)).thenReturn(3);
        RedirectAttributes redirect = redirectAttributes();

        controller.marcarJugadorBecado(5L, 1L, redirect);

        assertEquals("Se han marcado 3 cuotas como pagadas por beca.", redirect.getFlashAttributes().get("mensaje"));
    }
}

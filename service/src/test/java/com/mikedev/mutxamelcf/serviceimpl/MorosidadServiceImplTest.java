package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.CuotaMorosidad;
import com.mikedev.mutxamelcf.model.MorosidadDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

class MorosidadServiceImplTest {

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
	private static final long ANTERIOR = 1L;
	private static final long ANTERIOR_2 = 2L;
	private static final long ACTIVA = 3L;
	private static final long FUTURA = 4L;

	private TemporadaService temporadaService;
	private CuotaJugadorService cuotaJugadorService;
	private ConceptoPagoService conceptoPagoService;
	private PagoService pagoService;
	private MorosidadServiceImpl servicio;

	@BeforeEach
	void setUp() {
		temporadaService = mock(TemporadaService.class);
		cuotaJugadorService = mock(CuotaJugadorService.class);
		conceptoPagoService = mock(ConceptoPagoService.class);
		pagoService = mock(PagoService.class);

		TemporadaDTO t1 = temporada(ANTERIOR, "2024/2025", "2024-07-01", 0);
		TemporadaDTO t2 = temporada(ANTERIOR_2, "2025/2026", "2025-07-01", 0);
		TemporadaDTO activa = temporada(ACTIVA, "2026/2027", "2026-07-01", 1);
		TemporadaDTO futura = temporada(FUTURA, "2027/2028", "2027-07-01", 0);
		when(temporadaService.obtenerTodos()).thenReturn(List.of(t1, t2, activa, futura));
		when(temporadaService.obtenerTemporadaActiva()).thenReturn(activa);

		Clock reloj = Clock.fixed(LocalDate.of(2026, 10, 29).atTime(10, 0).atZone(MADRID).toInstant(), MADRID);
		servicio = new MorosidadServiceImpl(temporadaService, cuotaJugadorService, conceptoPagoService, pagoService,
				reloj);
	}

	private static TemporadaDTO temporada(long id, String nombre, String inicio, int activa) {
		TemporadaDTO t = new TemporadaDTO();
		t.setId(id);
		t.setNombre(nombre);
		t.setFechaInicio(Date.valueOf(inicio));
		t.setActiva(activa);
		return t;
	}

	private static CuotaMorosidad fila(long cuota, long temporadaId, String temporada, String jugador, String estado,
			String importe, String pagado) {
		return new CuotaMorosidad(cuota, temporadaId, temporada, 10L + cuota, jugador, "Alevín A", "Cuota mensual",
				"2024-10", new BigDecimal(importe), new BigDecimal(pagado), estado);
	}

	private void cuotaDeLaTemporada(long cuotaId, long temporadaId, String estado, String importe) {
		CuotaJugadorDTO cuota = new CuotaJugadorDTO();
		cuota.setId(cuotaId);
		cuota.setConceptoPagoId(500L + temporadaId);
		cuota.setEstado(estado);
		cuota.setImporte(new BigDecimal(importe));
		when(cuotaJugadorService.obtenerPorId(cuotaId)).thenReturn(cuota);
		ConceptoPagoDTO concepto = new ConceptoPagoDTO();
		concepto.setId(500L + temporadaId);
		concepto.setTemporadaId(temporadaId);
		when(conceptoPagoService.obtenerPorId(500L + temporadaId)).thenReturn(concepto);
	}

	// ---- listado --------------------------------------------------------------------------------

	@Test
	void lasTemporadasAnterioresSonLasPasadasLaMasRecientePrimero() {
		assertThat(servicio.temporadasAnteriores()).extracting(TemporadaDTO::getId).containsExactly(ANTERIOR_2, ANTERIOR);
	}

	@Test
	void sinTemporadaActivaSalenTodasLasNoActivas() {
		when(temporadaService.obtenerTemporadaActiva()).thenReturn(null);

		assertThat(servicio.temporadasAnteriores()).extracting(TemporadaDTO::getId)
				.containsExactlyInAnyOrder(ANTERIOR, ANTERIOR_2, FUTURA);
	}

	@Test
	void sacaLasCuotasSinPagarDeTodasLasAnterioresConSusTotales() {
		when(cuotaJugadorService.listarParaMorosidad(ANTERIOR, false)).thenReturn(List.of(
				fila(1, ANTERIOR, "2024/2025", "Ana Pérez", "PENDIENTE", "30", "0"),
				fila(2, ANTERIOR, "2024/2025", "Biel Gil", "PARCIAL", "30", "10"),
				fila(3, ANTERIOR, "2024/2025", "Carla Sanz", "INCOBRABLE", "30", "0")));
		when(cuotaJugadorService.listarParaMorosidad(ANTERIOR_2, false)).thenReturn(List.of(
				fila(4, ANTERIOR_2, "2025/2026", "Dani Ruiz", "PENDIENTE", "60", "0")));

		MorosidadDTO m = servicio.obtener(null, false);

		assertThat(m.filas()).extracting(MorosidadDTO.Fila::cuotaId).containsExactly(4L, 1L, 2L, 3L);
		assertThat(m.totales()).hasSize(2);
		MorosidadDTO.TotalTemporada t = m.totales().stream().filter(x -> x.temporadaId() == ANTERIOR).findFirst().get();
		assertThat(t.cuotas()).isEqualTo(3);
		assertThat(t.importe()).isEqualByComparingTo("90");
		assertThat(t.pagado()).isEqualByComparingTo("10");
		assertThat(t.pendiente()).isEqualByComparingTo("80");
		assertThat(t.pendienteIncobrable()).isEqualByComparingTo("30");
		verify(cuotaJugadorService, never()).listarParaMorosidad(ACTIVA, false);
		verify(cuotaJugadorService, never()).listarParaMorosidad(FUTURA, false);
	}

	@Test
	void elFiltroPorTemporadaSoloPideEsaYUnaNoAnteriorSeRechaza() {
		when(cuotaJugadorService.listarParaMorosidad(ANTERIOR, false)).thenReturn(List.of(
				fila(1, ANTERIOR, "2024/2025", "Ana Pérez", "PENDIENTE", "30", "0")));

		assertThat(servicio.obtener(ANTERIOR, false).filas()).hasSize(1);
		verify(cuotaJugadorService, never()).listarParaMorosidad(ANTERIOR_2, false);

		assertThatThrownBy(() -> servicio.obtener(ACTIVA, false)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servicio.obtener(99L, false)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void unaCuotaPagadaSeMuestraComoCobradaYNoSumaPendiente() {
		when(cuotaJugadorService.listarParaMorosidad(ANTERIOR, true)).thenReturn(List.of(
				fila(1, ANTERIOR, "2024/2025", "Ana Pérez", "PAGADO", "30", "30"),
				fila(2, ANTERIOR, "2024/2025", "Biel Gil", "PENDIENTE", "30", "0")));

		MorosidadDTO m = servicio.obtener(ANTERIOR, true);

		assertThat(m.filas().get(0).estadoVisible()).isEqualTo("Cobrada");
		assertThat(m.filas().get(0).cobrada()).isTrue();
		assertThat(m.filas().get(0).pendiente()).isEqualByComparingTo("0");
		assertThat(m.filas().get(1).estadoVisible()).isEqualTo("PENDIENTE");
		assertThat(m.totales().get(0).pendiente()).isEqualByComparingTo("30");
	}

	@Test
	void unaTemporadaSinCuotasNoAparece() {
		when(cuotaJugadorService.listarParaMorosidad(anyLong(), anyBoolean())).thenReturn(List.of());

		MorosidadDTO m = servicio.obtener(null, false);

		assertThat(m.filas()).isEmpty();
		assertThat(m.totales()).isEmpty();
	}

	// ---- cobrar ---------------------------------------------------------------------------------

	@Test
	void cobrarRegistraUnPagoPorElImportePendiente() {
		cuotaDeLaTemporada(7, ANTERIOR, "PARCIAL", "30");
		when(pagoService.obtenerTotalPagado(7L)).thenReturn(new BigDecimal("10"));

		servicio.cobrar(7L);

		ArgumentCaptor<PagoDTO> pago = ArgumentCaptor.forClass(PagoDTO.class);
		verify(pagoService).registrarPago(pago.capture());
		assertThat(pago.getValue().getCuotaJugadorId()).isEqualTo(7L);
		assertThat(pago.getValue().getImporte()).isEqualByComparingTo("20");
		assertThat(pago.getValue().getMetodoPago()).isEqualTo("Efectivo");
		assertThat(pago.getValue().getObservaciones()).isEqualTo("Cobrada desde Morosidad");
		assertThat(pago.getValue().getFechaPago()).isNotNull();
	}

	@Test
	void unaIncobrableTambienSePuedeCobrar() {
		cuotaDeLaTemporada(7, ANTERIOR, "INCOBRABLE", "30");
		when(pagoService.obtenerTotalPagado(7L)).thenReturn(BigDecimal.ZERO);

		servicio.cobrar(7L);

		ArgumentCaptor<PagoDTO> pago = ArgumentCaptor.forClass(PagoDTO.class);
		verify(pagoService).registrarPago(pago.capture());
		assertThat(pago.getValue().getImporte()).isEqualByComparingTo("30");
	}

	@Test
	void cobrarUnaYaCobradaOUnaDeLaTemporadaActivaSeRechaza() {
		cuotaDeLaTemporada(7, ANTERIOR, "PAGADO", "30");
		cuotaDeLaTemporada(8, ACTIVA, "PENDIENTE", "30");

		assertThatThrownBy(() -> servicio.cobrar(7L)).hasMessageContaining("ya está cobrada");
		assertThatThrownBy(() -> servicio.cobrar(8L)).hasMessageContaining("temporadas anteriores");
		assertThatThrownBy(() -> servicio.cobrar(99L)).hasMessageContaining("no existe");
		assertThatThrownBy(() -> servicio.cobrar(null)).isInstanceOf(IllegalArgumentException.class);
		verify(pagoService, never()).registrarPago(any());
	}

	@Test
	void siYaEstabaPagadaEnteraSoloSeCorrigeElEstado() {
		cuotaDeLaTemporada(7, ANTERIOR, "PARCIAL", "30");
		when(pagoService.obtenerTotalPagado(7L)).thenReturn(new BigDecimal("30"));

		servicio.cobrar(7L);

		verify(pagoService, never()).registrarPago(any());
		verify(cuotaJugadorService).actualizarEstado(7L);
	}

	// ---- incobrable -----------------------------------------------------------------------------

	@Test
	void marcarYDeshacerIncobrable() {
		cuotaDeLaTemporada(7, ANTERIOR, "PENDIENTE", "30");
		when(cuotaJugadorService.marcarIncobrable(7L)).thenReturn(1);
		when(cuotaJugadorService.deshacerIncobrable(7L)).thenReturn(1);

		servicio.marcarIncobrable(7L);
		servicio.deshacerIncobrable(7L);

		verify(cuotaJugadorService).marcarIncobrable(7L);
		verify(cuotaJugadorService).deshacerIncobrable(7L);
	}

	@Test
	void sinFilasCambiadasSeExplicaPorQue() {
		cuotaDeLaTemporada(7, ANTERIOR, "PAGADO", "30");
		when(cuotaJugadorService.marcarIncobrable(7L)).thenReturn(0);
		when(cuotaJugadorService.deshacerIncobrable(7L)).thenReturn(0);

		assertThatThrownBy(() -> servicio.marcarIncobrable(7L)).hasMessageContaining("pendientes o parciales");
		assertThatThrownBy(() -> servicio.deshacerIncobrable(7L)).hasMessageContaining("no está marcada");
	}

	@Test
	void lasAccionesNoTocanLasCuotasDeLaTemporadaActiva() {
		cuotaDeLaTemporada(8, ACTIVA, "PENDIENTE", "30");

		assertThatThrownBy(() -> servicio.marcarIncobrable(8L)).hasMessageContaining("temporadas anteriores");
		assertThatThrownBy(() -> servicio.deshacerIncobrable(8L)).hasMessageContaining("temporadas anteriores");
		verify(cuotaJugadorService, never()).marcarIncobrable(anyLong());
		verify(cuotaJugadorService, never()).deshacerIncobrable(anyLong());
	}

	// ---- CSV ------------------------------------------------------------------------------------

	@Test
	void exportaLasFilasEnCsvConPuntoYComaYComaDecimal() {
		when(cuotaJugadorService.listarParaMorosidad(ANTERIOR, false)).thenReturn(List.of(
				fila(1, ANTERIOR, "2024/2025", "Ana Pérez", "PARCIAL", "30", "12.5"),
				new CuotaMorosidad(2L, ANTERIOR, "2024/2025", 12L, "=HYPERLINK(\"x\")", "Alevín; A", "Matrícula", null,
						new BigDecimal("60"), BigDecimal.ZERO, "INCOBRABLE")));

		String csv = servicio.exportarCsv(ANTERIOR, false);

		String[] lineas = csv.split("\n");
		assertThat(lineas[0]).isEqualTo("Temporada;Jugador;Equipo;Concepto;Periodo;Importe;Pagado;Pendiente;Estado");
		assertThat(lineas[1]).isEqualTo("2024/2025;Ana Pérez;Alevín A;Cuota mensual;2024-10;30,00;12,50;17,50;PARCIAL");
		// Sin fórmulas ejecutables y con las comillas que pide el ";"
		assertThat(lineas[2]).isEqualTo("2024/2025;\"'=HYPERLINK(\"\"x\"\")\";\"Alevín; A\";Matrícula;;60,00;0,00;60,00;INCOBRABLE");
	}
}

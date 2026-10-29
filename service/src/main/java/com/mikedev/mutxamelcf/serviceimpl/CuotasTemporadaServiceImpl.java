package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.PeriodosCuotas;
import com.mikedev.mutxamelcf.service.TemporadaService;

@Service
public class CuotasTemporadaServiceImpl implements CuotasTemporadaService {

	private static final Logger logger = LoggerFactory.getLogger(CuotasTemporadaServiceImpl.class);

	private final TemporadaService temporadaService;
	private final ConceptoPagoService conceptoPagoService;
	private final CuotaJugadorService cuotaJugadorService;
	private final PagoService pagoService;
	private final Clock clock;

	public CuotasTemporadaServiceImpl(TemporadaService temporadaService, ConceptoPagoService conceptoPagoService,
			CuotaJugadorService cuotaJugadorService, PagoService pagoService, Clock clock) {
		this.temporadaService = temporadaService;
		this.conceptoPagoService = conceptoPagoService;
		this.cuotaJugadorService = cuotaJugadorService;
		this.pagoService = pagoService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public int crearCuotasDelJugador(Long jugadorId) {
		TemporadaDTO activa = temporadaService.obtenerTemporadaActiva();

		if (jugadorId == null || activa == null) {
			return 0;
		}

		String[] limites = PeriodosCuotas.periodosTemporada(activa.getNombre(), activa.getFechaInicio());
		List<String> mensuales = PeriodosCuotas.generarDesde(limites[0], limites[1], YearMonth.now(clock));

		// Lo que ya tiene (concepto y periodo), para no duplicar
		Set<String> existentes = new HashSet<>();
		for (CuotaJugadorDTO cuota : cuotaJugadorService.obtenerPorJugador(jugadorId)) {
			existentes.add(clave(cuota.getConceptoPagoId(), cuota.getPeriodo()));
		}

		int creadas = 0;

		for (ConceptoPagoDTO concepto : conceptoPagoService.obtenerActivosPorTemporada(activa.getId())) {
			if (concepto.getImporte() == null) {
				continue;
			}

			List<String> periodos = PeriodosCuotas.esCuotaMensual(concepto.getNombre()) ? mensuales
					: java.util.Collections.singletonList(null);

			for (String periodo : periodos) {
				if (!existentes.add(clave(concepto.getId(), periodo))) {
					continue;
				}

				CuotaJugadorDTO cuota = new CuotaJugadorDTO();
				cuota.setJugadorId(jugadorId);
				cuota.setConceptoPagoId(concepto.getId());
				cuota.setPeriodo(periodo);
				cuota.setImporte(concepto.getImporte());
				cuota.setEstado("PENDIENTE");

				if (cuotaJugadorService.guardarCuota(cuota)) {
					creadas++;
				}
			}
		}

		logger.info("Cuotas creadas con el reparto: jugadorId={}, temporada={}, creadas={}", jugadorId,
				activa.getNombre(), creadas);

		return creadas;
	}

	private static String clave(Long conceptoId, String periodo) {
		return conceptoId + "|" + (periodo == null ? "" : periodo);
	}

	@Override
	@Transactional
	public ResultadoRetiradaCuotas retirarCuotasDelJugador(Long jugadorId) {
		TemporadaDTO activa = temporadaService.obtenerTemporadaActiva();

		if (jugadorId == null || activa == null) {
			return new ResultadoRetiradaCuotas(0, 0, List.of());
		}

		Set<Long> conceptosDeLaTemporada = conceptoPagoService.obtenerPorTemporada(activa.getId()).stream()
				.map(ConceptoPagoDTO::getId).collect(Collectors.toSet());
		List<CuotaJugadorDTO> cuotas = cuotaJugadorService.obtenerPorJugador(jugadorId).stream()
				.filter(c -> c.getId() != null && conceptosDeLaTemporada.contains(c.getConceptoPagoId())).toList();

		if (cuotas.isEmpty()) {
			return new ResultadoRetiradaCuotas(0, 0, List.of());
		}

		Set<Long> conPagos = pagoService.obtenerPorCuotas(cuotas.stream().map(CuotaJugadorDTO::getId).toList())
				.stream().map(PagoDTO::getCuotaJugadorId).filter(Objects::nonNull).collect(Collectors.toSet());

		List<Long> aBorrar = new ArrayList<>();
		List<Long> conservadas = new ArrayList<>();

		for (CuotaJugadorDTO cuota : cuotas) {
			(conPagos.contains(cuota.getId()) ? conservadas : aBorrar).add(cuota.getId());
		}

		cuotaJugadorService.eliminarEnLote(aBorrar);

		logger.info("Cuotas retiradas con el reparto: jugadorId={}, borradas={}, conservadasConPagos={}", jugadorId,
				aBorrar.size(), conservadas.size());

		return new ResultadoRetiradaCuotas(aBorrar.size(), conservadas.size(), conservadas);
	}

}

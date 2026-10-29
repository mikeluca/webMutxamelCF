package com.mikedev.mutxamelcf.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.CuotaMorosidad;
import com.mikedev.mutxamelcf.model.MorosidadDTO;
import com.mikedev.mutxamelcf.model.MorosidadDTO.Fila;
import com.mikedev.mutxamelcf.model.MorosidadDTO.TotalTemporada;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.MorosidadService;
import com.mikedev.mutxamelcf.service.PagoService;
import com.mikedev.mutxamelcf.service.TemporadaService;

@Service
public class MorosidadServiceImpl implements MorosidadService {

	private static final Logger logger = LoggerFactory.getLogger(MorosidadServiceImpl.class);

	static final String METODO_COBRO = "Efectivo";
	static final String OBSERVACION_COBRO = "Cobrada desde Morosidad";

	private final TemporadaService temporadaService;
	private final CuotaJugadorService cuotaJugadorService;
	private final ConceptoPagoService conceptoPagoService;
	private final PagoService pagoService;
	private final Clock clock;

	public MorosidadServiceImpl(TemporadaService temporadaService, CuotaJugadorService cuotaJugadorService,
			ConceptoPagoService conceptoPagoService, PagoService pagoService, Clock clock) {
		this.temporadaService = temporadaService;
		this.cuotaJugadorService = cuotaJugadorService;
		this.conceptoPagoService = conceptoPagoService;
		this.pagoService = pagoService;
		this.clock = clock;
	}

	@Override
	public List<TemporadaDTO> temporadasAnteriores() {
		TemporadaDTO activa = temporadaService.obtenerTemporadaActiva();

		return temporadaService.obtenerTodos().stream()
				.filter(t -> t.getId() != null && (activa == null
						? !Integer.valueOf(1).equals(t.getActiva())
						: !t.getId().equals(activa.getId()) && t.getFechaInicio() != null
								&& activa.getFechaInicio() != null && t.getFechaInicio().before(activa.getFechaInicio())))
				.sorted(Comparator.comparing(TemporadaDTO::getFechaInicio,
						Comparator.nullsLast(Comparator.<Date>reverseOrder())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public MorosidadDTO obtener(Long temporadaId, boolean incluirCobradas) {
		List<TemporadaDTO> anteriores = temporadasAnteriores();
		List<TemporadaDTO> elegidas = anteriores;

		if (temporadaId != null) {
			elegidas = anteriores.stream().filter(t -> temporadaId.equals(t.getId())).toList();

			if (elegidas.isEmpty()) {
				throw new IllegalArgumentException("La temporada no es una temporada anterior a la activa.");
			}
		}

		List<Fila> filas = new ArrayList<>();
		List<TotalTemporada> totales = new ArrayList<>();

		for (TemporadaDTO temporada : elegidas) {
			List<Fila> deLaTemporada = cuotaJugadorService.listarParaMorosidad(temporada.getId(), incluirCobradas)
					.stream().map(MorosidadServiceImpl::aFila).toList();

			if (deLaTemporada.isEmpty()) {
				continue;
			}

			filas.addAll(deLaTemporada);
			totales.add(total(temporada, deLaTemporada));
		}

		return new MorosidadDTO(filas, totales);
	}

	private static Fila aFila(CuotaMorosidad c) {
		BigDecimal pagado = c.pagado() == null ? BigDecimal.ZERO : c.pagado();
		BigDecimal pendiente = "PAGADO".equals(c.estado()) ? BigDecimal.ZERO : c.pendiente();

		return new Fila(c.cuotaId(), c.temporadaId(), c.temporada(), c.jugadorId(), c.jugador(), c.equipo(),
				c.concepto(), c.periodo(), c.importe() == null ? BigDecimal.ZERO : c.importe(), pagado, pendiente,
				c.estado(), "PAGADO".equals(c.estado()) ? "Cobrada" : c.estado());
	}

	private static TotalTemporada total(TemporadaDTO temporada, List<Fila> filas) {
		BigDecimal importe = BigDecimal.ZERO;
		BigDecimal pagado = BigDecimal.ZERO;
		BigDecimal pendiente = BigDecimal.ZERO;
		BigDecimal incobrable = BigDecimal.ZERO;

		for (Fila f : filas) {
			importe = importe.add(f.importe());
			pagado = pagado.add(f.pagado());
			pendiente = pendiente.add(f.pendiente());

			if (f.incobrable()) {
				incobrable = incobrable.add(f.pendiente());
			}
		}

		return new TotalTemporada(temporada.getId(), temporada.getNombre(), filas.size(), importe, pagado, pendiente,
				incobrable);
	}

	// ---- acciones ---------------------------------------------------------------------------------

	/** La cuota tiene que existir y ser de una temporada anterior (las de la activa se gestionan en Pagos). */
	private CuotaJugadorDTO cuotaDeUnaTemporadaAnterior(Long cuotaId) {
		CuotaJugadorDTO cuota = cuotaId == null ? null : cuotaJugadorService.obtenerPorId(cuotaId);

		if (cuota == null) {
			throw new IllegalArgumentException("La cuota no existe.");
		}

		ConceptoPagoDTO concepto = cuota.getConceptoPagoId() == null ? null
				: conceptoPagoService.obtenerPorId(cuota.getConceptoPagoId());

		if (concepto == null
				|| temporadasAnteriores().stream().noneMatch(t -> t.getId().equals(concepto.getTemporadaId()))) {
			throw new IllegalArgumentException("Solo se gestionan aquí las cuotas de temporadas anteriores.");
		}

		return cuota;
	}

	@Override
	@Transactional
	public void cobrar(Long cuotaId) {
		CuotaJugadorDTO cuota = cuotaDeUnaTemporadaAnterior(cuotaId);

		if ("PAGADO".equals(cuota.getEstado())) {
			throw new IllegalArgumentException("La cuota ya está cobrada.");
		}

		BigDecimal pagado = pagoService.obtenerTotalPagado(cuotaId);
		BigDecimal pendiente = (cuota.getImporte() == null ? BigDecimal.ZERO : cuota.getImporte())
				.subtract(pagado == null ? BigDecimal.ZERO : pagado);

		if (pendiente.signum() <= 0) {
			// Ya estaba pagada entera aunque el estado no lo dijera: se deja coherente
			cuotaJugadorService.actualizarEstado(cuotaId);
			return;
		}

		PagoDTO pago = new PagoDTO();
		pago.setCuotaJugadorId(cuotaId);
		pago.setImporte(pendiente);
		pago.setFechaPago(Date.from(clock.instant()));
		pago.setMetodoPago(METODO_COBRO);
		pago.setObservaciones(OBSERVACION_COBRO);

		// Valida, guarda y actualiza el estado de la cuota de forma atómica, como en Pagos
		pagoService.registrarPago(pago);

		logger.info("Cuota cobrada desde Morosidad: cuotaId={}, importe={}", cuotaId, pendiente);
	}

	@Override
	@Transactional
	public void marcarIncobrable(Long cuotaId) {
		cuotaDeUnaTemporadaAnterior(cuotaId);

		if (cuotaJugadorService.marcarIncobrable(cuotaId) == 0) {
			throw new IllegalArgumentException(
					"Solo se pueden marcar como incobrables las cuotas pendientes o parciales.");
		}
	}

	@Override
	@Transactional
	public void deshacerIncobrable(Long cuotaId) {
		cuotaDeUnaTemporadaAnterior(cuotaId);

		if (cuotaJugadorService.deshacerIncobrable(cuotaId) == 0) {
			throw new IllegalArgumentException("La cuota no está marcada como incobrable.");
		}
	}

	// ---- CSV --------------------------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public String exportarCsv(Long temporadaId, boolean incluirCobradas) {
		StringBuilder csv = new StringBuilder(
				"Temporada;Jugador;Equipo;Concepto;Periodo;Importe;Pagado;Pendiente;Estado\n");

		for (Fila f : obtener(temporadaId, incluirCobradas).filas()) {
			csv.append(String.join(";", campo(f.temporada()), campo(f.jugador()), campo(f.equipo()),
					campo(f.concepto()), campo(f.periodo()), importe(f.importe()), importe(f.pagado()),
					importe(f.pendiente()), campo(f.estadoVisible()))).append('\n');
		}

		return csv.toString();
	}

	private static String importe(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	/** Entre comillas si lleva ";", comillas o saltos de línea; y neutraliza las fórmulas de las hojas de cálculo. */
	private static String campo(String valor) {
		if (valor == null) {
			return "";
		}

		String limpio = valor;

		if (!limpio.isEmpty() && "=+-@".indexOf(limpio.charAt(0)) >= 0) {
			limpio = "'" + limpio;
		}

		if (limpio.contains(";") || limpio.contains("\"") || limpio.contains("\n") || limpio.contains("\r")) {
			return "\"" + limpio.replace("\"", "\"\"") + "\"";
		}

		return limpio;
	}
}

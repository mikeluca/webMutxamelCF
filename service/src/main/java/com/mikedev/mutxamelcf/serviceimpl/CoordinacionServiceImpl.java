package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CoordinacionDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.EquipoCoordinacion;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse.Descanso;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse.PartidoSinConvocatoria;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse.ResultadoPendiente;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse.Resumen;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse.SinPartido;
import com.mikedev.mutxamelcf.model.PartidoCoordinacion;
import com.mikedev.mutxamelcf.model.SeguimientoJugador;
import com.mikedev.mutxamelcf.model.SeguimientoJugadoresResponse;
import com.mikedev.mutxamelcf.model.SeguimientoJugadoresResponse.Detalle;
import com.mikedev.mutxamelcf.model.SeguimientoJugadoresResponse.Incidencia;
import com.mikedev.mutxamelcf.model.SeguimientoJugadoresResponse.Jugador;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CoordinacionService;

@Service
public class CoordinacionServiceImpl implements CoordinacionService {

	/** Cuánto hacia atrás se buscan resultados pendientes. */
	static final int DIAS_RESULTADOS_PENDIENTES = 10;

	static final String PERIODO_DIAS = "30d";
	static final String PERIODO_TEMPORADA = "temporada";

	private final CoordinacionDao coordinacionDao;
	private final EquipoGestionDao equipoGestionDao;
	private final TemporadaDao temporadaDao;
	private final Clock clock;
	private final int umbralFaltas;
	private final int umbralMalComportamiento;
	private final int dias;

	public CoordinacionServiceImpl(
			CoordinacionDao coordinacionDao,
			EquipoGestionDao equipoGestionDao,
			TemporadaDao temporadaDao,
			Clock clock,
			@Value("${seguimiento.faltas.umbral:3}") int umbralFaltas,
			@Value("${seguimiento.mal-comportamiento.umbral:2}") int umbralMalComportamiento,
			@Value("${seguimiento.dias:30}") int dias) {

		this.coordinacionDao = coordinacionDao;
		this.equipoGestionDao = equipoGestionDao;
		this.temporadaDao = temporadaDao;
		this.clock = clock;
		this.umbralFaltas = Math.max(1, umbralFaltas);
		this.umbralMalComportamiento = Math.max(1, umbralMalComportamiento);
		this.dias = Math.max(1, dias);
	}

	/**
	 * El viernes del fin de semana de referencia: de lunes a jueves, el próximo
	 * viernes-domingo; de viernes a domingo, el actual.
	 */
	static LocalDate viernesDeReferencia(LocalDate hoy) {

		return hoy.getDayOfWeek().getValue() >= DayOfWeek.FRIDAY.getValue()
				? hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
				: hoy.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
	}

	// ---- fin de semana -----------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public FinDeSemanaResponse obtenerFinDeSemana(Long usuarioAppId, LocalDate fecha) {

		validarAcceso(usuarioAppId);

		LocalDateTime ahora = LocalDateTime.now(clock);
		LocalDate hoy = ahora.toLocalDate();

		LocalDate viernes = viernesDeReferencia(fecha != null ? fecha : hoy);
		LocalDate domingo = viernes.plusDays(2);

		LocalDate desdePendientes = hoy.minusDays(DIAS_RESULTADOS_PENDIENTES);

		List<PartidoCoordinacion> delFinDeSemana = coordinacionDao.partidosEntre(viernes, domingo);

		List<PartidoSinConvocatoria> sinConvocatoria = delFinDeSemana.stream()
				.filter(p -> !p.cancelado() && !p.conConvocatoria())
				.map(p -> new PartidoSinConvocatoria(
						p.equipoId(), p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora(), p.local()))
				.toList();

		List<ResultadoPendiente> pendientes = coordinacionDao.partidosEntre(desdePendientes, hoy).stream()
				.filter(p -> !p.cancelado()
						&& !CriterioResultadoPartido.tieneResultado(p.golesFavor(), p.resultado())
						&& CriterioResultadoPartido.haTerminado(p.dia(), p.hora(), ahora))
				.map(p -> new ResultadoPendiente(
						p.equipoId(), p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora(),
						(int) ChronoUnit.DAYS.between(p.dia(), hoy)))
				.toList();

		List<EquipoCoordinacion> descansos = coordinacionDao.descansosEntre(viernes, domingo);

		List<Descanso> descansan = descansos.stream()
				.map(d -> new Descanso(d.equipoId(), d.equipo(), d.jornada()))
				.toList();

		// Un partido cancelado sigue siendo un partido del calendario: no es una jornada sin importar.
		Set<Long> conActividad = new HashSet<>();
		delFinDeSemana.forEach(p -> conActividad.add(p.equipoId()));
		descansos.forEach(d -> conActividad.add(d.equipoId()));

		List<SinPartido> sinPartido = coordinacionDao.equiposConRelacionFfcv().stream()
				.filter(e -> !conActividad.contains(e.equipoId()))
				.map(e -> new SinPartido(e.equipoId(), e.equipo()))
				.toList();

		return new FinDeSemanaResponse(
				viernes, domingo, sinConvocatoria, pendientes, descansan, sinPartido,
				new Resumen(sinConvocatoria.size(), pendientes.size(), descansan.size(), sinPartido.size(),
						coordinacionDao.ultimaImportacionFfcv()));
	}

	// ---- seguimiento de jugadores -------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public SeguimientoJugadoresResponse obtenerSeguimientoJugadores(Long usuarioAppId, String periodo) {

		validarAcceso(usuarioAppId);

		String clave = normalizarPeriodo(periodo);
		LocalDate[] rango = rango(clave);

		List<Jugador> jugadores = new ArrayList<>();

		for (SeguimientoJugador fila : coordinacionDao
				.seguimientoJugadores(rango[0], rango[1], umbralFaltas, umbralMalComportamiento)) {
			jugadores.add(aJugador(fila));
		}

		jugadores.sort(Comparator
				.comparingInt((Jugador j) -> j.faltas() + j.malComportamiento()).reversed()
				.thenComparing(Jugador::nombre, String.CASE_INSENSITIVE_ORDER));

		return new SeguimientoJugadoresResponse(clave, dias, rango[0], rango[1], jugadores);
	}

	@Override
	@Transactional(readOnly = true)
	public Detalle obtenerDetalleSeguimiento(Long usuarioAppId, Long jugadorId, String periodo) {

		validarAcceso(usuarioAppId);

		if (jugadorId == null || jugadorId <= 0) {
			throw new IllegalArgumentException("El jugador no es válido");
		}

		String clave = normalizarPeriodo(periodo);
		LocalDate[] rango = rango(clave);

		SeguimientoJugador fila = coordinacionDao.seguimientoDeJugador(jugadorId, rango[0], rango[1]);

		if (fila == null) {
			throw new IllegalArgumentException("El jugador no existe");
		}

		List<Incidencia> incidencias = coordinacionDao.incidenciasDeJugador(jugadorId, rango[0], rango[1]).stream()
				.map(i -> new Incidencia(i.fecha(), i.estado()))
				.toList();

		return new Detalle(clave, dias, rango[0], rango[1], aJugador(fila), incidencias);
	}

	private Jugador aJugador(SeguimientoJugador fila) {

		boolean faltas = fila.faltas() >= umbralFaltas;
		boolean comportamiento = fila.malComportamiento() >= umbralMalComportamiento;

		String motivo = faltas && comportamiento
				? SeguimientoJugadoresResponse.MOTIVO_AMBOS
				: faltas ? SeguimientoJugadoresResponse.MOTIVO_FALTAS
						: comportamiento ? SeguimientoJugadoresResponse.MOTIVO_COMPORTAMIENTO : null;

		return new Jugador(fila.jugadorId(), fila.nombre(), fila.equipo(), fila.faltas(), fila.faltasJustificadas(),
				fila.malComportamiento(), fila.retrasos(), fila.ultimaIncidencia(), motivo);
	}

	private static String normalizarPeriodo(String periodo) {

		if (periodo == null || periodo.isBlank() || PERIODO_DIAS.equalsIgnoreCase(periodo.trim())) {
			return PERIODO_DIAS;
		}

		if (PERIODO_TEMPORADA.equalsIgnoreCase(periodo.trim())) {
			return PERIODO_TEMPORADA;
		}

		throw new IllegalArgumentException("El periodo debe ser 30d o temporada");
	}

	/** [desde, hasta]: solo entrenamientos pasados (hasta ayer). Sin temporada activa, un rango vacío. */
	private LocalDate[] rango(String periodo) {

		LocalDate hoy = LocalDate.now(clock);
		LocalDate ayer = hoy.minusDays(1);

		if (PERIODO_DIAS.equals(periodo)) {
			return new LocalDate[] { hoy.minusDays(dias), ayer };
		}

		Temporada temporada = temporadaDao.obtenerTemporadaActiva();

		if (temporada == null || temporada.getFechaInicio() == null) {
			return new LocalDate[] { hoy, ayer };
		}

		return new LocalDate[] { aLocalDate(temporada.getFechaInicio()), ayer };
	}

	private void validarAcceso(Long usuarioAppId) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (!equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
			throw new SecurityException("Solo coordinación puede consultar este panel");
		}
	}

	private static LocalDate aLocalDate(java.util.Date fecha) {

		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}

		return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}
}

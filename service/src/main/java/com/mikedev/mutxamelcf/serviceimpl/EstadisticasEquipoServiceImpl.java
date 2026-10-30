package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDiaDeJugador;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse.Asistente;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse.AsistenciaJugador;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse.Entrenamientos;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse.Goleador;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse.Partidos;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.PartidosEquipo;
import com.mikedev.mutxamelcf.model.TarjetasEquipoResponse;
import com.mikedev.mutxamelcf.model.TarjetasJugadorResponse;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.EstadisticasEquipoService;

@Service
public class EstadisticasEquipoServiceImpl implements EstadisticasEquipoService {

	static final int TOP = 10;

	private static final LocalDate SIN_INICIO = LocalDate.of(1900, 1, 1);

	private static final Set<String> ESTADOS_ASISTIDOS = Set.of("PRESENTE", "RETRASO", "MAL_COMPORTAMIENTO");

	private final ResumenTemporadaDao resumenTemporadaDao;
	private final TemporadaDao temporadaDao;
	private final EquipoGestionDao equipoGestionDao;
	private final ReglaSancionAmarillas reglaSancion;
	private final Clock clock;

	public EstadisticasEquipoServiceImpl(
			ResumenTemporadaDao resumenTemporadaDao,
			TemporadaDao temporadaDao,
			EquipoGestionDao equipoGestionDao,
			ReglaSancionAmarillas reglaSancion,
			Clock clock) {

		this.resumenTemporadaDao = resumenTemporadaDao;
		this.temporadaDao = temporadaDao;
		this.equipoGestionDao = equipoGestionDao;
		this.reglaSancion = reglaSancion;
		this.clock = clock;
	}

	/** Las fechas de la temporada activa; null si no hay ninguna. */
	private record Rango(LocalDate desde, LocalDate fin) {
	}

	@Override
	@Transactional(readOnly = true)
	public TarjetasEquipoResponse obtenerTarjetas(Long usuarioAppId, Long equipoId) {

		validarAcceso(usuarioAppId, equipoId);

		if (!equipoGestionDao.equipoAdmiteEstadisticas(equipoId)) {
			return new TarjetasEquipoResponse(false, List.of());
		}

		LocalDate hoy = LocalDate.now(clock);
		Rango temporada = temporadaActiva();

		List<TarjetasJugadorResponse> jugadores = resumenTemporadaDao
				.estadisticasJugadoresDelEquipo(equipoId, desdePartidos(temporada), hastaPartidos(temporada, hoy), hoy)
				.stream()
				.map(j -> new TarjetasJugadorResponse(
						j.jugadorId(), j.nombre(), j.dorsal(), j.amarillas(), j.rojas(),
						reglaSancion.estado(j.amarillas()), j.invitado()))
				.sorted(Comparator
						.comparingInt(TarjetasJugadorResponse::amarillas).reversed()
						.thenComparing(Comparator.comparingInt(TarjetasJugadorResponse::rojas).reversed())
						.thenComparing(TarjetasJugadorResponse::nombre, String.CASE_INSENSITIVE_ORDER))
				.toList();

		return new TarjetasEquipoResponse(true, jugadores);
	}

	@Override
	@Transactional(readOnly = true)
	public EstadisticasEquipoResponse obtenerEstadisticas(Long usuarioAppId, Long equipoId) {

		validarAcceso(usuarioAppId, equipoId);

		LocalDate hoy = LocalDate.now(clock);
		Rango temporada = temporadaActiva();

		LocalDate desde = desdePartidos(temporada);
		LocalDate hasta = hastaPartidos(temporada, hoy);

		boolean admiteEstadisticas = equipoGestionDao.equipoAdmiteEstadisticas(equipoId);

		// Sin estadísticas de jugador no hay goleadores ni asistentes; partidos y entrenamientos igual.
		List<EstadisticasJugadorEquipo> jugadores = admiteEstadisticas
				? resumenTemporadaDao.estadisticasJugadoresDelEquipo(equipoId, desde, hasta, hoy)
				: List.of();

		List<Goleador> goleadores = jugadores.stream()
				.filter(j -> j.goles() > 0)
				.sorted(Comparator
						.comparingInt(EstadisticasJugadorEquipo::goles).reversed()
						.thenComparing(Comparator.comparingInt(EstadisticasJugadorEquipo::asistencias).reversed())
						.thenComparing(EstadisticasJugadorEquipo::nombre, String.CASE_INSENSITIVE_ORDER))
				.limit(TOP)
				.map(j -> new Goleador(j.jugadorId(), j.nombre(), j.dorsal(), j.goles(), j.asistencias(),
						j.partidos(), j.invitado()))
				.toList();

		List<Asistente> asistentes = jugadores.stream()
				.filter(j -> j.asistencias() > 0)
				.sorted(Comparator
						.comparingInt(EstadisticasJugadorEquipo::asistencias).reversed()
						.thenComparing(Comparator.comparingInt(EstadisticasJugadorEquipo::goles).reversed())
						.thenComparing(EstadisticasJugadorEquipo::nombre, String.CASE_INSENSITIVE_ORDER))
				.limit(TOP)
				.map(j -> new Asistente(j.jugadorId(), j.nombre(), j.dorsal(), j.asistencias(), j.goles(),
						j.partidos(), j.invitado()))
				.toList();

		PartidosEquipo partidos = resumenTemporadaDao.partidosDelEquipo(equipoId, desde, hasta);

		return new EstadisticasEquipoResponse(
				goleadores,
				asistentes,
				entrenamientos(equipoId, temporada, hoy),
				new Partidos(partidos.jugados(), partidos.ganados(), partidos.empatados(), partidos.perdidos(),
						partidos.golesFavor(), partidos.golesContra()),
				admiteEstadisticas);
	}

	/*
	 * Solo cuentan los entrenamientos pasados, como en ResumenTemporadaServiceImpl: los
	 * de días anteriores a hoy enteros; los de hoy, solo si su hora ya ha pasado (sin
	 * hora conocida, hasta que acabe el día). Los futuros ya tienen su fila de
	 * asistencia (PRESENTE por defecto) y no deben subir el porcentaje.
	 */
	private Entrenamientos entrenamientos(Long equipoId, Rango temporada, LocalDate hoy) {

		if (temporada == null) {
			return new Entrenamientos(0, 0, 0, null, List.of());
		}

		LocalDate ayer = hoy.minusDays(1);
		LocalDate hastaEntrenamientos = temporada.fin() != null && temporada.fin().isBefore(ayer)
				? temporada.fin()
				: ayer;

		List<AsistenciaJugadorEquipo> base = resumenTemporadaDao
				.asistenciaEntrenamientosDelEquipo(equipoId, temporada.desde(), hastaEntrenamientos);

		int sesiones = resumenTemporadaDao
				.contarEntrenamientosDelEquipo(equipoId, temporada.desde(), hastaEntrenamientos, false);
		int revisadas = resumenTemporadaDao
				.contarEntrenamientosDelEquipo(equipoId, temporada.desde(), hastaEntrenamientos, true);

		Map<Long, int[]> totales = new HashMap<>();   // [total, asistidos]

		for (AsistenciaJugadorEquipo jugador : base) {
			totales.put(jugador.jugadorId(), new int[] { jugador.total(), jugador.asistidos() });
		}

		boolean hoyEnTemporada = !hoy.isBefore(temporada.desde()) && (temporada.fin() == null || !hoy.isAfter(temporada.fin()));

		if (hoyEnTemporada) {

			LocalTime ahora = LocalTime.now(clock);
			Set<Long> sesionesDeHoy = new HashSet<>();
			Set<Long> revisadasDeHoy = new HashSet<>();

			for (EntrenamientoDelDiaDeJugador fila : resumenTemporadaDao.entrenamientosDelDiaDelEquipo(equipoId, hoy)) {

				LocalTime hora = HoraTexto.leer(fila.hora());

				if (hora == null || hora.isAfter(ahora)) {
					continue;
				}

				sesionesDeHoy.add(fila.entrenamientoId());

				// Sin la lista revisada las asistencias son las de por defecto (PRESENTE):
				// cuenta como sesión, pero no en los porcentajes.
				if (!fila.listaRevisada()) {
					continue;
				}

				revisadasDeHoy.add(fila.entrenamientoId());

				int[] total = totales.get(fila.jugadorId());

				if (total != null) {
					total[0]++;

					if (ESTADOS_ASISTIDOS.contains(fila.estado())) {
						total[1]++;
					}
				}
			}

			sesiones += sesionesDeHoy.size();
			revisadas += revisadasDeHoy.size();
		}

		List<AsistenciaJugador> porJugador = new ArrayList<>();
		int sumaTotal = 0;
		int sumaAsistidos = 0;

		for (AsistenciaJugadorEquipo jugador : base) {

			int[] total = totales.get(jugador.jugadorId());

			sumaTotal += total[0];
			sumaAsistidos += total[1];

			porJugador.add(new AsistenciaJugador(
					jugador.jugadorId(), jugador.nombre(), total[1], total[0], porcentaje(total[1], total[0])));
		}

		porJugador.sort(Comparator
				.comparing(AsistenciaJugador::porcentaje, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AsistenciaJugador::nombre, String.CASE_INSENSITIVE_ORDER));

		return new Entrenamientos(sesiones, revisadas, sesiones - revisadas,
				porcentaje(sumaAsistidos, sumaTotal), porJugador);
	}

	private static Integer porcentaje(int asistidos, int total) {
		return total == 0 ? null : (int) Math.round(asistidos * 100.0 / total);
	}

	private void validarAcceso(Long usuarioAppId, Long equipoId) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (equipoId == null || equipoId <= 0) {
			throw new IllegalArgumentException("El equipo no es válido");
		}

		if (!equipoGestionDao.existeEquipo(equipoId)) {
			throw new IllegalArgumentException("El equipo no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
			throw new SecurityException("No tienes permiso para consultar este equipo");
		}
	}

	private Rango temporadaActiva() {

		Temporada temporada = temporadaDao.obtenerTemporadaActiva();

		if (temporada == null) {
			return null;
		}

		return new Rango(
				temporada.getFechaInicio() != null ? aLocalDate(temporada.getFechaInicio()) : SIN_INICIO,
				temporada.getFechaFin() != null ? aLocalDate(temporada.getFechaFin()) : null);
	}

	/** Sin temporada activa el rango queda vacío (desde > hasta): nadie tiene datos. */
	private static LocalDate desdePartidos(Rango temporada) {
		return temporada == null ? LocalDate.of(2100, 1, 1) : temporada.desde();
	}

	private static LocalDate hastaPartidos(Rango temporada, LocalDate hoy) {

		if (temporada == null) {
			return SIN_INICIO;
		}

		return temporada.fin() != null ? temporada.fin() : hoy.plusYears(1);
	}

	private static LocalDate aLocalDate(Date fecha) {

		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}

		return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}
}

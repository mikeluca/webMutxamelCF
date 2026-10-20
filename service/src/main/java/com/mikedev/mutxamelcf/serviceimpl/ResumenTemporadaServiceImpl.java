package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.AsistenciaEntrenamientosJugador;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDia;
import com.mikedev.mutxamelcf.model.EstadisticasPartidosJugador;
import com.mikedev.mutxamelcf.model.RespuestasConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ResumenRespuestasConvocatoria;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse.EntrenamientosResumen;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse.PartidosResumen;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse.TemporadaResumen;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.ResumenTemporadaService;

@Service
public class ResumenTemporadaServiceImpl implements ResumenTemporadaService {

	private static final Logger logger = LoggerFactory.getLogger(ResumenTemporadaServiceImpl.class);

	private static final LocalDate SIN_INICIO = LocalDate.of(1900, 1, 1);

	private final ResumenTemporadaDao resumenTemporadaDao;
	private final TemporadaDao temporadaDao;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final EquipoGestionDao equipoGestionDao;
	private final Clock clock;

	public ResumenTemporadaServiceImpl(
			ResumenTemporadaDao resumenTemporadaDao,
			TemporadaDao temporadaDao,
			UsuarioAppVinculoDao usuarioAppVinculoDao,
			EquipoGestionDao equipoGestionDao,
			Clock clock) {

		this.resumenTemporadaDao = resumenTemporadaDao;
		this.temporadaDao = temporadaDao;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.equipoGestionDao = equipoGestionDao;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public ResumenTemporadaResponse obtenerResumen(Long usuarioAppId, Long jugadorId) {
		logger.debug("Inicio obtenerResumen: jugadorId={}", jugadorId);

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}

		if (jugadorId == null || jugadorId <= 0) {
			throw new IllegalArgumentException("El jugador no es válido");
		}

		validarAcceso(usuarioAppId, jugadorId);

		Temporada temporada = temporadaDao.obtenerTemporadaActiva();

		if (temporada == null) {
			return vacio();
		}

		LocalDate hoy = LocalDate.now(clock);
		LocalDate desde = temporada.getFechaInicio() != null ? aLocalDate(temporada.getFechaInicio()) : SIN_INICIO;
		LocalDate finTemporada = temporada.getFechaFin() != null ? aLocalDate(temporada.getFechaFin()) : null;

		/*
		 * Los entrenamientos futuros ya tienen su fila de asistencia (todos
		 * PRESENTE por defecto desde que se crea la sesión): contarlos haría
		 * subir el porcentaje antes de que se celebren. Los días anteriores a
		 * hoy cuentan enteros; el de hoy, solo los entrenamientos cuya hora ya
		 * ha pasado (ver entrenamientosDeHoy).
		 */
		LocalDate ayer = hoy.minusDays(1);
		LocalDate hastaEntrenamientos = finTemporada != null && finTemporada.isBefore(ayer) ? finTemporada : ayer;
		LocalDate hasta = finTemporada != null ? finTemporada : hoy.plusYears(1);

		EstadisticasPartidosJugador partidos = resumenTemporadaDao.estadisticasPartidos(jugadorId, desde, hasta, hoy);
		AsistenciaEntrenamientosJugador asistencia = resumenTemporadaDao.asistenciaEntrenamientos(
				jugadorId, desde, hastaEntrenamientos);

		if (!hoy.isBefore(desde) && (finTemporada == null || !hoy.isAfter(finTemporada))) {
			asistencia = sumar(asistencia, entrenamientosDeHoy(jugadorId, hoy));
		}
		RespuestasConvocatoriaJugador respuestas = resumenTemporadaDao.respuestasConvocatoria(jugadorId, desde, hasta);

		logger.debug("Fin obtenerResumen: jugadorId={}", jugadorId);

		return new ResumenTemporadaResponse(
				new TemporadaResumen(temporada.getNombre(),
						temporada.getFechaInicio() != null ? desde : null, finTemporada),
				new PartidosResumen(partidos.convocado(), partidos.goles(), partidos.asistencias(),
						partidos.amarillas(), partidos.rojas()),
				entrenamientos(asistencia),
				new ResumenRespuestasConvocatoria(respuestas.va(), respuestas.noPuede(), respuestas.sinResponder()));
	}

	/*
	 * Los entrenamientos de hoy cuya hora ya ha pasado (hora de Madrid). Si no
	 * se sabe la hora (sin sesión, sin hora o ilegible), no se cuenta: ese
	 * entrenamiento entra cuando acabe el día, como el resto hasta ayer.
	 */
	private List<EntrenamientoDelDia> entrenamientosDeHoy(Long jugadorId, LocalDate hoy) {

		LocalTime ahora = LocalTime.now(clock);

		return resumenTemporadaDao.entrenamientosDelDia(jugadorId, hoy).stream()
				.filter(entrenamiento -> {
					LocalTime hora = HoraTexto.leer(entrenamiento.hora());
					return hora != null && !hora.isAfter(ahora);
				})
				.toList();
	}

	private static AsistenciaEntrenamientosJugador sumar(
			AsistenciaEntrenamientosJugador base, List<EntrenamientoDelDia> hoy) {

		int presentes = base.presentes();
		int retrasos = base.retrasos();
		int malComportamiento = base.malComportamiento();
		int faltas = base.faltas();
		int justificadas = base.justificadas();

		for (EntrenamientoDelDia entrenamiento : hoy) {
			String estado = entrenamiento.estado() == null ? "" : entrenamiento.estado();

			switch (estado) {
				case "PRESENTE" -> presentes++;
				case "RETRASO" -> retrasos++;
				case "MAL_COMPORTAMIENTO" -> malComportamiento++;
				case "FALTA" -> faltas++;
				case "FALTA_JUSTIFICADA" -> justificadas++;
				default -> {
					// estado desconocido: cuenta en el total pero en ningún grupo
				}
			}
		}

		return new AsistenciaEntrenamientosJugador(
				base.total() + hoy.size(), presentes, retrasos, malComportamiento, faltas, justificadas);
	}

	/*
	 * El propio jugador o un familiar suyo (vínculo), o el staff que gestiona
	 * su equipo. La comprobación es del servidor: no depende de la app.
	 */
	private void validarAcceso(Long usuarioAppId, Long jugadorId) {

		if (usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
			return;
		}

		Long equipoId = equipoGestionDao.obtenerEquipoIdDeJugador(jugadorId);

		if (equipoId != null && equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
			return;
		}

		throw new SecurityException("No tienes permiso para consultar a este jugador");
	}

	static EntrenamientosResumen entrenamientos(AsistenciaEntrenamientosJugador asistencia) {

		int asistidos = asistencia.asistidos();

		Integer porcentaje = asistencia.total() == 0
				? null
				: (int) Math.round(asistidos * 100.0 / asistencia.total());

		return new EntrenamientosResumen(
				asistencia.total(), asistidos, asistencia.faltas(), asistencia.justificadas(), porcentaje);
	}

	private static ResumenTemporadaResponse vacio() {
		return new ResumenTemporadaResponse(
				null,
				new PartidosResumen(0, 0, 0, 0, 0),
				new EntrenamientosResumen(0, 0, 0, 0, null),
				new ResumenRespuestasConvocatoria(0, 0, 0));
	}

	private static LocalDate aLocalDate(Date fecha) {

		if (fecha instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}

		return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}

}

package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.MiConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.RespuestaConvocatoriaRequest;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.RespuestaConvocatoriaService;

@Service
public class RespuestaConvocatoriaServiceImpl implements RespuestaConvocatoriaService {

	private static final Logger logger = LoggerFactory.getLogger(RespuestaConvocatoriaServiceImpl.class);

	static final String VA = "VA";
	static final String NO_PUEDE = "NO_PUEDE";

	static final int MOTIVO_MINIMO = 3;
	static final int MOTIVO_MAXIMO = 500;

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

	private final ConvocatoriaDao convocatoriaDao;
	private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
	private final PartidoDao partidoDao;
	private final EquipoGestionDao equipoGestionDao;
	private final UsuarioAppVinculoDao usuarioAppVinculoDao;
	private final PerfilAppDao perfilAppDao;
	private final ComunicacionService comunicacionService;
	private final Clock clock;

	public RespuestaConvocatoriaServiceImpl(
			ConvocatoriaDao convocatoriaDao,
			ConvocatoriaJugadorDao convocatoriaJugadorDao,
			PartidoDao partidoDao,
			EquipoGestionDao equipoGestionDao,
			UsuarioAppVinculoDao usuarioAppVinculoDao,
			PerfilAppDao perfilAppDao,
			ComunicacionService comunicacionService,
			Clock clock) {

		this.convocatoriaDao = convocatoriaDao;
		this.convocatoriaJugadorDao = convocatoriaJugadorDao;
		this.partidoDao = partidoDao;
		this.equipoGestionDao = equipoGestionDao;
		this.usuarioAppVinculoDao = usuarioAppVinculoDao;
		this.perfilAppDao = perfilAppDao;
		this.comunicacionService = comunicacionService;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<MiConvocatoriaResponse> obtenerMisConvocatorias(Long usuarioAppId) {
		logger.debug("Inicio obtenerMisConvocatorias: usuarioAppId={}", usuarioAppId);

		validarUsuario(usuarioAppId);

		LocalDate hoy = LocalDate.now(clock);
		LocalDate hasta = hoy.plusDays(DIAS_HACIA_DELANTE);

		List<MiConvocatoriaResponse> respuesta = new ArrayList<>();

		for (Jugador jugador : perfilAppDao.obtenerJugadoresPorUsuario(usuarioAppId.intValue())) {

			for (ConvocatoriaJugador convocado : convocatoriaJugadorDao.obtenerPorJugadorYRango(
					jugador.getId(), hoy, hasta)) {

				Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocado.getConvocatoriaId());

				if (convocatoria == null) {
					continue;
				}

				Partido partido = partidoDao.obtenerPorId(convocatoria.getPartidoId());

				respuesta.add(construir(convocatoria, partido, convocado, nombreCompleto(jugador)));
			}
		}

		respuesta.sort(Comparator
				.comparing(MiConvocatoriaResponse::fechaPartido, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(MiConvocatoriaResponse::horaPartido, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(MiConvocatoriaResponse::jugador, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

		logger.debug("Fin obtenerMisConvocatorias: total={}", respuesta.size());

		return respuesta;
	}

	@Override
	@Transactional
	public MiConvocatoriaResponse responder(
			Long usuarioAppId,
			Long convocatoriaId,
			Long jugadorId,
			RespuestaConvocatoriaRequest request) {

		logger.debug("Inicio responder: convocatoriaId={}, jugadorId={}", convocatoriaId, jugadorId);

		validarUsuario(usuarioAppId);

		if (convocatoriaId == null || convocatoriaId <= 0 || jugadorId == null || jugadorId <= 0) {
			throw new IllegalArgumentException("La convocatoria o el jugador no son válidos");
		}

		if (request == null) {
			throw new IllegalArgumentException("La respuesta es obligatoria");
		}

		String respuesta = request.getRespuesta() == null
				? ""
				: request.getRespuesta().trim().toUpperCase(Locale.ROOT);

		if (!VA.equals(respuesta) && !NO_PUEDE.equals(respuesta)) {
			throw new IllegalArgumentException("La respuesta debe ser VA o NO_PUEDE");
		}

		String motivo = limpiar(request.getMotivo());

		if (NO_PUEDE.equals(respuesta)) {

			if (motivo == null || motivo.length() < MOTIVO_MINIMO || motivo.length() > MOTIVO_MAXIMO) {
				throw new IllegalArgumentException("El motivo es obligatorio y debe tener entre " + MOTIVO_MINIMO
						+ " y " + MOTIVO_MAXIMO + " caracteres");
			}

		} else {
			// Con VA el motivo se ignora.
			motivo = null;
		}

		// La seguridad es del servidor: solo el propio jugador o un familiar suyo.
		if (!usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
			throw new SecurityException("No tienes permiso para responder por este jugador");
		}

		Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

		if (convocatoria == null) {
			throw new IllegalArgumentException("La convocatoria no existe");
		}

		ConvocatoriaJugador convocado = convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(convocatoriaId,
				jugadorId);

		if (convocado == null) {
			throw new IllegalArgumentException("El jugador no está en esta convocatoria");
		}

		Partido partido = partidoDao.obtenerPorId(convocatoria.getPartidoId());

		if (partido == null) {
			throw new IllegalArgumentException("El partido de la convocatoria no existe");
		}

		if (partido.isCancelado()) {
			throw new IllegalArgumentException("El partido está cancelado");
		}

		if (!dentroDePlazo(convocatoria)) {
			throw new IllegalArgumentException("El plazo para responder a la convocatoria ha terminado");
		}

		String anterior = convocado.getRespuesta();
		LocalDateTime ahora = LocalDateTime.now(clock);

		convocatoriaJugadorDao.guardarRespuesta(convocatoriaId, jugadorId, respuesta, motivo, ahora, usuarioAppId);

		convocado.setRespuesta(respuesta);
		convocado.setMotivo(motivo);
		convocado.setFechaRespuesta(ahora);
		convocado.setUsuarioAppRespuestaId(usuarioAppId);

		notificarCambio(convocatoria, jugadorId, anterior, respuesta, motivo, usuarioAppId);

		// Privacidad: ni el motivo ni nombres en el log.
		logger.info("Respuesta a convocatoria: convocatoriaId={}, jugadorId={}, respuesta={}", convocatoriaId,
				jugadorId, respuesta);

		return construir(convocatoria, partido, convocado, nombreCompleto(jugadorId));
	}

	/*
	 * Solo se avisa a los entrenadores en los cambios A o DESDE "No puede":
	 * sin responder / Va -> No puede, y No puede -> Va. Un cambio de motivo
	 * dentro de "No puede" no vuelve a avisar.
	 */
	private void notificarCambio(
			Convocatoria convocatoria,
			Long jugadorId,
			String anterior,
			String nueva,
			String motivo,
			Long usuarioAppId) {

		boolean aNoPuede = NO_PUEDE.equals(nueva) && !NO_PUEDE.equals(anterior);
		boolean deNoPuede = VA.equals(nueva) && NO_PUEDE.equals(anterior);

		if (!aNoPuede && !deNoPuede) {
			return;
		}

		List<Long> entrenadores = new ArrayList<>(
				equipoGestionDao.obtenerEntrenadoresPorEquipo(convocatoria.getEquipoId()));

		entrenadores.remove(usuarioAppId);

		if (entrenadores.isEmpty()) {
			return;
		}

		String jugador = equipoGestionDao.obtenerNombreJugador(jugadorId);
		String rival = convocatoria.getRival();

		Comunicacion comunicacion = new Comunicacion();

		if (aNoPuede) {
			comunicacion.setTitulo("Convocatoria: no puede ir");
			comunicacion.setContenido(jugador + " no puede ir al partido contra " + rival + ": " + motivo);
		} else {
			comunicacion.setTitulo("Convocatoria: ahora sí puede ir");
			comunicacion.setContenido(jugador + " finalmente sí puede ir al partido contra " + rival);
		}

		comunicacionService.crearPrivada(comunicacion, entrenadores, usuarioAppId);
	}

	private MiConvocatoriaResponse construir(
			Convocatoria convocatoria,
			Partido partido,
			ConvocatoriaJugador convocado,
			String nombreJugador) {

		boolean cancelado = partido != null && partido.isCancelado();
		LocalDateTime limite = limite(convocatoria);
		boolean puedeResponder = partido != null && !cancelado && dentroDePlazo(convocatoria);

		return new MiConvocatoriaResponse(
				convocatoria.getId(),
				convocado.getJugadorId(),
				nombreJugador,
				convocatoria.getEquipoId(),
				equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId()),
				convocatoria.getRival(),
				convocatoria.getFechaPartido(),
				convocatoria.getHoraPartido(),
				convocatoria.getCampo(),
				partido == null || partido.isLocal(),
				cancelado,
				convocatoria.getHoraConvocatoria(),
				convocatoria.getLugarConvocatoria(),
				convocado.getRespuesta(),
				convocado.getMotivo(),
				convocado.getFechaRespuesta(),
				puedeResponder,
				limite);
	}

	private boolean dentroDePlazo(Convocatoria convocatoria) {

		LocalDateTime limite = limite(convocatoria);

		return limite != null && !LocalDateTime.now(clock).isAfter(limite);
	}

	/**
	 * Hasta la hora de convocatoria del día del partido; sin ella, hasta la hora
	 * del partido; sin ninguna de las dos, hasta las 00:00 del día del partido.
	 * {@code null} si el partido no tiene fecha.
	 */
	static LocalDateTime limite(Convocatoria convocatoria) {

		LocalDate dia = convocatoria.getFechaPartido();

		if (dia == null) {
			return null;
		}

		LocalTime hora = leerHora(convocatoria.getHoraConvocatoria());

		if (hora == null) {
			hora = leerHora(convocatoria.getHoraPartido());
		}

		return dia.atTime(hora != null ? hora : LocalTime.MIDNIGHT);
	}

	private static LocalTime leerHora(String texto) {

		if (texto == null || texto.isBlank()) {
			return null;
		}

		try {
			return LocalTime.parse(texto.trim(), HORA);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private String nombreCompleto(Jugador jugador) {
		return ((jugador.getNombre() == null ? "" : jugador.getNombre()) + " "
				+ (jugador.getApellidos() == null ? "" : jugador.getApellidos())).trim();
	}

	private String nombreCompleto(Long jugadorId) {
		return equipoGestionDao.obtenerNombreJugador(jugadorId);
	}

	private void validarUsuario(Long usuarioAppId) {

		if (usuarioAppId == null || usuarioAppId <= 0) {
			throw new SecurityException("Usuario no válido");
		}
	}

	private static String limpiar(String valor) {

		if (valor == null) {
			return null;
		}

		String resultado = valor.trim();

		return resultado.isEmpty() ? null : resultado;
	}

}

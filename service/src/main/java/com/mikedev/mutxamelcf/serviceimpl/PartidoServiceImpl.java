package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.DescansoEquipo;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.PartidoService;

@Service
public class PartidoServiceImpl implements PartidoService {

	private static final Logger logger = LoggerFactory.getLogger(PartidoServiceImpl.class);

	private static final Pattern PATRON_RESULTADO = Pattern.compile("^\\d{1,2}-\\d{1,2}$");

	private static final Set<String> TIPOS_VALIDOS = Set.of("AMISTOSO", "LIGA", "COPA", "TORNEO");
	private static final String TIPO_POR_DEFECTO = "LIGA";

	/*
	 * Nombre/categoría literales del equipo cuyo próximo/último partido se
	 * muestra en la portada pública. En EQUIPO, la fila del primer equipo
	 * tiene NOMBRE = "Primer Equipo" (no "Mutxamel CF", que es el nombre
	 * del club, no de esa fila) y CATEGORIA = "Primer Equipo" -
	 * confirmado en /admin/equipos (id=21, orden='I').
	 */
	private static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";
	private static final String CATEGORIA_PRIMER_EQUIPO = "Primer Equipo";

	private final PartidoDao partidoDao;
	private final EquipoGestionDao equipoGestionDao;
	private final EquipoDao equipoDao;
	private final ConvocatoriaDao convocatoriaDao;
	private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
	private final DescansoEquipoDao descansoEquipoDao;
	private final JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;
	private final TemporadaDao temporadaDao;
	private final Clock clock;

	public PartidoServiceImpl(
			PartidoDao partidoDao,
			EquipoGestionDao equipoGestionDao,
			EquipoDao equipoDao,
			ConvocatoriaDao convocatoriaDao,
			ConvocatoriaJugadorDao convocatoriaJugadorDao,
			DescansoEquipoDao descansoEquipoDao,
			JustificacionFaltaPartidoDao justificacionFaltaPartidoDao,
			TemporadaDao temporadaDao,
			Clock clock) {

		this.partidoDao = partidoDao;
		this.equipoGestionDao = equipoGestionDao;
		this.equipoDao = equipoDao;
		this.convocatoriaDao = convocatoriaDao;
		this.convocatoriaJugadorDao = convocatoriaJugadorDao;
		this.descansoEquipoDao = descansoEquipoDao;
		this.justificacionFaltaPartidoDao = justificacionFaltaPartidoDao;
		this.temporadaDao = temporadaDao;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PartidoDTO crear(Long usuarioAppId, PartidoGuardarRequest request) {
		logger.debug("Inicio crear: usuarioAppId={}", usuarioAppId);

		if (usuarioAppId == null) {
			throw new SecurityException("Usuario no autenticado");
		}

		validarRequest(request);

		Long equipoId = request.getEquipoId();

		if (!equipoGestionDao.existeEquipo(equipoId)) {
			throw new IllegalArgumentException("El equipo no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
			throw new SecurityException("El usuario no puede gestionar este equipo");
		}

		validarPartidoAnteriorTieneResultado(equipoId);

		Partido partido = new Partido();
		partido.setEquipoId(equipoId);
		aplicarCambios(partido, request);

		partido = partidoDao.crear(partido);

		logger.debug("Fin crear: id={}", partido.getId());

		return construirDTO(partido);
	}

	@Override
	@Transactional
	public PartidoDTO actualizar(Long usuarioAppId, Long partidoId, PartidoGuardarRequest request) {
		logger.debug("Inicio actualizar: usuarioAppId={}, partidoId={}", usuarioAppId, partidoId);

		if (usuarioAppId == null) {
			throw new SecurityException("Usuario no autenticado");
		}

		if (partidoId == null) {
			throw new IllegalArgumentException("El ID del partido es obligatorio");
		}

		validarRequest(request);

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		/*
		 * No se permite cambiar el equipo de un partido al editarlo.
		 */
		if (!partido.getEquipoId().equals(request.getEquipoId())) {
			throw new IllegalArgumentException("No se puede cambiar el equipo del partido");
		}

		/*
		 * Comprobamos permiso sobre el equipo del partido ya existente,
		 * no sobre el que viniera (potencialmente manipulado) en el
		 * request.
		 */
		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, partido.getEquipoId())) {
			throw new SecurityException("El usuario no puede gestionar este equipo");
		}

		aplicarCambios(partido, request);
		partido.setUsuarioActualizoId(usuarioAppId);
		partido.setFechaActualizacion(Timestamp.valueOf(LocalDateTime.now()));

		partidoDao.actualizar(partido);

		logger.debug("Fin actualizar: id={}", partido.getId());

		return construirDTO(partido);
	}

	@Override
	@Transactional
	public PartidoDTO crearComoAdmin(PartidoGuardarRequest request) {
		logger.debug("Inicio crearComoAdmin");

		validarRequest(request);

		Long equipoId = request.getEquipoId();

		if (!equipoGestionDao.existeEquipo(equipoId)) {
			throw new IllegalArgumentException("El equipo no existe");
		}

		validarPartidoAnteriorTieneResultado(equipoId);

		Partido partido = new Partido();
		partido.setEquipoId(equipoId);
		aplicarCambios(partido, request);

		partido = partidoDao.crear(partido);

		logger.debug("Fin crearComoAdmin: id={}", partido.getId());

		return construirDTO(partido);
	}

	@Override
	@Transactional
	public PartidoDTO actualizarComoAdmin(Long partidoId, PartidoGuardarRequest request) {
		logger.debug("Inicio actualizarComoAdmin: partidoId={}", partidoId);

		if (partidoId == null) {
			throw new IllegalArgumentException("El ID del partido es obligatorio");
		}

		validarRequest(request);

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		if (!partido.getEquipoId().equals(request.getEquipoId())) {
			throw new IllegalArgumentException("No se puede cambiar el equipo del partido");
		}

		aplicarCambios(partido, request);

		/*
		 * El admin web ya está autorizado por el filtro /admin/** y no
		 * dispone de un usuarioAppId; no se asocia el cambio a ningún
		 * entrenador de la app.
		 */
		partido.setUsuarioActualizoId(null);
		partido.setFechaActualizacion(Timestamp.valueOf(LocalDateTime.now()));

		partidoDao.actualizar(partido);

		logger.debug("Fin actualizarComoAdmin: id={}", partido.getId());

		return construirDTO(partido);
	}

	@Override
	@Transactional
	public void eliminar(Long usuarioAppId, Long partidoId) {
		logger.debug("Inicio eliminar: usuarioAppId={}, partidoId={}", usuarioAppId, partidoId);

		if (usuarioAppId == null) {
			throw new SecurityException("Usuario no autenticado");
		}

		if (partidoId == null) {
			throw new IllegalArgumentException("El ID del partido es obligatorio");
		}

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, partido.getEquipoId())) {
			throw new SecurityException("El usuario no puede gestionar este equipo");
		}

		partidoDao.eliminar(partidoId);

		logger.debug("Fin eliminar: partidoId={}", partidoId);
	}

	@Override
	@Transactional
	public void cancelar(Long usuarioAppId, Long partidoId) {
		logger.debug("Inicio cancelar: usuarioAppId={}, partidoId={}", usuarioAppId, partidoId);

		if (usuarioAppId == null) {
			throw new SecurityException("Usuario no autenticado");
		}

		if (partidoId == null) {
			throw new IllegalArgumentException("El ID del partido es obligatorio");
		}

		Partido partido = partidoDao.obtenerPorId(partidoId);

		if (partido == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, partido.getEquipoId())) {
			throw new SecurityException("El usuario no puede gestionar este equipo");
		}

		partidoDao.cancelar(partidoId);

		logger.debug("Fin cancelar: partidoId={}", partidoId);
	}

	@Override
	@Transactional
	public void eliminarComoAdmin(Long partidoId) {
		logger.debug("Inicio eliminarComoAdmin: partidoId={}", partidoId);

		if (partidoId == null) {
			throw new IllegalArgumentException("El ID del partido es obligatorio");
		}

		if (partidoDao.obtenerPorId(partidoId) == null) {
			throw new IllegalArgumentException("El partido no existe");
		}

		partidoDao.eliminar(partidoId);

		logger.debug("Fin eliminarComoAdmin: partidoId={}", partidoId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerUltimosPorEquipo(Long equipoId, int limite) {
		logger.debug("Inicio obtenerUltimosPorEquipo: equipoId={}, limite={}", equipoId, limite);

		List<Partido> partidos = partidoDao.obtenerUltimosPorEquipo(equipoId, limite);

		List<PartidoDTO> resultado = toDTOList(partidos);

		logger.debug("Fin obtenerUltimosPorEquipo: equipoId={}, total={}", equipoId, resultado.size());

		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerUltimosPorEquipoNombre(String equipoNombre, int limite) {
		logger.debug("Inicio obtenerUltimosPorEquipoNombre: equipoNombre={}, limite={}", equipoNombre, limite);

		Equipo equipo = equipoDao.obtenerEquipoPorNombre(equipoNombre);

		if (equipo == null) {
			logger.debug("Fin obtenerUltimosPorEquipoNombre: equipo no encontrado");
			return List.of();
		}

		return obtenerUltimosPorEquipo(equipo.getId(), limite);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerPorEquipo(Long equipoId) {
		logger.debug("Inicio obtenerPorEquipo: equipoId={}", equipoId);

		List<Partido> partidos = partidosDeLaTemporadaActiva(equipoId);

		List<PartidoDTO> resultado = toDTOList(partidos);

		logger.debug("Fin obtenerPorEquipo: equipoId={}, total={}", equipoId, resultado.size());

		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerPartidosSinConvocatoria(Long usuarioAppId, Long equipoId, Long incluirPartidoId) {
		logger.debug("Inicio obtenerPartidosSinConvocatoria: usuarioAppId={}, equipoId={}, incluirPartidoId={}",
				usuarioAppId, equipoId, incluirPartidoId);

		if (usuarioAppId == null) {
			throw new SecurityException("Usuario no autenticado");
		}

		if (equipoId == null) {
			throw new IllegalArgumentException("El equipo es obligatorio");
		}

		if (!equipoGestionDao.existeEquipo(equipoId)) {
			throw new IllegalArgumentException("El equipo no existe");
		}

		if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, equipoId)) {
			throw new SecurityException("El usuario no puede gestionar este equipo");
		}

		List<Partido> partidos = partidoDao.obtenerPartidosSinConvocatoria(equipoId, incluirPartidoId);

		List<PartidoDTO> resultado = toDTOList(partidos);

		// El partido pedido con incluirPartidoId puede tener ya convocatoria: se indica para que
		// la app no lo ofrezca como si estuviera libre.
		for (PartidoDTO dto : resultado) {
			dto.setTieneConvocatoria(dto.getId() != null && convocatoriaDao.existePorPartido(dto.getId(), null));
		}

		logger.debug("Fin obtenerPartidosSinConvocatoria: equipoId={}, total={}", equipoId, resultado.size());

		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerPorEquipoYRangoFechas(Long equipoId, LocalDate desde, LocalDate hasta) {
		logger.debug("Inicio obtenerPorEquipoYRangoFechas: equipoId={}, desde={}, hasta={}", equipoId, desde, hasta);

		List<Partido> partidos = partidoDao.obtenerPorEquipoYRangoFechas(equipoId, desde, hasta);

		List<PartidoDTO> resultado = toDTOList(partidos);

		logger.debug("Fin obtenerPorEquipoYRangoFechas: equipoId={}, total={}", equipoId, resultado.size());

		return resultado;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartidoDTO> obtenerPorEquipoYRangoFechas(
			Long equipoId, LocalDate desde, LocalDate hasta, Long jugadorId) {

		logger.debug("Inicio obtenerPorEquipoYRangoFechas: equipoId={}, desde={}, hasta={}, jugadorId={}",
				equipoId, desde, hasta, jugadorId);

		List<PartidoDTO> resultado = obtenerPorEquipoYRangoFechas(equipoId, desde, hasta);

		if (jugadorId != null) {

			// Una sola consulta para las justificaciones del jugador en el rango, y
			// el Partido y la Convocatoria de cada partido se cargan una vez.
			Map<Long, JustificacionFaltaPartido> justificaciones = new HashMap<>();

			for (JustificacionFaltaPartido j : justificacionFaltaPartidoDao
					.obtenerPorJugadorYRango(jugadorId, desde, hasta)) {
				justificaciones.put(j.getPartidoId(), j);
			}

			Map<Long, Partido> partidos = new HashMap<>();

			for (Partido p : partidoDao.obtenerPorEquipoYRangoFechas(equipoId, desde, hasta)) {
				partidos.put(p.getId(), p);
			}

			for (PartidoDTO dto : resultado) {

				Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(dto.getId());

				dto.setConvocado(resolverConvocado(convocatoria, jugadorId));
				resolverAusencia(dto, partidos.get(dto.getId()), convocatoria, justificaciones.get(dto.getId()));
			}
		}

		logger.debug("Fin obtenerPorEquipoYRangoFechas: equipoId={}, jugadorId={}, total={}",
				equipoId, jugadorId, resultado.size());

		return resultado;
	}

	/**
	 * Rellena en el calendario del jugador si ha justificado su ausencia (y
	 * por qué), si todavía puede justificarla o retirarla y hasta cuándo.
	 */
	private void resolverAusencia(
			PartidoDTO dto, Partido partido, Convocatoria convocatoria, JustificacionFaltaPartido justificacion) {

		dto.setAusenciaJustificada(justificacion != null);
		dto.setMotivoAusencia(justificacion != null ? justificacion.getMotivo() : null);

		if (partido == null) {
			return;
		}

		LocalDateTime limite = PlazoJustificacionPartido.limite(partido, convocatoria);

		dto.setLimiteJustificacion(limite);
		dto.setPuedeJustificar(
				!partido.isCancelado() && limite != null && !LocalDateTime.now(clock).isAfter(limite));
	}

	/**
	 * {@code null} si el partido todavía no tiene convocatoria creada;
	 * si la tiene, {@code true}/{@code false} según si el jugador está
	 * en ella.
	 */
	private Boolean resolverConvocado(Convocatoria convocatoria, Long jugadorId) {

		if (convocatoria == null) {
			return null;
		}

		return convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(convocatoria.getId(), jugadorId) != null;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResultadoDTO> obtenerResultados(String deporte) {
		logger.debug("Inicio obtenerResultados: deporte={}", deporte);

		/*
		 * Debe aparecer EXACTAMENTE una entrada por cada equipo de ese
		 * deporte (aunque no tenga ningún partido todavía), con su
		 * partido más relevante: el próximo sin resultado o, si no hay
		 * ninguno futuro, el último jugado.
		 */
		List<Equipo> equipos = equipoDao.obtenerActivosPublicosPorDeporte(deporte);

		List<EquipoConPartidoMasReciente> equiposConPartido = new ArrayList<>();

		for (Equipo equipo : equipos) {

			Partido partido = partidoDao.obtenerMasRelevantePorEquipo(equipo.getId());

			equiposConPartido.add(new EquipoConPartidoMasReciente(equipo, partido));
		}

		/*
		 * Agrupados por categoría, en orden DESCENDENTE del campo
		 * EQUIPO.orden (equipoDao.obtenerCategorias() ya lo devuelve
		 * ascendente, así que aquí lo invertimos) para que el Primer
		 * Equipo aparezca arriba del todo y la Escuelita abajo del
		 * todo. Dentro de cada categoría, por fecha del partido
		 * descendente, con los equipos sin partido (dia == null) al
		 * final de su categoría.
		 *
		 * Ojo: esta inversión es solo para este listado de "Partidos";
		 * equipoDao.obtenerCategorias() se sigue usando ascendente tal
		 * cual en el resto de pantallas (filtros de admin, etc.).
		 */
		List<String> categoriasOrdenadas = new ArrayList<>(equipoDao.obtenerCategorias());
		Collections.reverse(categoriasOrdenadas);

		Map<String, Integer> ordenCategoria = new HashMap<>();
		for (int i = 0; i < categoriasOrdenadas.size(); i++) {
			ordenCategoria.putIfAbsent(categoriasOrdenadas.get(i), i);
		}

		Comparator<EquipoConPartidoMasReciente> comparador = Comparator
				.<EquipoConPartidoMasReciente>comparingInt(
						ep -> ordenCategoria.getOrDefault(ep.equipo.getCategoria(), Integer.MAX_VALUE))
				.thenComparing(
						ep -> ep.partido != null ? ep.partido.getDia() : null,
						Comparator.nullsLast(Comparator.reverseOrder()));

		equiposConPartido.sort(comparador);

		List<ResultadoDTO> resultados = new ArrayList<>();

		for (EquipoConPartidoMasReciente ep : equiposConPartido) {
			ResultadoDTO dto = toResultadoDTO(ep.partido, ep.equipo);
			dto.setDescansa(descansa(ep.equipo != null ? ep.equipo.getId() : null, ep.partido));
			dto.setPendienteResultado(pendienteResultado(ep.partido));
			resultados.add(dto);
		}

		logger.debug("Fin obtenerResultados: deporte={}, total={}", deporte, resultados.size());

		return resultados;
	}

	@Override
	@Transactional(readOnly = true)
	public ResultadoDTO obtenerResultadoPrimerEquipo() {
		logger.debug("Inicio obtenerResultadoPrimerEquipo");

		Partido partido = partidoDao.obtenerMasRelevantePorEquipoNombre(
				EQUIPO_PRIMER_EQUIPO,
				CATEGORIA_PRIMER_EQUIPO);

		ResultadoDTO resultado = new ResultadoDTO();
		resultado.setCategoria(CATEGORIA_PRIMER_EQUIPO);
		resultado.setEquipo(EQUIPO_PRIMER_EQUIPO);
		resultado.setDescansa(descansa(
				partido != null ? partido.getEquipoId() : idEquipoPorNombre(EQUIPO_PRIMER_EQUIPO), partido));

		if (partido == null) {
			// Sin próximo partido ni último jugado: se devuelve igualmente
			// un ResultadoDTO (rival vacío), como con una jornada de
			// descanso, para que la app pinte la tarjeta "SIN PARTIDO
			// PRIMER EQUIPO" en vez de un simple mensaje de texto.
			logger.debug("Fin obtenerResultadoPrimerEquipo: encontrado=false");
			return resultado;
		}

		resultado.setEquipoId(partido.getEquipoId());
		resultado.setRival(partido.getRival());
		resultado.setResultado(partido.getResultado());
		resultado.setTipo(partido.getTipo());
		aplicarFecha(partido.getDia(), resultado::setDia, resultado::setDiaFormateado);
		resultado.setHora(partido.getHora());
		resultado.setCampo(partido.getCampo());
		resultado.setLocal(partido.isLocal());
		resultado.setPendienteResultado(pendienteResultado(partido));

		logger.debug("Fin obtenerResultadoPrimerEquipo: encontrado=true");

		return resultado;
	}

	private Long idEquipoPorNombre(String nombre) {
		Equipo equipo = equipoDao.obtenerEquipoPorNombre(nombre);
		return equipo != null ? equipo.getId() : null;
	}

	/*
	 * El equipo descansa si tiene un descanso registrado (importador FFCV)
	 * para el fin de semana en curso o el próximo (la fecha del descanso es
	 * el primer día del fin de semana, que dura dos días) y no tiene un
	 * partido pendiente hasta el final de ese fin de semana.
	 */
	private boolean descansa(Long equipoId, Partido partidoRelevante) {

		if (equipoId == null) {
			return false;
		}

		LocalDate hoy = LocalDate.now(clock);

		List<DescansoEquipo> descansos = descansoEquipoDao.obtenerDesde(equipoId, hoy.minusDays(1));

		if (descansos == null || descansos.isEmpty()) {
			return false;
		}

		LocalDate finDeSemana = descansos.get(0).getFecha().plusDays(1);

		/*
		 * Pendiente = partido por jugar (hoy o más adelante) sin resultado,
		 * dentro de ese fin de semana. Un partido YA PASADO sin resultado
		 * (el último, a la espera de que el entrenador lo introduzca) no
		 * anula el descanso del fin de semana siguiente.
		 */
		boolean tienePartidoPendiente = partidoRelevante != null
				&& partidoRelevante.getDia() != null
				&& !partidoRelevante.isCancelado()
				&& !tieneResultado(partidoRelevante)
				&& !aLocalDate(partidoRelevante.getDia()).isBefore(hoy)
				&& !aLocalDate(partidoRelevante.getDia()).isAfter(finDeSemana);

		return !tienePartidoPendiente;
	}

	/*
	 * Mismo criterio que el recordatorio de resultado
	 * (RecordatorioResultadoPartidoScheduler): hay resultado si hay goles o
	 * un texto que no sea solo guiones.
	 */
	private static boolean tieneResultado(Partido partido) {

		if (partido.getGolesFavor() != null) {
			return true;
		}

		String resultado = partido.getResultado();

		return resultado != null && !resultado.replace("-", "").isBlank();
	}

	/**
	 * El partido ya empezó (día anterior a hoy, o hoy con la hora ya pasada
	 * en hora de Madrid) y no tiene resultado ni goles. Con hoy y sin una hora
	 * válida HH:mm no se puede saber si ya se jugó: no se marca.
	 */
	private boolean pendienteResultado(Partido partido) {

		if (partido == null || partido.getDia() == null || partido.isCancelado() || tieneResultado(partido)) {
			return false;
		}

		LocalDateTime ahora = LocalDateTime.now(clock);
		LocalDate dia = aLocalDate(partido.getDia());

		if (dia.isBefore(ahora.toLocalDate())) {
			return true;
		}

		if (!dia.equals(ahora.toLocalDate()) || partido.getHora() == null) {
			return false;
		}

		try {
			return !ahora.toLocalTime().isBefore(LocalTime.parse(partido.getHora().trim()));
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private static LocalDate aLocalDate(Date dia) {
		return dia instanceof java.sql.Date sqlDate
				? sqlDate.toLocalDate()
				: dia.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}

	private Equipo obtenerEquipo(Long equipoId) {
		return equipoId == null ? null : equipoDao.obtenerEquipoPorId(equipoId);
	}

	private void validarRequest(PartidoGuardarRequest request) {

		if (request == null) {
			throw new IllegalArgumentException("La petición es obligatoria");
		}

		if (request.getEquipoId() == null) {
			throw new IllegalArgumentException("El equipo es obligatorio");
		}

		if (request.getRival() == null || request.getRival().isBlank()) {
			throw new IllegalArgumentException("El rival es obligatorio");
		}

		String resultado = request.getResultado();

		if (resultado != null
				&& !resultado.isBlank()
				&& !PATRON_RESULTADO.matcher(resultado).matches()) {

			throw new IllegalArgumentException(
					"El resultado debe tener el formato 'goles locales-goles visitantes' (ej. 2-1)");
		}

		String tipo = request.getTipo();

		if (tipo != null
				&& !tipo.isBlank()
				&& !TIPOS_VALIDOS.contains(tipo.trim().toUpperCase())) {

			throw new IllegalArgumentException(
					"El tipo de partido debe ser AMISTOSO, LIGA, COPA o TORNEO");
		}
	}

	/*
	 * Regla de negocio: no se puede crear un partido nuevo para un
	 * equipo si el partido anterior de ese equipo todavía no tiene
	 * resultado puesto. Si el equipo no tiene ningún partido previo, se
	 * puede crear sin restricción (es el primero).
	 */
	// Partidos del equipo de la temporada activa, en el orden del DAO. Sin temporada activa, todos.
	private List<Partido> partidosDeLaTemporadaActiva(Long equipoId) {
		RangoTemporada rango = RangoTemporada.de(temporadaDao.obtenerTemporadaActiva());

		return partidoDao.obtenerPorEquipo(equipoId).stream()
				.filter(p -> rango.contiene(p.getDia()))
				.toList();
	}

	private void validarPartidoAnteriorTieneResultado(Long equipoId) {

		// Solo la temporada activa: un partido sin resultado de la anterior no bloquea el primero de la nueva
		List<Partido> partidosEquipo = partidosDeLaTemporadaActiva(equipoId);

		if (partidosEquipo.isEmpty()) {
			return;
		}

		Partido ultimoPartido = partidosEquipo.get(0);

		if (ultimoPartido.getResultado() == null || ultimoPartido.getResultado().isBlank()) {
			throw new IllegalArgumentException(
					"No se puede crear un partido nuevo mientras el partido anterior no tenga el resultado puesto");
		}
	}

	private void aplicarCambios(Partido partido, PartidoGuardarRequest request) {
		partido.setRival(request.getRival());
		partido.setDia(toDate(request.getDia()));
		partido.setHora(request.getHora());
		partido.setCampo(request.getCampo());
		partido.setResultado(normalizarResultado(request.getResultado()));
		partido.setTipo(normalizarTipo(request.getTipo()));
	}

	private static String normalizarResultado(String resultado) {
		return (resultado == null || resultado.isBlank()) ? null : resultado;
	}

	private static String normalizarTipo(String tipo) {
		return (tipo == null || tipo.isBlank()) ? TIPO_POR_DEFECTO : tipo.trim().toUpperCase();
	}

	private static Date toDate(LocalDate localDate) {
		return localDate == null ? null : java.sql.Date.valueOf(localDate);
	}

	private PartidoDTO construirDTO(Partido partido) {

		PartidoDTO dto = new PartidoDTO();

		dto.setId(partido.getId());
		dto.setEquipoId(partido.getEquipoId());

		Equipo equipo = obtenerEquipo(partido.getEquipoId());

		if (equipo != null) {
			dto.setEquipo(equipo.getNombre());
			dto.setCategoria(equipo.getCategoria());
			dto.setDeporte(equipo.getDeporte());
		}

		dto.setRival(partido.getRival());
		dto.setResultado(partido.getResultado());
		dto.setTipo(partido.getTipo());
		aplicarFecha(partido.getDia(), dto::setDia, dto::setDiaFormateado);
		dto.setHora(partido.getHora());
		dto.setCampo(partido.getCampo());
		dto.setLocal(partido.isLocal());
		dto.setCancelado(partido.isCancelado());

		return dto;
	}

	private static ResultadoDTO toResultadoDTO(Partido partido, Equipo equipo) {

		ResultadoDTO dto = new ResultadoDTO();

		dto.setCategoria(equipo != null ? equipo.getCategoria() : null);
		dto.setEquipoId(equipo != null ? equipo.getId() : null);
		dto.setEquipo(equipo != null ? equipo.getNombre() : null);

		/*
		 * Equipo sin ningún partido todavía: entrada "placeholder" con
		 * rival=null (no cadena vacía), para que la app/la web puedan
		 * distinguirlo de un partido real y mostrar "No tiene partido".
		 */
		if (partido == null) {
			dto.setRival(null);
			dto.setResultado(null);
			dto.setTipo(null);
			dto.setDia(null);
			dto.setDiaFormateado(null);
			dto.setHora(null);
			dto.setCampo(null);
			return dto;
		}

		dto.setRival(partido.getRival());
		dto.setResultado(partido.getResultado());
		dto.setTipo(partido.getTipo());
		aplicarFecha(partido.getDia(), dto::setDia, dto::setDiaFormateado);
		dto.setHora(partido.getHora());
		dto.setCampo(partido.getCampo());
		dto.setLocal(partido.isLocal());

		return dto;
	}

	private static void aplicarFecha(
			Date dia,
			java.util.function.Consumer<Date> setDia,
			java.util.function.Consumer<String> setDiaFormateado) {

		if (dia != null) {
			setDia.accept(dia);
			setDiaFormateado.accept(new SimpleDateFormat("dd/MM/yyyy").format(dia));
		} else {
			setDia.accept(null);
			setDiaFormateado.accept("");
		}
	}

	private List<PartidoDTO> toDTOList(List<Partido> partidos) {
		List<PartidoDTO> resultado = new ArrayList<>();
		for (Partido partido : partidos) {
			resultado.add(construirDTO(partido));
		}
		return resultado;
	}

	/*
	 * Par (equipo, partido más relevante de ese equipo) usado únicamente
	 * para poder ordenar/agrupar obtenerResultados() antes de mapear a
	 * ResultadoDTO.
	 */
	private static final class EquipoConPartidoMasReciente {

		private final Equipo equipo;
		private final Partido partido;

		private EquipoConPartidoMasReciente(Equipo equipo, Partido partido) {
			this.equipo = equipo;
			this.partido = partido;
		}
	}

}

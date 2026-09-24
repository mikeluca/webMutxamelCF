package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
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
	 * muestra en la portada pública. Mismo criterio que usaba
	 * ResultadoDaoImpl.obtenerResultadoPrimerEquipo().
	 */
	private static final String EQUIPO_PRIMER_EQUIPO = "Mutxamel CF";
	private static final String CATEGORIA_PRIMER_EQUIPO = "Primer Equipo";

	private final PartidoDao partidoDao;
	private final EquipoGestionDao equipoGestionDao;
	private final EquipoDao equipoDao;

	public PartidoServiceImpl(
			PartidoDao partidoDao,
			EquipoGestionDao equipoGestionDao,
			EquipoDao equipoDao) {

		this.partidoDao = partidoDao;
		this.equipoGestionDao = equipoGestionDao;
		this.equipoDao = equipoDao;
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

		List<Partido> partidos = partidoDao.obtenerPorEquipo(equipoId);

		List<PartidoDTO> resultado = toDTOList(partidos);

		logger.debug("Fin obtenerPorEquipo: equipoId={}, total={}", equipoId, resultado.size());

		return resultado;
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
		List<Equipo> equipos = equipoDao.obtenerTodosPorDeporte(deporte);

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
			resultados.add(toResultadoDTO(ep.partido, ep.equipo));
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

		if (partido == null) {
			logger.debug("Fin obtenerResultadoPrimerEquipo: encontrado=false");
			return null;
		}

		ResultadoDTO resultado = new ResultadoDTO();
		resultado.setCategoria(CATEGORIA_PRIMER_EQUIPO);
		resultado.setEquipo(EQUIPO_PRIMER_EQUIPO);
		resultado.setRival(partido.getRival());
		resultado.setResultado(partido.getResultado());
		resultado.setTipo(partido.getTipo());
		aplicarFecha(partido.getDia(), resultado::setDia, resultado::setDiaFormateado);
		resultado.setHora(partido.getHora());
		resultado.setCampo(partido.getCampo());

		logger.debug("Fin obtenerResultadoPrimerEquipo: encontrado=true");

		return resultado;
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
	private void validarPartidoAnteriorTieneResultado(Long equipoId) {

		List<Partido> partidosEquipo = partidoDao.obtenerPorEquipo(equipoId);

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

		return dto;
	}

	private static ResultadoDTO toResultadoDTO(Partido partido, Equipo equipo) {

		ResultadoDTO dto = new ResultadoDTO();

		dto.setCategoria(equipo != null ? equipo.getCategoria() : null);
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

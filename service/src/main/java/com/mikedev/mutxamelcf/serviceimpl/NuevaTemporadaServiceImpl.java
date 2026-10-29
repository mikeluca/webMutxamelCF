package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mikedev.mutxamelcf.dao.CierreTemporadaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.AnioNacimiento;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado.Estado;
import com.mikedev.mutxamelcf.model.CifrasCierre;
import com.mikedev.mutxamelcf.model.EnumEquipos;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.ResumenCierreDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.Fila;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.GrupoCategoria;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.GrupoEquipo;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO.SinPropuesta;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

@Service
public class NuevaTemporadaServiceImpl implements NuevaTemporadaService {

	private static final Logger logger = LoggerFactory.getLogger(NuevaTemporadaServiceImpl.class);

	static final String NOTA_SIN_ANIO = "Sin año de nacimiento";
	static final String NOTA_FIN_ETAPA = "Termina Juvenil: ¿Filial, Primer Equipo o baja?";
	static final String NOTA_PRUEBA = "Jugador de prueba";

	private static final Set<String> CONTINUIDADES = Set.of("PENDIENTE", "SIGUE", "NO_SIGUE", "DUDA");

	private final TemporadaDao temporadaDao;
	private final JugadorDao jugadorDao;
	private final JugadorTemporadaDao jugadorTemporadaDao;
	private final EquipoDao equipoDao;
	private final CategoriaPorEdad categoriaPorEdad;
	private final CierreTemporadaDao cierreDao;
	private final PlatformTransactionManager transactionManager;
	private final NotificacionAppService notificacionAppService;
	private final Clock clock;

	public NuevaTemporadaServiceImpl(TemporadaDao temporadaDao, JugadorDao jugadorDao,
			JugadorTemporadaDao jugadorTemporadaDao, EquipoDao equipoDao, CategoriaPorEdad categoriaPorEdad,
			CierreTemporadaDao cierreDao, PlatformTransactionManager transactionManager,
			NotificacionAppService notificacionAppService, Clock clock) {
		this.cierreDao = cierreDao;
		this.transactionManager = transactionManager;
		this.notificacionAppService = notificacionAppService;
		this.temporadaDao = temporadaDao;
		this.jugadorDao = jugadorDao;
		this.jugadorTemporadaDao = jugadorTemporadaDao;
		this.equipoDao = equipoDao;
		this.categoriaPorEdad = categoriaPorEdad;
		this.clock = clock;
	}

	// ============================================================
	// Destino
	// ============================================================

	@Override
	public Temporada temporadaActiva() {
		return temporadaDao.obtenerTemporadaActiva();
	}

	@Override
	public List<Temporada> temporadasDestino() {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		if (activa == null) {
			return List.of();
		}

		return temporadaDao.obtenerTodos().stream().filter(t -> esDestinoValido(t, activa))
				.sorted(Comparator.comparing(Temporada::getFechaInicio)).toList();
	}

	private static boolean esDestinoValido(Temporada t, Temporada activa) {
		return t.getId() != null && !t.getId().equals(activa.getId()) && t.getFechaInicio() != null
				&& activa.getFechaInicio() != null && t.getFechaInicio().after(activa.getFechaInicio());
	}

	/** El destino tiene que existir, no estar activo y empezar después de la activa. */
	private Temporada validarDestino(Long destinoId) {
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		if (activa == null) {
			throw new IllegalArgumentException("No hay ninguna temporada activa.");
		}

		Temporada destino = destinoId == null ? null : temporadaDao.obtenerPorId(destinoId);
		if (destino == null) {
			throw new IllegalArgumentException("La temporada de destino no existe.");
		}
		if (!esDestinoValido(destino, activa)) {
			throw new IllegalArgumentException(
					"La temporada de destino no puede estar activa y tiene que empezar después de la activa ("
							+ activa.getNombre() + ").");
		}

		return destino;
	}

	// ============================================================
	// Propuesta
	// ============================================================

	@Override
	@Transactional
	public int generarPropuesta(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		Temporada activa = temporadaDao.obtenerTemporadaActiva();
		Contexto contexto = contexto(activa, destino);

		Set<Long> conFila = jugadorTemporadaDao.listarPorTemporada(destinoId).stream()
				.map(JugadorTemporada::getJugadorId).collect(Collectors.toSet());

		int creadas = 0;
		for (Jugador jugador : jugadorDao.obtenerActivos()) {
			if (!conFila.contains(jugador.getId())) {
				jugadorTemporadaDao.guardar(proponer(jugador, contexto));
				creadas++;
			}
		}

		logger.info("Propuesta generada: destino={}, filasCreadas={}", destino.getNombre(), creadas);
		return creadas;
	}

	/** Borra el borrador y lo vuelve a generar. Las altas de prueba se conservan: no salen de la plantilla actual. */
	@Override
	@Transactional
	public int regenerar(Long destinoId) {
		validarDestino(destinoId);
		int borradas = jugadorTemporadaDao.eliminarBorradorSinPrueba(destinoId);
		logger.info("Borrador borrado para regenerar (sin las pruebas): destinoId={}, filas={}", destinoId, borradas);
		return generarPropuesta(destinoId);
	}

	@Override
	@Transactional
	public void anadirSinPropuesta(Long destinoId, Long jugadorId) {
		Temporada destino = validarDestino(destinoId);
		Jugador jugador = jugadorDao.obtenerPorId(jugadorId);
		if (jugador == null || jugador.estaDeBaja()) {
			throw new IllegalArgumentException("Solo se pueden añadir jugadores activos.");
		}
		if (jugadorTemporadaDao.obtener(jugadorId, destinoId) != null) {
			throw new IllegalStateException("El jugador ya tiene fila en la temporada " + destino.getNombre() + ".");
		}

		jugadorTemporadaDao.guardar(proponer(jugador, contexto(temporadaDao.obtenerTemporadaActiva(), destino)));
	}

	/** Datos que comparten todas las propuestas de una generación. */
	private record Contexto(Temporada destino, int anioActiva, int anioDestino, Set<String> categoriasConEdad,
			Map<Long, Equipo> equiposPorId) {
	}

	private Contexto contexto(Temporada activa, Temporada destino) {
		Map<Long, Equipo> porId = new HashMap<>();
		equipoDao.obtenerTodos().forEach(e -> porId.put(e.getId(), e));

		// Las categorías que no están en CATEGORIAS_EDAD (Filial, Primer Equipo, Veteranos) no dependen de la edad.
		Set<String> conEdad = categoriaPorEdad.obtenerTodas().stream()
				.map(c -> c.getCategoria().trim().toLowerCase()).collect(Collectors.toSet());

		return new Contexto(destino, categoriaPorEdad.anioInicio(activa), categoriaPorEdad.anioInicio(destino), conEdad,
				porId);
	}

	/**
	 * Reglas de la propuesta para un jugador activo:
	 * <ol>
	 * <li>Equipo de una categoría que no está en CATEGORIAS_EDAD (Filial, Primer Equipo, Veteranos): mismo equipo y
	 * dorsal, equipo definitivo, continuidad SIGUE y sin revisión, tenga año de nacimiento o no. Va antes que todo lo
	 * demás.</li>
	 * <li>Categoría formativa: la que le toca por edad en el destino, sin equipo, sin dorsal y sin equipo definitivo
	 * (entrena en la pretemporada de su categoría hasta que el coordinador le asigne uno).</li>
	 * <li>Sin año de nacimiento: conserva su categoría actual, sin equipo, a revisar.</li>
	 * <li>Supera la edad máxima configurada (fin de Juvenil): sin equipo ni categoría, a revisar; decide el
	 * coordinador (Filial, Primer Equipo o baja).</li>
	 * <li>Ninguna categoría cubre su edad: mismo equipo y dorsal, a revisar.</li>
	 * <li>Hoy juega en una categoría distinta de la que le toca por edad: se propone según su edad y se marca a
	 * revisar, además de lo anterior.</li>
	 * </ol>
	 * Todas las filas nacen con CONTINUIDAD = PENDIENTE: hasta que no se resuelve (SIGUE) siguen pendientes de revisar.
	 */
	private JugadorTemporada proponer(Jugador jugador, Contexto contexto) {
		Equipo actual = jugador.getEquipoId() == null ? null : contexto.equiposPorId().get(jugador.getEquipoId());
		JugadorTemporada fila = new JugadorTemporada();
		fila.setJugadorId(jugador.getId());
		fila.setTemporadaId(contexto.destino().getId());
		fila.setEquipoDefinitivo(false);

		// Equipo de una categoría sin regla de edad (adultos): se queda como está, con o sin año de nacimiento.
		if (actual != null && actual.getCategoria() != null
				&& !contexto.categoriasConEdad().contains(actual.getCategoria().trim().toLowerCase())) {
			mismoEquipo(fila, actual, jugador);
			// Se quedan en su equipo (decisión del club): no esperan la respuesta de las familias
			fila.setContinuidad("SIGUE");
			return fila;
		}

		if (jugador.getAnioNacimiento() == null) {
			fila.setCategoria(actual != null ? actual.getCategoria() : jugador.getCategoria());
			fila.setPendienteRevision(true);
			fila.setNota(NOTA_SIN_ANIO);
			return fila;
		}

		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(jugador.getAnioNacimiento(),
				contexto.anioDestino());

		if (porEdad.estado() == Estado.SUPERA_EDAD_MAXIMA) {
			// Ya no se propone la baja: el coordinador decide (Filial, Primer Equipo o baja) en la revisión.
			fila.setPendienteRevision(true);
			fila.setNota(NOTA_FIN_ETAPA);
			return fila;
		}

		if (!porEdad.tieneCategoria()) {
			mismoEquipo(fila, actual, jugador);
			fila.setPendienteRevision(true);
			fila.setNota("Ninguna categoría cubre su edad (" + porEdad.edad() + " años)");
			return fila;
		}

		fila.setCategoria(porEdad.categoria());

		// Juega por arriba o por abajo: la categoría que le tocaba por edad esta temporada no es la de su equipo.
		if (actual != null) {
			CategoriaPorEdadResultado hoy = categoriaPorEdad.categoriaPara(jugador.getAnioNacimiento(),
					contexto.anioActiva());
			if (hoy.tieneCategoria() && !mismaCategoria(hoy.categoria(), actual.getCategoria())) {
				fila.setPendienteRevision(true);
				fila.setNota("Jugaba fuera de su categoría (" + actual.getCategoria() + ")");
			}
		}

		return fila;
	}

	private static void mismoEquipo(JugadorTemporada fila, Equipo actual, Jugador jugador) {
		if (actual != null) {
			copiarEquipo(fila, actual);
		}
		fila.setDorsal(jugador.getDorsal());
		fila.setEquipoDefinitivo(actual != null);
	}

	private static void copiarEquipo(JugadorTemporada fila, Equipo equipo) {
		fila.setEquipoId(equipo.getId());
		fila.setEquipoNombre(equipo.getNombre());
		fila.setCategoria(equipo.getCategoria());
	}

	private static boolean mismaCategoria(String a, String b) {
		return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
	}

	// ============================================================
	// Altas de prueba
	// ============================================================

	/**
	 * Crea al jugador y su fila PRUEBA en el borrador, con la categoría que le toca por edad y sin equipo.
	 *
	 * <p>
	 * Hasta que se aplique la temporada no existe en la temporada activa: su ficha nace con FECHA_BAJA (así ningún
	 * listado de jugadores activos la ve, porque todos filtran por FECHA_BAJA IS NULL), no tiene equipo ni fila en la
	 * temporada activa, y las bajas y el listado completo del panel la ocultan mientras tenga una fila PRUEBA. Al
	 * aplicar la temporada se activa en su categoría.
	 */
	@Override
	@Transactional
	public void anadirPrueba(Long destinoId, String nombre, String apellidos, Integer anioNacimiento) {
		Temporada destino = validarDestino(destinoId);

		String nombreLimpio = nombre == null ? "" : nombre.trim();
		String apellidosLimpios = apellidos == null ? "" : apellidos.trim();
		if (nombreLimpio.isEmpty() || apellidosLimpios.isEmpty()) {
			throw new IllegalArgumentException("El nombre y los apellidos son obligatorios.");
		}
		if (anioNacimiento == null) {
			throw new IllegalArgumentException("El año de nacimiento es obligatorio para un jugador de prueba.");
		}
		String errorAnio = AnioNacimiento.mensajeError(anioNacimiento, LocalDate.now(clock).getYear());
		if (errorAnio != null) {
			throw new IllegalArgumentException(errorAnio);
		}

		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(anioNacimiento,
				categoriaPorEdad.anioInicio(destino));
		if (porEdad.estado() == Estado.SUPERA_EDAD_MAXIMA || !porEdad.tieneCategoria()) {
			throw new IllegalArgumentException(
					"Ninguna categoría formativa cubre su edad (" + porEdad.edad() + " años) en " + destino.getNombre() + ".");
		}
		String categoria = porEdad.categoria();

		// El deporte lo da cualquier equipo de esa categoría; sin equipos, fútbol.
		String deporte = equipoDao.obtenerTodos().stream().filter(e -> mismaCategoria(e.getCategoria(), categoria))
				.map(Equipo::getDeporte).filter(d -> d != null && !d.isBlank()).findFirst().orElse("F");

		Jugador jugador = new Jugador();
		jugador.setNombre(nombreLimpio);
		jugador.setApellidos(apellidosLimpios);
		jugador.setCategoria(categoria);
		jugador.setDeporte(deporte);
		jugador.setAnioNacimiento(anioNacimiento);
		if (!jugadorDao.guardarJugador(jugador) || jugador.getId() == null) {
			throw new IllegalStateException("No se ha podido crear al jugador de prueba.");
		}
		jugadorDao.darDeBaja(jugador.getId(), LocalDate.now(clock));

		JugadorTemporada fila = new JugadorTemporada();
		fila.setJugadorId(jugador.getId());
		fila.setTemporadaId(destino.getId());
		fila.setCategoria(categoria);
		fila.setEstado("PRUEBA");
		// Una prueba no contesta la pregunta de continuidad: no se queda pendiente por eso.
		fila.setContinuidad("SIGUE");
		fila.setEquipoDefinitivo(false);
		fila.setNota(NOTA_PRUEBA);
		jugadorTemporadaDao.guardar(fila);

		logger.info("Alta de prueba: jugadorId={}, categoria={}, destino={}", jugador.getId(), categoria,
				destino.getNombre());
	}

	// ============================================================
	// Continuidad
	// ============================================================

	/**
	 * Marca la respuesta de continuidad de una fila del borrador. NO_SIGUE propone la baja, pero la fila queda
	 * pendiente hasta que el coordinador la confirme guardándola; PENDIENTE y DUDA dejan la fila pendiente de revisar.
	 */
	@Override
	@Transactional
	public void marcarContinuidad(Long destinoId, Long jugadorId, String continuidad) {
		validarDestino(destinoId);

		if (continuidad == null || !CONTINUIDADES.contains(continuidad)) {
			throw new IllegalArgumentException("La continuidad tiene que ser PENDIENTE, SIGUE, NO_SIGUE o DUDA.");
		}

		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugadorId, destinoId);
		if (fila == null) {
			throw new IllegalArgumentException("El jugador no tiene fila en esta temporada.");
		}
		if ("PRUEBA".equals(fila.getEstado())) {
			throw new IllegalArgumentException("Un jugador de prueba no tiene continuidad.");
		}

		boolean eraNoSigue = "NO_SIGUE".equals(fila.getContinuidad());
		fila.setContinuidad(continuidad);
		fila.setFechaContinuidad(LocalDateTime.now(clock));
		fila.setUsuarioAppContinuidadId(null);

		if ("NO_SIGUE".equals(continuidad)) {
			fila.setBaja(true);
			fila.setPendienteRevision(true);
		} else if (eraNoSigue) {
			// Dejó de ser una baja propuesta: que el coordinador lo revise.
			fila.setBaja(false);
			fila.setPendienteRevision(true);
		}

		jugadorTemporadaDao.guardar(fila);
	}

	// ============================================================
	// Revisión
	// ============================================================

	@Override
	public RevisionTemporadaDTO revision(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(destinoId);

		// Dorsales repetidos dentro de un mismo equipo destino (las bajas no cuentan).
		Map<String, Integer> usos = new HashMap<>();
		for (JugadorTemporada f : filas) {
			if (!f.isBaja() && f.getEquipoId() != null && f.getDorsal() != null) {
				usos.merge(f.getEquipoId() + ":" + f.getDorsal(), 1, Integer::sum);
			}
		}

		List<Fila> todas = new ArrayList<>();
		for (JugadorTemporada f : filas) {
			boolean repetido = !f.isBaja() && f.getEquipoId() != null && f.getDorsal() != null
					&& usos.get(f.getEquipoId() + ":" + f.getDorsal()) > 1;
			// Una prueba nueva tiene la ficha oculta (FECHA_BAJA), pero no está "de baja en el panel".
			boolean deBajaEnElPanel = f.isJugadorDeBaja() && !"PRUEBA".equals(f.getEstado());
			todas.add(new Fila(f.getJugadorId(), nombre(f.getJugadorNombre(), f.getJugadorApellidos()),
					f.getAnioNacimiento(), f.getEquipoActualId(), f.getEquipoActualNombre(), f.getEquipoId(),
					f.getEquipoNombre(), f.getCategoria(), f.getDorsal(), f.isBaja(), f.requiereRevision(), f.getNota(),
					repetido, deBajaEnElPanel, f.getEstado(), f.getContinuidad(), f.isEquipoDefinitivo()));
		}

		Map<Long, List<Fila>> porEquipo = new LinkedHashMap<>();
		Map<Long, String> nombres = new HashMap<>();
		Map<String, List<Fila>> porCategoria = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Fila f : todas) {
			if (f.baja()) {
				continue;
			}
			if (f.equipoId() != null) {
				porEquipo.computeIfAbsent(f.equipoId(), k -> new ArrayList<>()).add(f);
				nombres.put(f.equipoId(), f.equipoNombre());
			} else if (f.categoria() != null) {
				porCategoria.computeIfAbsent(f.categoria(), k -> new ArrayList<>()).add(f);
			}
		}

		List<GrupoEquipo> grupos = porEquipo.entrySet().stream()
				.map(e -> new GrupoEquipo(e.getKey(), nombres.get(e.getKey()), e.getValue().size(), e.getValue()))
				.sorted(Comparator.comparing(GrupoEquipo::equipoNombre, Comparator.nullsLast(String::compareTo)))
				.toList();

		List<GrupoCategoria> categorias = porCategoria.entrySet().stream()
				.map(e -> new GrupoCategoria(e.getKey(), e.getValue().size(), e.getValue())).toList();

		List<String> avisos = grupos.stream()
				.flatMap(g -> g.filas().stream().filter(Fila::dorsalRepetido)
						.map(f -> "Dorsal " + f.dorsal() + " repetido en " + g.equipoNombre()))
				.distinct().toList();

		List<SinPropuesta> sinPropuesta = jugadorTemporadaDao.jugadoresActivosSinFila(destinoId).stream()
				.map(j -> new SinPropuesta(j.getId(), nombre(j.getNombre(), j.getApellidos()), j.getAnioNacimiento(),
						j.getEquipo()))
				.toList();

		return new RevisionTemporadaDTO(destino, grupos, categorias, todas.stream().filter(Fila::pendiente).toList(),
				todas.stream().filter(f -> !f.baja() && f.equipoId() == null && f.categoria() == null).toList(),
				todas.stream().filter(Fila::baja).toList(), sinPropuesta, avisos, todas.size());
	}

	private static String nombre(String nombre, String apellidos) {
		return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
	}

	@Override
	@Transactional
	public void guardarFila(Long destinoId, Long jugadorId, Long equipoId, Integer dorsal, boolean baja,
			Integer anioNacimiento) {
		validarDestino(destinoId);

		JugadorTemporada fila = jugadorTemporadaDao.obtener(jugadorId, destinoId);
		if (fila == null) {
			throw new IllegalArgumentException("El jugador no tiene fila en esta temporada.");
		}

		String errorAnio = AnioNacimiento.mensajeError(anioNacimiento, LocalDate.now(clock).getYear());
		if (errorAnio != null) {
			throw new IllegalArgumentException(errorAnio);
		}
		if (dorsal != null && (dorsal < 0 || dorsal > 99)) {
			throw new IllegalArgumentException("El dorsal debe estar entre 0 y 99.");
		}

		if (equipoId != null) {
			Equipo equipo = buscarEquipo(equipoId);
			if (equipo == null) {
				throw new IllegalArgumentException("El equipo seleccionado no existe.");
			}
			copiarEquipo(fila, equipo);
			fila.setEquipoDefinitivo(true);
			fila.setDorsal(dorsal);
		} else if (!baja) {
			// Sin equipo: entrena en su categoría, sin dorsal y sin equipo definitivo. Con el año de nacimiento se
			// recalcula la categoría (por si se acaba de rellenar).
			fila.setEquipoId(null);
			fila.setEquipoNombre(null);
			fila.setEquipoDefinitivo(false);
			fila.setDorsal(null);
			fila.setCategoria(categoriaSinEquipo(fila, anioNacimiento, destinoId));
		} else {
			fila.setDorsal(dorsal);
		}

		fila.setBaja(baja);
		fila.setPendienteRevision(false);
		jugadorTemporadaDao.guardar(fila);

		// El año de nacimiento se guarda en JUGADORES (null = no se conoce).
		jugadorDao.actualizarAnioNacimiento(jugadorId, anioNacimiento);
	}

	/** La categoría por edad si se conoce el año y una categoría formativa lo cubre; si no, la que ya tenía. */
	private String categoriaSinEquipo(JugadorTemporada fila, Integer anioNacimiento, Long destinoId) {
		if (anioNacimiento == null) {
			return fila.getCategoria();
		}

		Temporada destino = temporadaDao.obtenerPorId(destinoId);
		CategoriaPorEdadResultado porEdad = categoriaPorEdad.categoriaPara(anioNacimiento,
				categoriaPorEdad.anioInicio(destino));

		return porEdad.estado() != Estado.SUPERA_EDAD_MAXIMA && porEdad.tieneCategoria() ? porEdad.categoria()
				: fila.getCategoria();
	}

	private Equipo buscarEquipo(Long equipoId) {
		try {
			return equipoDao.obtenerEquipoPorId(equipoId);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return null;
		}
	}

	// ============================================================
	// Cerrar temporada
	// ============================================================

	/** Tipo del aviso de cambio de temporada: respeta el interruptor general y el de noticias de cada usuario. */
	static final String TIPO_AVISO = "NOTICIA";

	/** Modalidad del equipo de pretemporada de cada categoría (sin tildes ni mayúsculas). */
	private static final Map<String, String> MODALIDAD_PRETEMPORADA = Map.of("escuelita", "F5", "prebenjamin", "F8",
			"benjamin", "F8", "alevin", "F8", "infantil", "F11", "cadete", "F11", "juvenil", "F11");

	/** Grupo del equipo de pretemporada: no choca con los grupos en uso (A, B, C, D, Fem, Esc). */
	static final String GRUPO_PRETEMPORADA = "P";

	private static String clave(String categoria) {
		return categoria == null ? "" : categoria.trim().toLowerCase();
	}

	private static String modalidadPretemporada(String categoria) {
		String sinAcentos = java.text.Normalizer.normalize(clave(categoria), java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return MODALIDAD_PRETEMPORADA.getOrDefault(sinAcentos, "F11");
	}

	static String nombrePretemporada(String categoria) {
		return categoria.trim() + " (Pretemporada)";
	}

	private static LocalDate aLocalDate(java.util.Date fecha) {
		return fecha instanceof java.sql.Date sql ? sql.toLocalDate()
				: fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
	}

	/** Las filas del borrador según lo que les va a pasar al cerrar. */
	private record Reparto(List<JugadorTemporada> bajas, List<JugadorTemporada> pruebas,
			List<JugadorTemporada> formativos, List<JugadorTemporada> adultos, List<JugadorTemporada> sinEquipo,
			int yaDeBaja) {

		/** Las categorías (primera grafía vista) de los jugadores que van a una pretemporada. */
		Map<String, String> categoriasDePretemporada() {
			Map<String, String> categorias = new LinkedHashMap<>();
			Stream.concat(formativos.stream(), pruebas.stream())
					.forEach(f -> categorias.putIfAbsent(clave(f.getCategoria()), f.getCategoria().trim()));
			return categorias;
		}
	}

	private Reparto repartir(List<JugadorTemporada> filas, Set<String> categoriasConEdad) {
		List<JugadorTemporada> bajas = new ArrayList<>();
		List<JugadorTemporada> pruebas = new ArrayList<>();
		List<JugadorTemporada> formativos = new ArrayList<>();
		List<JugadorTemporada> adultos = new ArrayList<>();
		List<JugadorTemporada> sinEquipo = new ArrayList<>();
		int yaDeBaja = 0;

		for (JugadorTemporada f : filas) {
			if ("PRUEBA".equals(f.getEstado())) {
				pruebas.add(f);
			} else if (f.isJugadorDeBaja()) {
				// Se dio de baja desde el panel mientras se preparaba el borrador: sigue de baja.
				yaDeBaja++;
			} else if (f.isBaja()) {
				bajas.add(f);
			} else if (f.getCategoria() != null && categoriasConEdad.contains(clave(f.getCategoria()))) {
				// Aunque el coordinador le haya elegido equipo en el borrador: el reparto es en septiembre
				formativos.add(f);
			} else if (f.getEquipoId() != null) {
				adultos.add(f);
			} else {
				sinEquipo.add(f);
			}
		}

		return new Reparto(bajas, pruebas, formativos, adultos, sinEquipo, yaDeBaja);
	}

	private Set<String> categoriasConEdad() {
		return categoriaPorEdad.obtenerTodas().stream().map(c -> clave(c.getCategoria())).collect(Collectors.toSet());
	}

	@Override
	public ResumenCierreDTO resumenCierre(Long destinoId) {
		Temporada destino = validarDestino(destinoId);
		Temporada origen = temporadaDao.obtenerTemporadaActiva();
		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(destinoId);

		List<String> bloqueos = new ArrayList<>();
		int sinFila = jugadorTemporadaDao.jugadoresActivosSinFila(destinoId).size();
		if (sinFila > 0) {
			bloqueos.add(sinFila + " jugador(es) activo(s) sin fila en " + destino.getNombre()
					+ ": añádelos desde \"Sin propuesta\".");
		}
		int pendientes = jugadorTemporadaDao.contarPendientes(destinoId);
		if (pendientes > 0) {
			bloqueos.add(pendientes + " fila(s) pendiente(s) de revisar (incluye la continuidad sin resolver).");
		}
		long sinAnio = jugadorDao.obtenerActivos().stream().filter(j -> j.getAnioNacimiento() == null).count();
		if (sinAnio > 0) {
			bloqueos.add(sinAnio + " jugador(es) activo(s) sin año de nacimiento: rellénalo en la revisión.");
		}

		Reparto reparto = repartir(filas, categoriasConEdad());

		// Cómo quedan al terminar: los adultos en su equipo y los formativos (y las pruebas) en su pretemporada
		Map<String, Integer> porEquipo = new TreeMap<>();
		reparto.adultos().forEach(f -> porEquipo.merge(f.getEquipoNombre(), 1, Integer::sum));
		Stream.concat(reparto.formativos().stream(), reparto.pruebas().stream())
				.forEach(f -> porEquipo.merge(nombrePretemporada(f.getCategoria()), 1, Integer::sum));

		Set<String> equiposExistentes = equipoDao.obtenerTodos().stream().map(e -> clave(e.getNombre()))
				.collect(Collectors.toSet());
		List<String> nuevos = new ArrayList<>();
		List<String> reutilizados = new ArrayList<>();
		reparto.categoriasDePretemporada().values().forEach(categoria -> {
			String nombre = nombrePretemporada(categoria);
			(equiposExistentes.contains(clave(nombre)) ? reutilizados : nuevos).add(nombre);
		});

		CifrasCierre c = cierreDao.cifras(origen.getId(), destinoId, aLocalDate(destino.getFechaInicio()));
		ResumenCierreDTO.Pasos pasos = new ResumenCierreDTO.Pasos(c.fotoCuerpoTecnico(), c.equiposAdultos(),
				c.equiposInactivos(), nuevos, reutilizados, reparto.bajas().size(), reparto.pruebas().size(),
				reparto.formativos().size(), reparto.adultos().size(), c.cuerpoTecnicoDesasignado(),
				c.horariosActivos(), c.horariosActivos(), c.sesionesABorrar(), c.conceptosACopiar(),
				c.notificaciones(), c.usuariosActivos());

		return new ResumenCierreDTO(destino.getNombre(), origen.getNombre(), porEquipo, reparto.bajas().size(),
				reparto.sinEquipo().size(), pasos, bloqueos);
	}

	@Override
	public void cerrar(Long destinoId, String confirmacion) {
		Temporada destino = validarDestino(destinoId);

		if (confirmacion == null || !destino.getNombre().trim().equals(confirmacion.trim())) {
			throw new IllegalArgumentException("Para confirmar, escribe el nombre de la temporada: " + destino.getNombre());
		}

		Temporada origen = temporadaDao.obtenerTemporadaActiva();

		// Pasos 1 a 8: todo o nada
		new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
			ResumenCierreDTO resumen = resumenCierre(destinoId);
			if (!resumen.puedeCerrar()) {
				throw new IllegalStateException("No se puede cerrar: " + String.join(" ", resumen.bloqueos()));
			}

			ejecutarCierre(origen, destino);
		});

		// Paso 9: solo con el cierre ya confirmado en la base de datos; si falla, el cierre no se deshace
		avisarCambioDeTemporada(destino);
	}

	private void ejecutarCierre(Temporada origen, Temporada destino) {
		LocalDate hoy = LocalDate.now(clock);
		List<JugadorTemporada> filas = jugadorTemporadaDao.listarPorTemporada(destino.getId());
		Reparto reparto = repartir(filas, categoriasConEdad());

		// 1. Foto del cuerpo técnico de la temporada que se cierra
		int foto = cierreDao.fotografiarCuerpoTecnico(origen.getId());

		// 2. Equipos de la temporada nueva y un equipo de pretemporada por categoría formativa con jugadores
		cierreDao.copiarEquipos(origen.getId(), destino.getId());
		List<Equipo> equipos = equipoDao.obtenerTodos();
		Map<String, Equipo> pretemporada = new HashMap<>();
		reparto.categoriasDePretemporada().forEach((clave, categoria) -> {
			String nombre = nombrePretemporada(categoria);
			String modalidad = modalidadPretemporada(categoria);
			Long id = cierreDao.obtenerOCrearEquipo(nombre, categoria, GRUPO_PRETEMPORADA,
					ordenDe(categoria, equipos), "F", modalidad);
			cierreDao.activarPretemporada(id, destino.getId(), modalidad);
			pretemporada.put(clave, new Equipo(id, categoria, GRUPO_PRETEMPORADA, null, nombre, "F"));
		});

		// 3. El borrador: bajas, pruebas y formativos a su pretemporada; los adultos se quedan
		reparto.bajas().forEach(f -> jugadorDao.darDeBaja(f.getJugadorId(), hoy));

		for (JugadorTemporada f : reparto.pruebas()) {
			// Deja de estar oculto y entra en la pretemporada de su categoría; su fila sigue siendo PRUEBA
			jugadorDao.activarSinEquipo(f.getJugadorId(), f.getCategoria());
			enPretemporada(f, pretemporada);
		}

		reparto.formativos().forEach(f -> enPretemporada(f, pretemporada));

		for (JugadorTemporada f : reparto.adultos()) {
			Equipo equipo = equipoDao.obtenerEquipoPorId(f.getEquipoId());
			jugadorDao.asignarPlantilla(f.getJugadorId(), equipo.getId(), equipo.getNombre(), equipo.getCategoria(),
					equipo.getDeporte(), f.getDorsal());
		}

		reparto.sinEquipo().forEach(f -> jugadorDao.asignarPlantilla(f.getJugadorId(), null, null, null, null,
				f.getDorsal()));

		// 4. Cuerpo técnico: se desasigna salvo el de los equipos de adultos
		int desasignados = cierreDao.desasignarCuerpoTecnico();

		// 5. Horarios: se copian a la nueva temporada (desactivados), se desactivan los viejos y se limpian las sesiones
		int horarios = cierreDao.copiarHorariosActivos(origen.getId(), destino.getId());
		cierreDao.desactivarHorarios(origen.getId());
		int sesiones = cierreDao.eliminarSesionesSinEntrenamiento(aLocalDate(destino.getFechaInicio()));

		// 6. Conceptos de pago
		int conceptos = cierreDao.copiarConceptosActivos(origen.getId(), destino.getId());

		// 7. Notificaciones de la app
		int notificaciones = cierreDao.eliminarNotificaciones();

		// 8. La temporada nueva pasa a ser la activa
		temporadaDao.desactivarOtrasTemporadas(destino.getId());
		if (!temporadaDao.activar(destino.getId())) {
			throw new IllegalStateException("No se ha podido activar la temporada " + destino.getNombre() + ".");
		}

		logger.info("Temporada cerrada: origen={}, destino={}, bajas={}, pruebas={}, formativos={}, adultos={}, "
				+ "yaDeBaja={}, fotoCuerpoTecnico={}, cuerpoTecnicoDesasignado={}, horariosCopiados={}, "
				+ "sesionesBorradas={}, conceptosCopiados={}, notificacionesBorradas={}", origen.getNombre(),
				destino.getNombre(), reparto.bajas().size(), reparto.pruebas().size(), reparto.formativos().size(),
				reparto.adultos().size(), reparto.yaDeBaja(), foto, desasignados, horarios, sesiones, conceptos,
				notificaciones);
	}

	/** El jugador pasa al equipo de pretemporada de su categoría, sin dorsal y sin equipo definitivo. */
	private void enPretemporada(JugadorTemporada fila, Map<String, Equipo> pretemporada) {
		Equipo equipo = pretemporada.get(clave(fila.getCategoria()));

		jugadorDao.asignarPlantilla(fila.getJugadorId(), equipo.getId(), equipo.getNombre(), equipo.getCategoria(),
				equipo.getDeporte(), null);

		fila.setEquipoId(equipo.getId());
		fila.setEquipoNombre(equipo.getNombre());
		fila.setDorsal(null);
		fila.setEquipoDefinitivo(false);
		jugadorTemporadaDao.guardar(fila);
	}

	/** ORDEN de la categoría (la letra de EnumEquipos) o, si no es una categoría conocida, la de sus equipos. */
	private static String ordenDe(String categoria, List<Equipo> equipos) {
		try {
			return EnumEquipos.getOrdenByCategoria(categoria);
		} catch (IllegalArgumentException e) {
			return equipos.stream().filter(eq -> mismaCategoria(eq.getCategoria(), categoria))
					.map(Equipo::getOrden).filter(Objects::nonNull).findFirst().orElse("X");
		}
	}

	private void avisarCambioDeTemporada(Temporada destino) {
		try {
			notificacionAppService.difundirATodos(TIPO_AVISO, "Nueva temporada",
					"Empieza la temporada " + destino.getNombre() + ". Los equipos se publicarán en septiembre.", null);
		} catch (RuntimeException e) {
			logger.error("La temporada {} está cerrada, pero ha fallado el aviso a los usuarios de la app: {}",
					destino.getNombre(), e.getMessage(), e);
		}
	}
}

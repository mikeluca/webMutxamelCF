package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.model.Clasificacion;
import com.mikedev.mutxamelcf.model.CompeticionVeteranosPrevia;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.FilaClasificacionVeteranos;
import com.mikedev.mutxamelcf.model.PrevisualizacionVeteranos;
import com.mikedev.mutxamelcf.model.ResultadoImportacionVeteranos;
import com.mikedev.mutxamelcf.service.ClubRivalService;
import com.mikedev.mutxamelcf.service.ImportacionVeteranosService;

/**
 * Importación de la clasificación de la LVCA (ver {@link ClasificacionLvcaParser} para la estructura de la página).
 *
 * <ul>
 * <li>Solo se importan las tablas en las que aparece el Mutxamel (hoy, la de la Liga y la de "Copa grupo C"): el resto se
 * ignora.
 * <li>Cada una es una clasificación del equipo Veteranos, con el nombre de la pestaña como COMPETICION. Un equipo puede
 * tener varias (no hay ninguna restricción por equipo en CLASIFICACIONES).
 * <li>La web no da la jornada: se guarda como JORNADA el máximo de PJ de la tabla.
 * <li>Los clubes rivales se enlazan por alias (sin código de club de la FFCV): si no existe el club se crea sin escudo,
 * para subir el escudo desde "Escudos de rivales". La URL del escudo la resuelve la clasificación pública por ese alias.
 * </ul>
 */
@Service
public class ImportacionVeteranosServiceImpl implements ImportacionVeteranosService {

	private static final Logger logger = LoggerFactory.getLogger(ImportacionVeteranosServiceImpl.class);

	private final ClasificacionDao clasificacionDao;
	private final EquipoDao equipoDao;
	private final ClubRivalService clubRivalService;

	public ImportacionVeteranosServiceImpl(
			ClasificacionDao clasificacionDao,
			EquipoDao equipoDao,
			ClubRivalService clubRivalService) {
		this.clasificacionDao = clasificacionDao;
		this.equipoDao = equipoDao;
		this.clubRivalService = clubRivalService;
	}

	@Override
	@Transactional(readOnly = true)
	public PrevisualizacionVeteranos previsualizar(byte[] archivo) {
		logger.debug("Inicio previsualizar veteranos: bytes={}", archivo != null ? archivo.length : 0);

		List<ClasificacionLvcaParser.Competicion> leidas = ClasificacionLvcaParser.parsear(archivo);

		if (leidas.isEmpty()) {
			throw new IllegalArgumentException("El Mutxamel no aparece en ninguna clasificación de la página");
		}

		Equipo veteranos = equipoVeteranos();

		Set<String> clubesTodos = new LinkedHashSet<>();
		List<CompeticionVeteranosPrevia> competiciones = new ArrayList<>();

		for (ClasificacionLvcaParser.Competicion leida : leidas) {

			List<FilaClasificacionVeteranos> tabla = leida.filas().stream()
					.sorted(Comparator.comparingInt(FilaClasificacionVeteranos::posicion)
							.thenComparing(FilaClasificacionVeteranos::equipo, String.CASE_INSENSITIVE_ORDER))
					.toList();

			Set<String> clubes = new LinkedHashSet<>();

			for (FilaClasificacionVeteranos fila : tabla) {
				if (!fila.esNuestro() && clubRivalService.necesitaClubNuevo(fila.equipo())) {
					clubes.add(fila.equipo());
				}
			}

			clubesTodos.addAll(clubes);

			int jornada = tabla.stream().mapToInt(FilaClasificacionVeteranos::pj).max().orElse(0);
			int posicionNuestra = tabla.stream().filter(FilaClasificacionVeteranos::esNuestro).findFirst()
					.map(FilaClasificacionVeteranos::posicion).orElse(0);

			competiciones.add(new CompeticionVeteranosPrevia(leida.nombre(), jornada, posicionNuestra, tabla,
					List.copyOf(clubes)));
		}

		logger.debug("Fin previsualizar veteranos: competiciones={}, clubesACrear={}", competiciones.size(),
				clubesTodos.size());

		return new PrevisualizacionVeteranos(veteranos.getId(), veteranos.getNombre(), competiciones,
				List.copyOf(clubesTodos));
	}

	@Override
	@Transactional
	public ResultadoImportacionVeteranos confirmar(String usuario, PrevisualizacionVeteranos previa) {
		logger.debug("Inicio confirmar veteranos");

		if (previa == null || previa.competiciones() == null || previa.competiciones().isEmpty()) {
			throw new IllegalArgumentException("No hay ninguna vista previa que confirmar");
		}

		// El equipo puede haber cambiado desde la vista previa.
		Equipo veteranos = equipoVeteranos();

		if (!veteranos.getId().equals(previa.equipoId())) {
			throw new IllegalArgumentException(
					"El equipo Veteranos ha cambiado desde la vista previa: vuelve a subir la página");
		}

		LocalDateTime ahora = LocalDateTime.now();
		Set<String> creados = new LinkedHashSet<>();
		List<String> nombres = new ArrayList<>();
		int filasInsertadas = 0;

		for (CompeticionVeteranosPrevia competicion : previa.competiciones()) {

			List<Clasificacion> nuevas = new ArrayList<>(competicion.tabla().size());

			for (FilaClasificacionVeteranos fila : competicion.tabla()) {
				nuevas.add(aClasificacion(fila, veteranos.getId(), competicion, ahora));
			}

			// Solo se sustituye esta competición: las demás del equipo no se tocan.
			clasificacionDao.eliminarPorEquipoYCompeticion(veteranos.getId(), competicion.nombre());
			clasificacionDao.insertarTodas(nuevas);

			// Clubes rivales: se anotan los que faltan antes de crearlos y luego se aseguran todos (alias incluidos).
			for (FilaClasificacionVeteranos fila : competicion.tabla()) {

				if (fila.esNuestro()) {
					continue;
				}

				if (clubRivalService.necesitaClubNuevo(fila.equipo())) {
					creados.add(fila.equipo());
				}

				clubRivalService.asegurarDeClasificacion(null, fila.equipo(), false);
			}

			nombres.add(competicion.nombre());
			filasInsertadas += nuevas.size();
		}

		// Privacidad: solo el usuario del panel o de la app y contadores.
		logger.info("Importación de veteranos confirmada: usuario={}, competiciones={}, filas={}, clubesCreados={}",
				usuario, nombres.size(), filasInsertadas, creados.size());

		return new ResultadoImportacionVeteranos(nombres.size(), filasInsertadas, nombres, List.copyOf(creados));
	}

	private Equipo equipoVeteranos() {

		Equipo equipo = equipoDao.obtenerEquipoPorNombre(EQUIPO_VETERANOS);

		if (equipo == null) {
			throw new IllegalArgumentException("No existe el equipo \"" + EQUIPO_VETERANOS
					+ "\": créalo en Equipos antes de importar la clasificación de la liga de veteranos");
		}

		return equipo;
	}

	private static Clasificacion aClasificacion(
			FilaClasificacionVeteranos fila,
			Long equipoId,
			CompeticionVeteranosPrevia competicion,
			LocalDateTime ahora) {

		Clasificacion c = new Clasificacion();
		c.setEquipoId(equipoId);
		c.setCompeticion(competicion.nombre());
		// La web no da la jornada: el máximo de PJ de la tabla.
		c.setJornada(competicion.jornada());
		c.setPosicion(fila.posicion());
		c.setNombreEquipo(fila.equipo());
		c.setLetra(null);
		c.setEsNuestro(fila.esNuestro());
		c.setPuntos(fila.puntos());
		c.setPj(fila.pj());
		c.setPg(fila.pg());
		c.setPe(fila.pe());
		c.setPp(fila.pp());
		c.setGf(fila.gf());
		c.setGc(fila.gc());
		c.setFechaImportacion(ahora);
		return c;
	}
}

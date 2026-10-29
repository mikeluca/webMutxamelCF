package com.mikedev.mutxamelcf.dao;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.CuentaSinJugadores;

/** Consultas y marcas de las cuentas de familia sin ningún jugador activo (docs/transicion-temporada.md, decisión 27). */
public interface CuentaSinJugadoresDao {

	/**
	 * Cuentas no eliminadas con rol FAMILIAR o JUGADOR y ningún otro rol (ENTRENADOR, COORDINADOR, ADMIN_APP,
	 * RETRANSMISION) cuyos jugadores vinculados, directos o por un familiar, están todos de baja (o no tiene ninguno).
	 * Un jugador con una fila PRUEBA en una temporada que no ha terminado en {@code hoy} cuenta como activo aunque esté
	 * oculto.
	 */
	List<CuentaSinJugadores> listarSinJugadoresActivos(LocalDate hoy);

	/** Cuentas no eliminadas con un aviso guardado o desactivadas por este motivo, tengan o no jugadores ahora. */
	List<CuentaSinJugadores> listarMarcadas();

	/** Guarda la fecha del aviso si la cuenta está activa y todavía no tiene ninguno. 1 si lo guardó. */
	int marcarAviso(long usuarioId, Timestamp fecha);

	/** Desactiva una cuenta activa con aviso y anota que fue por este motivo. 1 si la desactivó. */
	int desactivar(long usuarioId);

	/** Borra el aviso de una cuenta que sigue activa. 1 si había algo que borrar. */
	int cancelarAviso(long usuarioId);

	/** Reactiva una cuenta que desactivó el job y borra sus marcas. 0 si no la desactivó el job. */
	int reactivar(long usuarioId);
}

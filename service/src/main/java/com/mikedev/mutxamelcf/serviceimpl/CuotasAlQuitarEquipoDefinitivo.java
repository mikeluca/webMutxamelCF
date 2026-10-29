package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.service.AlQuitarEquipoDefinitivo;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService;
import com.mikedev.mutxamelcf.service.CuotasTemporadaService.ResultadoRetiradaCuotas;

/**
 * Al devolver a un jugador a la pretemporada se borran sus cuotas de la temporada activa que no tienen ningún pago.
 * Las que sí tienen pagos no se tocan y se avisa en la respuesta del reparto.
 */
@Component
public class CuotasAlQuitarEquipoDefinitivo implements AlQuitarEquipoDefinitivo {

	private final CuotasTemporadaService cuotasTemporadaService;
	private final JugadorDao jugadorDao;

	public CuotasAlQuitarEquipoDefinitivo(CuotasTemporadaService cuotasTemporadaService, JugadorDao jugadorDao) {
		this.cuotasTemporadaService = cuotasTemporadaService;
		this.jugadorDao = jugadorDao;
	}

	@Override
	public List<String> alQuitar(QuitaDefinitiva quita) {
		ResultadoRetiradaCuotas resultado = cuotasTemporadaService.retirarCuotasDelJugador(quita.jugadorId());

		if (resultado.conservadasConPagos() == 0) {
			return List.of();
		}

		Jugador jugador = jugadorDao.obtenerPorId(quita.jugadorId());
		String nombre = jugador == null ? "El jugador " + quita.jugadorId()
				: ((jugador.getNombre() == null ? "" : jugador.getNombre()) + " "
						+ (jugador.getApellidos() == null ? "" : jugador.getApellidos())).trim();

		return List.of(nombre + " tiene " + resultado.conservadasConPagos()
				+ (resultado.conservadasConPagos() == 1 ? " cuota con pagos que no se ha borrado"
						: " cuotas con pagos que no se han borrado")
				+ ": revísalas en Pagos.");
	}
}

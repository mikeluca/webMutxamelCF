package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.EquipoCoordinacion;
import com.mikedev.mutxamelcf.model.IncidenciaEntrenamiento;
import com.mikedev.mutxamelcf.model.PartidoCoordinacion;
import com.mikedev.mutxamelcf.model.SeguimientoJugador;

/** Consultas agregadas para el panel de coordinación. */
public interface CoordinacionDao {

    /** Partidos (también cancelados) con DIA entre las fechas, por EQUIPO.ORDEN, equipo y día. */
    List<PartidoCoordinacion> partidosEntre(LocalDate desde, LocalDate hasta);

    /** Descansos con FECHA entre las fechas, por EQUIPO.ORDEN. */
    List<EquipoCoordinacion> descansosEntre(LocalDate desde, LocalDate hasta);

    /** Equipos con alguna relación en EQUIPOS_FFCV, por EQUIPO.ORDEN. */
    List<EquipoCoordinacion> equiposConRelacionFfcv();

    /** La actualización más reciente de un partido con ORIGEN = 'FFCV'; null si no hay. */
    LocalDateTime ultimaImportacionFfcv();

    /**
     * Jugadores con al menos {@code umbralFaltas} FALTA o {@code umbralMalComportamiento}
     * MAL_COMPORTAMIENTO en entrenamientos con FECHA entre las fechas. Una sola consulta
     * con GROUP BY; las FALTA_JUSTIFICADA se cuentan aparte.
     */
    List<SeguimientoJugador> seguimientoJugadores(
            LocalDate desde, LocalDate hasta, int umbralFaltas, int umbralMalComportamiento);

    /** El jugador con sus contadores del periodo (aunque no llegue a los umbrales); null si no existe. */
    SeguimientoJugador seguimientoDeJugador(Long jugadorId, LocalDate desde, LocalDate hasta);

    /** Asistencias distintas de PRESENTE del jugador en el periodo, de más reciente a más antigua. */
    List<IncidenciaEntrenamiento> incidenciasDeJugador(Long jugadorId, LocalDate desde, LocalDate hasta);
}

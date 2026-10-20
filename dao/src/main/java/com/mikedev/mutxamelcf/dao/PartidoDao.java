package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mikedev.mutxamelcf.model.Partido;

public interface PartidoDao {

    Partido crear(Partido partido);

    void actualizar(Partido partido);

    Partido obtenerPorId(Long id);

    List<Partido> obtenerPorEquipo(Long equipoId);

    List<Partido> obtenerUltimosPorEquipo(Long equipoId, int limite);

    /**
     * Partidos de un equipo que todavía no tienen una convocatoria
     * asociada (para el selector de "crear convocatoria a partir de un
     * partido"), ordenados por fecha ascendente (los próximos primero).
     *
     * @param equipoId          equipo cuyos partidos se listan.
     * @param incluirPartidoId  id de un partido a incluir en el listado
     *                          aunque ya tenga convocatoria (usado al
     *                          editar una convocatoria existente, para
     *                          que el partido ya vinculado siga siendo
     *                          seleccionable); puede ser {@code null}.
     */
    List<Partido> obtenerPartidosSinConvocatoria(Long equipoId, Long incluirPartidoId);

    Partido obtenerMasRelevantePorEquipoNombre(String equipoNombre, String categoria);

    Partido obtenerMasRelevantePorEquipo(Long equipoId);

    /**
     * Partidos de un equipo cuya fecha (DIA) cae dentro del rango
     * indicado (ambos extremos incluidos), usado por el endpoint
     * combinado de calendario. Incluye partidos cancelados: el
     * consumidor decide cómo mostrarlos a partir del campo cancelado.
     */
    List<Partido> obtenerPorEquipoYRangoFechas(Long equipoId, LocalDate desde, LocalDate hasta);

    /**
     * Todos los partidos (de cualquier equipo) cuya fecha cae en el rango
     * indicado, ambos extremos incluidos. Usado por el importador FFCV.
     */
    List<Partido> obtenerPorRangoFechas(LocalDate desde, LocalDate hasta);

    /**
     * Actualiza solo rival, hora, campo y jornada de un partido de origen
     * FFCV. Nunca toca resultado, goles ni cancelación.
     */
    void actualizarDatosFfcv(Partido partido);

    /**
     * Marca FECHA_IMPORTACION = ahora en esos partidos de origen FFCV (los que procesa
     * la confirmación del importador, hayan cambiado o no). FECHA_ACTUALIZACION no se
     * toca: significa solo "editado a mano".
     */
    void marcarFechaImportacion(java.util.Collection<Long> ids);

    void eliminar(Long id);

    /**
     * Marca un partido como cancelado (CANCELADO = 1), sin borrarlo, para
     * conservar su histórico y la convocatoria que pudiera tener asociada.
     */
    void cancelar(Long id);

    /**
     * Partidos no cancelados cuyo inicio (DIA + HORA válida, o DIA a
     * medianoche) está entre {@code desde} y {@code hasta}, sin resultado
     * por ninguna vía (ni goles ni texto de resultado) y sin recordatorio
     * enviado (AVISO_RESULTADO_ENVIADO = 0).
     * Usado por RecordatorioResultadoPartidoScheduler.
     */
    List<Partido> obtenerPendientesDeAvisoResultado(LocalDateTime desde, LocalDateTime hasta);

    /**
     * Marca como avisados (sin enviar nada) los partidos sin resultado cuyo
     * inicio es anterior a {@code limite}, para que una importación o un
     * alta tardía no dispare avisos de hace semanas.
     *
     * @return filas afectadas
     */
    int marcarAvisosAntiguosComoEnviados(LocalDateTime limite);

    /**
     * Marca que ya se ha enviado el recordatorio de resultado pendiente
     * para este partido (AVISO_RESULTADO_ENVIADO = 1), para que no se
     * vuelva a enviar.
     */
    void marcarAvisoResultadoEnviado(Long id);

    /**
     * Pone AVISO_RESULTADO_ENVIADO a 1 o a 0 (para devolver el partido a como
     * estaba al deshacer el final de un directo).
     */
    void actualizarAvisoResultadoEnviado(Long id, boolean enviado);

    /**
     * Bloquea la fila del partido (SELECT ... FOR UPDATE) hasta el fin de la
     * transacción actual, para serializar los eventos del directo de un mismo
     * partido. Debe llamarse dentro de una transacción.
     */
    void bloquearPorId(Long id);

}

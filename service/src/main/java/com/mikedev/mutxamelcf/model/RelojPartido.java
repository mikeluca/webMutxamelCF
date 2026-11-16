package com.mikedev.mutxamelcf.model;

/**
 * Reloj en directo del partido (portada, ficha y estado de la retransmisión); el objeto es null salvo en juego o en
 * el descanso. La app pinta {@code minutoBase + (ahora - inicioParte)} y corrige el desfase de su reloj con
 * {@code ahoraServidorEpochMs}.
 *
 * @param fase PRIMERA_PARTE, DESCANSO o SEGUNDA_PARTE
 * @param inicioParteEpochMs instante (epoch ms) del INICIO (1.ª parte) o de SEGUNDA_PARTE (2.ª), el último no
 *                           anulado; null en el descanso
 * @param minutoBase 0 en la 1.ª parte; la duración de una parte en la 2.ª y en el descanso
 * @param minutosParte duración de cada parte según la modalidad del equipo (retransmision.minutos-parte.*)
 * @param ahoraServidorEpochMs hora del servidor en epoch ms
 */
public record RelojPartido(
        String fase, Long inicioParteEpochMs, int minutoBase, int minutosParte, long ahoraServidorEpochMs) {
}

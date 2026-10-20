package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.TarjetasJugadorResponse;

/**
 * Cuándo una cantidad de amarillas acumuladas es una posible sanción o está a
 * punto de serlo. Se configura en application.properties:
 *
 * <ul>
 * <li>{@code sancion.amarillas.ciclo} (5): cada cuántas amarillas hay posible
 * sanción (5, 10, 15...).</li>
 * <li>{@code sancion.amarillas.aviso-previo} (1): a cuántas amarillas de ella se
 * avisa (con 1 y ciclo 5: con 4, 9, 14...).</li>
 * </ul>
 */
@Component
public class ReglaSancionAmarillas {

    private static final Logger logger = LoggerFactory.getLogger(ReglaSancionAmarillas.class);

    static final int CICLO_POR_DEFECTO = 5;
    static final int AVISO_PREVIO_POR_DEFECTO = 1;

    private final int ciclo;
    private final int avisoPrevio;

    public ReglaSancionAmarillas(
            @Value("${sancion.amarillas.ciclo:5}") int ciclo,
            @Value("${sancion.amarillas.aviso-previo:1}") int avisoPrevio) {

        if (ciclo < 2) {
            logger.warn("sancion.amarillas.ciclo={} no es válido: se usa {}", ciclo, CICLO_POR_DEFECTO);
            ciclo = CICLO_POR_DEFECTO;
        }

        if (avisoPrevio < 1 || avisoPrevio >= ciclo) {
            logger.warn("sancion.amarillas.aviso-previo={} no es válido para un ciclo de {}: se usa {}",
                    avisoPrevio, ciclo, Math.min(AVISO_PREVIO_POR_DEFECTO, ciclo - 1));
            avisoPrevio = Math.min(AVISO_PREVIO_POR_DEFECTO, ciclo - 1);
        }

        this.ciclo = ciclo;
        this.avisoPrevio = avisoPrevio;
    }

    /** SANCION_POSIBLE, A_UNA u OK para ese total de amarillas. */
    public String estado(int amarillas) {

        if (esSancion(amarillas)) {
            return TarjetasJugadorResponse.SANCION_POSIBLE;
        }

        if (esAvisoPrevio(amarillas)) {
            return TarjetasJugadorResponse.A_UNA;
        }

        return TarjetasJugadorResponse.OK;
    }

    /** Alguna cantidad de amarillas con posible sanción en (antes, despues]. */
    public boolean cruzaSancion(int antes, int despues) {

        for (int amarillas = antes + 1; amarillas <= despues; amarillas++) {
            if (esSancion(amarillas)) {
                return true;
            }
        }

        return false;
    }

    /** La mayor cantidad de amarillas con posible sanción en (antes, despues]; 0 si ninguna. */
    public int umbralSancionCruzado(int antes, int despues) {

        for (int amarillas = despues; amarillas > antes; amarillas--) {
            if (esSancion(amarillas)) {
                return amarillas;
            }
        }

        return 0;
    }

    /** Alguna cantidad de amarillas "a una de la sanción" en (antes, despues]. */
    public boolean cruzaAvisoPrevio(int antes, int despues) {

        for (int amarillas = antes + 1; amarillas <= despues; amarillas++) {
            if (esAvisoPrevio(amarillas) && !esSancion(amarillas)) {
                return true;
            }
        }

        return false;
    }

    public int ciclo() {
        return ciclo;
    }

    private boolean esSancion(int amarillas) {
        return amarillas > 0 && amarillas % ciclo == 0;
    }

    private boolean esAvisoPrevio(int amarillas) {
        return amarillas > 0 && (amarillas + avisoPrevio) % ciclo == 0;
    }
}

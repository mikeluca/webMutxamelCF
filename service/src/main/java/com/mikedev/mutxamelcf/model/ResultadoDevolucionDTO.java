package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * @param devueltos              jugadores que vuelven a la pretemporada
 * @param pretemporadaReactivada el equipo de pretemporada estaba desactivado (vacío) y vuelve a estar activo
 * @param avisos                 lo que no se ha podido deshacer (p. ej. cuotas con pagos que se conservan)
 */
public record ResultadoDevolucionDTO(int devueltos, boolean pretemporadaReactivada, List<String> avisos) {
}

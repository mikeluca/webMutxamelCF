package com.mikedev.mutxamelcf.serviceimpl;

/** La URL del escudo de un club rival, la misma para todas las respuestas de la app. */
public final class EscudosRivales {

    public static final String RUTA = "/api/public/escudos-rivales/";

    private EscudosRivales() {
    }

    /**
     * Ruta relativa a la URL base del servidor: {@code /api/public/escudos-rivales/{id}?v={version}}; null si no hay
     * club o no tiene escudo ({@code version} null significa "sin escudo").
     */
    public static String url(Long clubId, Long version) {

        if (clubId == null || version == null) {
            return null;
        }

        return RUTA + clubId + "?v=" + version;
    }

    /**
     * La URL del escudo de una fila de clasificación por el club que trae la consulta (por su código de club); null en
     * nuestra fila, en las de nuestro club y en las filas sin club con escudo.
     */
    public static String urlDeFila(com.mikedev.mutxamelcf.model.Clasificacion fila) {

        if (fila.isEsNuestro() || NombreClub.esPropio(fila.getNombreEquipo())) {
            return null;
        }

        return url(fila.getClubRivalId(), fila.getEscudoVersion());
    }

    /**
     * true si la fila de clasificación es de nuestro club: marcada como nuestra (ES_NUESTRO), con nuestro código de club
     * de la FFCV (ffcv.club.codigo: sus filiales) o con el nombre de nuestro club ({@link NombreClub#esPropio}).
     */
    public static boolean esPropioFila(com.mikedev.mutxamelcf.model.Clasificacion fila, int codigoClubPropio) {
        return fila.isEsNuestro()
                || (fila.getCodigoClub() != null && fila.getCodigoClub() == codigoClubPropio)
                || NombreClub.esPropio(fila.getNombreEquipo());
    }
}

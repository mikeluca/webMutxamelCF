package com.mikedev.mutxamelcf.dao.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.LimpiezaCuentaAppDao;

@Repository
public class LimpiezaCuentaAppDaoImpl implements LimpiezaCuentaAppDao {

    /** Actividad del club: descripción, tabla, columnas que apuntan a USUARIOS_APP y condición extra (puede ser null). */
    private record Actividad(String descripcion, String tabla, List<String> columnas, String extra) {
    }

    private static final List<Actividad> ACTIVIDAD = List.of(
            // USUARIO_APP_RESPUESTA_ID ya no existe: sql/2026-10-22 la quitó de CONVOCATORIA_JUGADOR (con su FK) y la
            // respuesta pasó a JUSTIFICACIONES_FALTA_PARTIDO.
            new Actividad("invitados a convocatorias", "CONVOCATORIA_JUGADOR", List.of("USUARIO_INVITA_ID"), null),
            new Actividad("coordinación de convocatorias", "CONVOCATORIA_JUGADOR",
                    List.of("USUARIO_COORDINADOR_ID"), null),
            new Actividad("justificaciones de falta a entrenamientos", "JUSTIFICACIONES_FALTA_ENTRENAMIENTO",
                    List.of("USUARIO_APP_ID"), null),
            new Actividad("justificaciones de falta a partidos", "JUSTIFICACIONES_FALTA_PARTIDO",
                    List.of("USUARIO_APP_ID"), null),
            new Actividad("eventos de partidos en directo", "PARTIDO_LIVE_EVENTOS", List.of("USUARIO_APP_ID"), null),
            new Actividad("reportes de comunicaciones", "REPORTES_COMUNICACION",
                    List.of("AUTOR_ID", "REPORTADOR_ID", "RESUELTO_POR_ID"), null),
            new Actividad("entrenamientos revisados o dirigidos", "ENTRENAMIENTOS",
                    List.of("USUARIO_REVISION_ID", "USUARIO_ENTRENADOR_ID"), null),
            new Actividad("horarios de entrenamiento", "HORARIOS_ENTRENAMIENTO", List.of("USUARIO_ACTUALIZO_ID"), null),
            new Actividad("partidos actualizados", "PARTIDOS", List.of("USUARIO_ACTUALIZO_ID"), null),
            new Actividad("continuidad de jugadores", "JUGADOR_TEMPORADA",
                    List.of("USUARIO_APP_CONTINUIDAD_ID"), null),
            new Actividad("comunicaciones publicadas", "COMUNICACIONES", List.of("USUARIO_AUTOR_ID"), null),
            // Las solicitudes de familia que revisó esta cuenta (las suyas propias se borran, no cuentan).
            new Actividad("solicitudes de familia revisadas", "SOLICITUDES_FAMILIA",
                    List.of("REVISADA_POR_USUARIO_APP_ID"), "USUARIO_APP_ID <> ?"));

    private final JdbcTemplate jdbcTemplate;

    public LimpiezaCuentaAppDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Integer> contarActividad(int usuarioAppId) {

        Map<String, Integer> resultado = new LinkedHashMap<>();

        for (Actividad a : ACTIVIDAD) {

            StringBuilder condicion = new StringBuilder();
            Object[] args = new Object[a.columnas().size() + (a.extra() != null ? 1 : 0)];
            int i = 0;

            for (String columna : a.columnas()) {
                condicion.append(i == 0 ? "" : " OR ").append(columna).append(" = ?");
                args[i++] = usuarioAppId;
            }

            String where = a.columnas().size() > 1 ? "(" + condicion + ")" : condicion.toString();

            if (a.extra() != null) {
                where += " AND " + a.extra();
                args[i] = usuarioAppId;
            }

            Integer filas = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + a.tabla() + " WHERE " + where, Integer.class, args);

            if (filas != null && filas > 0) {
                resultado.merge(a.descripcion(), filas, Integer::sum);
            }
        }

        return resultado;
    }

    @Override
    public Map<String, Integer> borrarDatosPropios(int usuarioAppId) {

        Map<String, Integer> borradas = new LinkedHashMap<>();

        // Los hijos primero: en Oracle los borraría el ON DELETE CASCADE, pero así se cuentan y no dependen de él.
        anotar(borradas, "SOLICITUDES_FAMILIA_JUGADOR", jdbcTemplate.update(
                "DELETE FROM SOLICITUDES_FAMILIA_JUGADOR WHERE SOLICITUD_ID IN "
                        + "(SELECT ID FROM SOLICITUDES_FAMILIA WHERE USUARIO_APP_ID = ?)", usuarioAppId));
        anotar(borradas, "SOLICITUDES_FAMILIA", jdbcTemplate.update(
                "DELETE FROM SOLICITUDES_FAMILIA WHERE USUARIO_APP_ID = ?", usuarioAppId));
        anotar(borradas, "NOTIFICACIONES_APP", jdbcTemplate.update(
                "DELETE FROM NOTIFICACIONES_APP WHERE USUARIO_APP_ID = ?", usuarioAppId));
        anotar(borradas, "PREFERENCIAS_NOTIFICACION", jdbcTemplate.update(
                "DELETE FROM PREFERENCIAS_NOTIFICACION WHERE USUARIO_APP_ID = ?", usuarioAppId));
        anotar(borradas, "USUARIOS_APP_DISPOSITIVOS", jdbcTemplate.update(
                "DELETE FROM USUARIOS_APP_DISPOSITIVOS WHERE USUARIO_APP_ID = ?", usuarioAppId));
        anotar(borradas, "COMUNICACION_USUARIO", jdbcTemplate.update(
                "DELETE FROM COMUNICACION_USUARIO WHERE USUARIO_APP_ID = ?", usuarioAppId));

        return borradas;
    }

    private static void anotar(Map<String, Integer> mapa, String tabla, int filas) {
        if (filas > 0) {
            mapa.put(tabla, filas);
        }
    }
}

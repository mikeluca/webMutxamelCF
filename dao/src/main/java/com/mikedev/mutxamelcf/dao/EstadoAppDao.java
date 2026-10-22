package com.mikedev.mutxamelcf.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Agregados para el panel "Estado de la app" (ADMIN_APP). Todo son consultas agregadas: nada
 * se carga cuenta a cuenta. "Activa" es una cuenta con ACTIVO = 1 que su titular no ha
 * eliminado.
 */
public interface EstadoAppDao {

    /** Cuentas por estado. */
    record Totales(int activas, int pendientes, int desactivadas) {
    }

    /** Dispositivos activos de cuentas activas con esa plataforma y versión (null = no consta). */
    record DispositivosPorVersion(String plataforma, String version, int dispositivos) {
    }

    /** Una cuenta de las listas de seguimiento. */
    record Usuario(long id, String email, LocalDateTime ultimoAcceso) {
    }

    Totales contarCuentas();

    int contarActivasSinDispositivo();

    /** Activas con la preferencia general de notificaciones desactivada. */
    int contarActivasSinNotificaciones();

    /** Activas cuyo último acceso (o, si nunca entraron, su activación) es anterior a [desde]. */
    int contarInactivasDesde(LocalDateTime desde);

    /** Dispositivos activos de cuentas activas, por plataforma y versión. */
    List<DispositivosPorVersion> contarDispositivosPorVersion();

    List<Usuario> listarActivasSinDispositivo(int maximo);

    List<Usuario> listarActivasSinNotificaciones(int maximo);

    List<Usuario> listarInactivasDesde(LocalDateTime desde, int maximo);

    /**
     * Activas con algún dispositivo activo de [plataforma] cuya versión sea una de [versiones]
     * (sin duplicados).
     */
    List<Usuario> listarActivasConVersiones(String plataforma, Collection<String> versiones, int maximo);

    /** Por cuenta, las versiones de sus dispositivos activos (sin repetir; null si no constaba). */
    Map<Long, List<String>> versionesDeDispositivosActivos(Collection<Long> usuarioIds);
}

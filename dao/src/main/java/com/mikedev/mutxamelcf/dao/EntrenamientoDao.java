package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Entrenamiento;

public interface EntrenamientoDao {

    Entrenamiento guardar(Entrenamiento entrenamiento);

    void actualizar(Entrenamiento entrenamiento);

    Entrenamiento obtenerPorId(Long id);

    List<Entrenamiento> obtenerPorEquipo(Long equipoId);

    /**
     * Igual que {@link #obtenerPorEquipo(Long)} pero limitado a las fechas
     * entre desde y hasta (ambas incluidas); un extremo null queda abierto.
     */
    List<Entrenamiento> obtenerPorEquipoEnRango(Long equipoId, java.time.LocalDate desde, java.time.LocalDate hasta);

    /**
     * Entrenamiento creado automáticamente para esa sesión del calendario
     * (SESIONES_ENTRENAMIENTO), o {@code null} si esa sesión todavía no
     * tiene ningún ENTRENAMIENTO vinculado. La UNIQUE(SESION_ENTRENAMIENTO_ID)
     * garantiza que como mucho hay uno.
     */
    Entrenamiento obtenerPorSesionEntrenamientoId(Long sesionEntrenamientoId);

    /**
     * Marca la lista como revisada (LISTA_REVISADA = 1) con quién y cuándo; se
     * llama cada vez que el staff guarda la asistencia, haya cambiado o no algún
     * estado.
     */
    void marcarListaRevisada(Long id, Long usuarioId, java.time.LocalDateTime fecha);

    /**
     * Entrenamientos con la lista sin revisar y sin aviso enviado, de fecha hasta
     * {@code hasta} (incluida), con la hora y el estado de su sesión del
     * calendario (null si no tienen sesión).
     */
    List<com.mikedev.mutxamelcf.model.EntrenamientoPendienteDeLista> obtenerPendientesDeAvisoLista(
            java.time.LocalDate hasta);

    /** Marca AVISO_LISTA_ENVIADO = 1: un solo aviso por sesión. */
    void marcarAvisoListaEnviado(Long id);

    boolean existe(Long id);

    void eliminar(Long id);
}
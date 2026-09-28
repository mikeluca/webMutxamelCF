package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Convocatoria;

public interface ConvocatoriaDao {

        Convocatoria guardar(
                        Convocatoria convocatoria);

        Convocatoria obtenerPorId(
                        Long id);

        List<Convocatoria> obtenerPorEquipo(
                        Long equipoId);

        void eliminar(
                        Long id);

        void actualizar(Convocatoria convocatoria);

        /**
         * Indica si ya existe una convocatoria asociada al partido dado.
         *
         * @param partidoId               partido a comprobar.
         * @param convocatoriaIdExcluir   id de convocatoria a excluir de la
         *                                comprobación (usado al actualizar,
         *                                para no comparar la convocatoria
         *                                consigo misma); puede ser
         *                                {@code null} si no se quiere
         *                                excluir ninguna (caso de alta).
         */
        boolean existePorPartido(
                        Long partidoId,
                        Long convocatoriaIdExcluir);
}

package com.mikedev.mutxamelcf.serviceimpl;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipacionDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Equipacion;
import com.mikedev.mutxamelcf.model.EquipacionResponse;
import com.mikedev.mutxamelcf.service.EquipacionService;

@Service
public class EquipacionServiceImpl implements EquipacionService {

    private static final int MAX_TEXTO = 50;
    private static final Pattern COLOR_HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final EquipacionDao equipacionDao;
    private final EquipoGestionDao equipoGestionDao;

    public EquipacionServiceImpl(EquipacionDao equipacionDao, EquipoGestionDao equipoGestionDao) {
        this.equipacionDao = equipacionDao;
        this.equipoGestionDao = equipoGestionDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipacionResponse> obtenerActivasParaApp(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }

        if (!equipoGestionDao.gestionaAlgunEquipo(usuarioAppId)) {
            throw new SecurityException("No tienes permiso para consultar los equipajes");
        }

        return equipacionDao.obtenerActivas().stream().map(EquipacionResponse::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Equipacion> obtenerTodas() {
        return equipacionDao.obtenerTodas();
    }

    @Override
    @Transactional
    public Equipacion guardar(Long id, String nombre, String camiseta, String pantalon, String medias,
            String colorHex, Integer orden) {

        Equipacion equipacion = new Equipacion();

        if (id != null) {
            Equipacion existente = equipacionDao.obtenerPorId(id);

            if (existente == null) {
                throw new IllegalArgumentException("El equipaje no existe");
            }

            // Activar o desactivar tiene su propia acción: editar los datos no cambia el estado.
            equipacion.setActiva(existente.isActiva());
        }

        equipacion.setId(id);
        equipacion.setNombre(texto(nombre, "El nombre", true));
        equipacion.setCamiseta(texto(camiseta, "La camiseta", false));
        equipacion.setPantalon(texto(pantalon, "El pantalón", false));
        equipacion.setMedias(texto(medias, "Las medias", false));
        equipacion.setColorHex(color(colorHex));
        equipacion.setOrden(orden == null ? 0 : orden);

        for (Equipacion otra : equipacionDao.obtenerTodas()) {
            if (otra.getNombre().equalsIgnoreCase(equipacion.getNombre()) && !otra.getId().equals(id)) {
                throw new IllegalArgumentException("Ya existe un equipaje llamado " + otra.getNombre());
            }
        }

        if (id == null) {
            return equipacionDao.crear(equipacion);
        }

        equipacionDao.actualizar(equipacion);
        return equipacion;
    }

    @Override
    @Transactional
    public void cambiarActiva(Long id, boolean activa) {

        Equipacion equipacion = equipacionDao.obtenerPorId(id);

        if (equipacion == null) {
            throw new IllegalArgumentException("El equipaje no existe");
        }

        equipacion.setActiva(activa);
        equipacionDao.actualizar(equipacion);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {

        if (equipacionDao.obtenerPorId(id) == null) {
            throw new IllegalArgumentException("El equipaje no existe");
        }

        if (equipacionDao.contarConvocatorias(id) > 0) {
            throw new IllegalArgumentException(
                    "El equipaje se ha usado en alguna convocatoria y no se puede borrar: desactívalo");
        }

        equipacionDao.eliminar(id);
    }

    private static String texto(String valor, String campo, boolean obligatorio) {

        String limpio = valor == null ? "" : valor.trim();

        if (limpio.isEmpty()) {
            if (obligatorio) {
                throw new IllegalArgumentException(campo + " es obligatorio");
            }
            return null;
        }

        if (limpio.length() > MAX_TEXTO) {
            throw new IllegalArgumentException(campo + " no puede tener más de " + MAX_TEXTO + " caracteres");
        }

        return limpio;
    }

    private static String color(String valor) {

        String limpio = valor == null ? "" : valor.trim();

        if (limpio.isEmpty()) {
            return null;
        }

        if (!COLOR_HEX.matcher(limpio).matches()) {
            throw new IllegalArgumentException("El color tiene que tener el formato #RRGGBB");
        }

        return limpio.toUpperCase();
    }
}

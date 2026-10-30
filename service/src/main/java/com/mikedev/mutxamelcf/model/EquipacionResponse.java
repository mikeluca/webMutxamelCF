package com.mikedev.mutxamelcf.model;

/** Un equipaje tal como lo ve la app: el color de cada prenda en texto y la muestra de color (#RRGGBB) opcional. */
public record EquipacionResponse(
        Long id,
        String nombre,
        String camiseta,
        String pantalon,
        String medias,
        String colorHex) {

    public static EquipacionResponse de(Equipacion equipacion) {

        return equipacion == null
                ? null
                : new EquipacionResponse(
                        equipacion.getId(),
                        equipacion.getNombre(),
                        equipacion.getCamiseta(),
                        equipacion.getPantalon(),
                        equipacion.getMedias(),
                        equipacion.getColorHex());
    }
}

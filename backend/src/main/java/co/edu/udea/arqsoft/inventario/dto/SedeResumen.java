package co.edu.udea.arqsoft.inventario.dto;

import co.edu.udea.arqsoft.inventario.domain.Almacen;

/** Identificación de la sede dentro de una representación de inventario. */
public record SedeResumen(Long id, String nombre, String ciudad) {

    public static SedeResumen de(Almacen almacen) {
        return new SedeResumen(almacen.getId(), almacen.getNombre(), almacen.getCiudad());
    }
}

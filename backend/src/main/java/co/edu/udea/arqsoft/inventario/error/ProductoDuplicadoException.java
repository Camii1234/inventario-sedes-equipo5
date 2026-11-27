package co.edu.udea.arqsoft.inventario.error;

import co.edu.udea.arqsoft.inventario.domain.Producto;

/** Se intenta registrar un SKU que ya existe en la misma sede. */
public class ProductoDuplicadoException extends RuntimeException {

    private final transient Producto existente;

    public ProductoDuplicadoException(Producto existente) {
        super("Ya existe el SKU " + existente.getSku() + " en la sede " + existente.getAlmacen().getId()
                + ". El stock registrado (" + existente.getStock() + ") no se modificó.");
        this.existente = existente;
    }

    public Producto getExistente() {
        return existente;
    }
}

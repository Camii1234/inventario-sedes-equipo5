package co.edu.udea.arqsoft.inventario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;

/** Inventario completo de una sede, ordenado por SKU (y por id como desempate). */
@JsonPropertyOrder({"sede", "totalProductos", "totalUnidades", "productos"})
@Schema(name = "Inventario")
public class InventarioModel extends RepresentationModel<InventarioModel> {

    private final SedeResumen sede;
    private final List<ProductoModel> productos;

    public InventarioModel(SedeResumen sede, List<ProductoModel> productos) {
        this.sede = sede;
        this.productos = List.copyOf(productos);
    }

    public SedeResumen getSede() { return sede; }

    public int getTotalProductos() { return productos.size(); }

    public long getTotalUnidades() { return productos.stream().mapToLong(ProductoModel::getStock).sum(); }

    public List<ProductoModel> getProductos() { return productos; }
}

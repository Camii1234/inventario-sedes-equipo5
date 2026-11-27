package co.edu.udea.arqsoft.inventario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/** Representación HAL de un producto (inventario de un SKU en una sede). */
@Relation(itemRelation = "producto", collectionRelation = "productos")
@JsonPropertyOrder({"id", "sku", "nombre", "stock", "sedeId", "sedeNombre", "creadoEn"})
@Schema(name = "Producto")
public class ProductoModel extends RepresentationModel<ProductoModel> {

    private final Long id;
    private final String sku;
    private final String nombre;
    private final Integer stock;
    private final Long sedeId;
    private final String sedeNombre;
    private final Instant creadoEn;

    public ProductoModel(Long id, String sku, String nombre, Integer stock, Long sedeId, String sedeNombre,
                         Instant creadoEn) {
        this.id = id;
        this.sku = sku;
        this.nombre = nombre;
        this.stock = stock;
        this.sedeId = sedeId;
        this.sedeNombre = sedeNombre;
        this.creadoEn = creadoEn;
    }

    public Long getId() { return id; }

    public String getSku() { return sku; }

    public String getNombre() { return nombre; }

    public Integer getStock() { return stock; }

    public Long getSedeId() { return sedeId; }

    public String getSedeNombre() { return sedeNombre; }

    public Instant getCreadoEn() { return creadoEn; }
}

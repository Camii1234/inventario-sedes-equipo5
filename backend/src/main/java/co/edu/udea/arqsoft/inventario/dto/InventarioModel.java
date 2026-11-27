package co.edu.udea.arqsoft.inventario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;

/**
 * Una página del inventario de una sede, ordenada por SKU (y por id como desempate).
 * totalProductos y totalUnidades se calculan sobre toda la sede, no solo sobre la página.
 */
@JsonPropertyOrder({"sede", "totalProductos", "totalUnidades", "pagina", "productos"})
@Schema(name = "Inventario")
public class InventarioModel extends RepresentationModel<InventarioModel> {

    private final SedeResumen sede;
    private final long totalProductos;
    private final long totalUnidades;
    private final Pagina pagina;
    private final List<ProductoModel> productos;

    public InventarioModel(SedeResumen sede, long totalProductos, long totalUnidades, Pagina pagina,
                           List<ProductoModel> productos) {
        this.sede = sede;
        this.totalProductos = totalProductos;
        this.totalUnidades = totalUnidades;
        this.pagina = pagina;
        this.productos = List.copyOf(productos);
    }

    public SedeResumen getSede() { return sede; }

    public long getTotalProductos() { return totalProductos; }

    public long getTotalUnidades() { return totalUnidades; }

    public Pagina getPagina() { return pagina; }

    public List<ProductoModel> getProductos() { return productos; }

    /** Metadatos de paginación: número de página (desde 0), tamaño, total de elementos y de páginas. */
    @Schema(name = "Pagina")
    public record Pagina(int numero, int tamano, long totalElementos, int totalPaginas) {
    }
}

package co.edu.udea.arqsoft.inventario.service;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Página del inventario de una sede tal como se guarda en Redis. Solo contiene datos simples
 * (no entidades JPA) para que se pueda serializar y reutilizar entre peticiones.
 */
public record InventarioPagina(
        Long sedeId, String sedeNombre, String sedeCiudad,
        List<Item> productos,
        long totalProductos, long totalUnidades,
        int pagina, int tamano, int totalPaginas) implements Serializable {

    public record Item(Long id, String sku, String nombre, Integer stock, Instant creadoEn) implements Serializable {
    }
}

package co.edu.udea.arqsoft.inventario.service;

import co.edu.udea.arqsoft.inventario.domain.Almacen;
import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.dto.ProductoCreateRequest;
import co.edu.udea.arqsoft.inventario.error.ProductoDuplicadoException;
import co.edu.udea.arqsoft.inventario.error.RecursoNoEncontradoException;
import co.edu.udea.arqsoft.inventario.repository.AlmacenRepository;
import co.edu.udea.arqsoft.inventario.repository.ProductoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    private final AlmacenRepository almacenes;
    private final ProductoRepository productos;

    public InventarioService(AlmacenRepository almacenes, ProductoRepository productos) {
        this.almacenes = almacenes;
        this.productos = productos;
    }

    /** Inventario de una sede existente; lanza 404 si la sede no existe. */
    @Transactional(readOnly = true)
    public InventarioSede consultarInventario(Long sedeId) {
        Almacen almacen = almacenes.findById(sedeId).orElseThrow(() -> RecursoNoEncontradoException.sede(sedeId));
        return new InventarioSede(almacen, productos.findByAlmacenIdOrderBySkuAscIdAsc(sedeId));
    }

    /**
     * Registra el inventario inicial de un SKU en una sede. Si el SKU ya existe en esa sede
     * no se modifica nada y se informa el conflicto; la restricción única de la base de datos
     * respalda la regla ante peticiones concurrentes.
     */
    @Transactional
    public Producto registrar(ProductoCreateRequest solicitud) {
        Almacen almacen = almacenes.findById(solicitud.sedeId())
                .orElseThrow(() -> RecursoNoEncontradoException.sede(solicitud.sedeId()));
        productos.findByAlmacenIdAndSku(almacen.getId(), solicitud.sku()).ifPresent(existente -> {
            throw new ProductoDuplicadoException(existente);
        });
        return productos.saveAndFlush(
                new Producto(almacen, solicitud.sku(), solicitud.nombre(), solicitud.stockInicial()));
    }

    @Transactional(readOnly = true)
    public Producto obtenerProducto(Long id) {
        return productos.findWithAlmacenById(id).orElseThrow(() -> RecursoNoEncontradoException.producto(id));
    }

    @Transactional(readOnly = true)
    public List<Almacen> listarAlmacenes() {
        return almacenes.findAllByOrderByIdAsc();
    }

    public record InventarioSede(Almacen almacen, List<Producto> productos) {
    }
}

package co.edu.udea.arqsoft.inventario.service;

import co.edu.udea.arqsoft.inventario.domain.Almacen;
import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.dto.ProductoCreateRequest;
import co.edu.udea.arqsoft.inventario.error.ProductoDuplicadoException;
import co.edu.udea.arqsoft.inventario.error.RecursoNoEncontradoException;
import co.edu.udea.arqsoft.inventario.repository.AlmacenRepository;
import co.edu.udea.arqsoft.inventario.repository.ProductoRepository;
import co.edu.udea.arqsoft.inventario.config.CacheConfig;
import co.edu.udea.arqsoft.inventario.web.CacheEstado;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /**
     * Página del inventario de una sede existente (404 si la sede no existe), ordenada por SKU e id.
     * El resultado se guarda en Redis con la clave sedeId:pagina:tamano; los errores no se cachean.
     */
    @Cacheable(cacheNames = CacheConfig.INVENTARIO, key = "#sedeId + ':' + #pagina + ':' + #tamano")
    @Transactional(readOnly = true)
    public InventarioPagina consultarInventario(Long sedeId, int pagina, int tamano) {
        CacheEstado.marcarMiss();
        Almacen almacen = almacenes.findById(sedeId).orElseThrow(() -> RecursoNoEncontradoException.sede(sedeId));
        Page<Producto> page = productos.findByAlmacenId(sedeId,
                PageRequest.of(pagina, tamano, Sort.by("sku").ascending().and(Sort.by("id").ascending())));
        List<InventarioPagina.Item> items = page.getContent().stream()
                .map(p -> new InventarioPagina.Item(p.getId(), p.getSku(), p.getNombre(), p.getStock(), p.getCreadoEn()))
                .toList();
        return new InventarioPagina(almacen.getId(), almacen.getNombre(), almacen.getCiudad(), items,
                page.getTotalElements(), productos.sumarStock(sedeId), pagina, tamano, page.getTotalPages());
    }

    /**
     * Registra el inventario inicial de un SKU en una sede. Si el SKU ya existe en esa sede
     * no se modifica nada y se informa el conflicto; la restricción única de la base de datos
     * respalda la regla ante peticiones concurrentes.
     */
    @CacheEvict(cacheNames = CacheConfig.INVENTARIO, allEntries = true)
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
}

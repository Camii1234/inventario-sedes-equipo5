package co.edu.udea.arqsoft.inventario.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import co.edu.udea.arqsoft.inventario.domain.Almacen;
import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.dto.AlmacenModel;
import co.edu.udea.arqsoft.inventario.dto.AlmacenesModel;
import co.edu.udea.arqsoft.inventario.dto.InventarioModel;
import co.edu.udea.arqsoft.inventario.dto.ProductoModel;
import co.edu.udea.arqsoft.inventario.dto.SedeResumen;
import co.edu.udea.arqsoft.inventario.service.InventarioPagina;
import java.util.List;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

/**
 * Construye las representaciones HAL. Los enlaces se generan a partir de los propios controladores,
 * por lo que siempre apuntan a recursos reales y respetan el host/puerto con que llegó la petición
 * (incluidos X-Forwarded-* cuando la petición pasa por el proxy del frontend).
 */
@Component
public class Representaciones {

    public static final String REL_INVENTARIO = "inventario";
    public static final String REL_ALMACENES = "almacenes";
    public static final String REL_REGISTRAR = "registrar-producto";

    public ProductoModel producto(Producto p) {
        Long sedeId = p.getAlmacen().getId();
        ProductoModel modelo = new ProductoModel(p.getId(), p.getSku(), p.getNombre(), p.getStock(), sedeId,
                p.getAlmacen().getNombre(), p.getCreadoEn());
        modelo.add(enlaceProducto(p.getId()).withSelfRel());
        modelo.add(enlaceInventario(sedeId).withRel(REL_INVENTARIO));
        return modelo;
    }

    /** Página de inventario con enlaces self, first, prev, next y last (HATEOAS de paginación). */
    public InventarioModel inventario(InventarioPagina inv) {
        Long sedeId = inv.sedeId();
        List<ProductoModel> productos = inv.productos().stream().map(p -> {
            ProductoModel m = new ProductoModel(p.id(), p.sku(), p.nombre(), p.stock(), sedeId, inv.sedeNombre(), p.creadoEn());
            m.add(enlaceProducto(p.id()).withSelfRel());
            return m;
        }).toList();
        InventarioModel modelo = new InventarioModel(new SedeResumen(sedeId, inv.sedeNombre(), inv.sedeCiudad()),
                inv.totalProductos(), inv.totalUnidades(),
                new InventarioModel.Pagina(inv.pagina(), inv.tamano(), inv.totalProductos(), inv.totalPaginas()), productos);
        int ultima = Math.max(inv.totalPaginas() - 1, 0);
        modelo.add(enlacePagina(sedeId, inv.pagina(), inv.tamano()).withSelfRel());
        modelo.add(enlacePagina(sedeId, 0, inv.tamano()).withRel(IanaLinkRelations.FIRST));
        if (inv.pagina() > 0) {
            modelo.add(enlacePagina(sedeId, Math.min(inv.pagina() - 1, ultima), inv.tamano()).withRel(IanaLinkRelations.PREV));
        }
        if (inv.pagina() < ultima) {
            modelo.add(enlacePagina(sedeId, inv.pagina() + 1, inv.tamano()).withRel(IanaLinkRelations.NEXT));
        }
        modelo.add(enlacePagina(sedeId, ultima, inv.tamano()).withRel(IanaLinkRelations.LAST));
        modelo.add(linkTo(methodOn(ProductoController.class).registrar(null)).withRel(REL_REGISTRAR));
        modelo.add(linkTo(methodOn(AlmacenController.class).listar()).withRel(REL_ALMACENES));
        return modelo;
    }

    public AlmacenesModel almacenes(List<Almacen> almacenes) {
        AlmacenesModel modelo = new AlmacenesModel(almacenes.stream().map(this::almacen).toList());
        modelo.add(linkTo(methodOn(AlmacenController.class).listar()).withSelfRel());
        return modelo;
    }

    private AlmacenModel almacen(Almacen a) {
        AlmacenModel modelo = new AlmacenModel(a.getId(), a.getNombre(), a.getCiudad(), a.getDireccion());
        modelo.add(enlaceInventario(a.getId()).withRel(REL_INVENTARIO));
        return modelo;
    }

    private static Link enlaceProducto(Long id) {
        return linkTo(methodOn(ProductoController.class).obtener(id)).withSelfRel();
    }

    private static Link enlaceInventario(Long sedeId) {
        return linkTo(methodOn(InventarioController.class).consultar(sedeId, null, null)).withSelfRel().expand();
    }

    private static Link enlacePagina(Long sedeId, int pagina, int tamano) {
        return linkTo(methodOn(InventarioController.class).consultar(sedeId, pagina, tamano)).withSelfRel();
    }
}

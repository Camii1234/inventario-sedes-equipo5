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
import co.edu.udea.arqsoft.inventario.service.InventarioService.InventarioSede;
import java.util.List;
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

    public InventarioModel inventario(InventarioSede inventario) {
        Long sedeId = inventario.almacen().getId();
        List<ProductoModel> productos = inventario.productos().stream().map(this::productoEnInventario).toList();
        InventarioModel modelo = new InventarioModel(SedeResumen.de(inventario.almacen()), productos);
        modelo.add(enlaceInventario(sedeId).withSelfRel());
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

    /** Dentro del inventario cada producto solo lleva su enlace de detalle (self). */
    private ProductoModel productoEnInventario(Producto p) {
        ProductoModel modelo = new ProductoModel(p.getId(), p.getSku(), p.getNombre(), p.getStock(),
                p.getAlmacen().getId(), p.getAlmacen().getNombre(), p.getCreadoEn());
        modelo.add(enlaceProducto(p.getId()).withSelfRel());
        return modelo;
    }

    private static Link enlaceProducto(Long id) {
        return linkTo(methodOn(ProductoController.class).obtener(id)).withSelfRel();
    }

    private static Link enlaceInventario(Long sedeId) {
        return linkTo(methodOn(InventarioController.class).consultar(sedeId)).withSelfRel();
    }
}

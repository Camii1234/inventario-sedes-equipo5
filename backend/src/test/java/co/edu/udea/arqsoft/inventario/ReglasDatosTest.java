package co.edu.udea.arqsoft.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.udea.arqsoft.inventario.domain.Almacen;
import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.dto.ProductoCreateRequest;
import co.edu.udea.arqsoft.inventario.repository.AlmacenRepository;
import co.edu.udea.arqsoft.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** Reglas que deben cumplirse también en la capa de datos. */
@DataJpaTest
@ActiveProfiles("test")
class ReglasDatosTest {

    @Autowired
    AlmacenRepository almacenes;

    @Autowired
    ProductoRepository productos;

    @Test
    @DisplayName("La restricción única (almacen_id, sku) rechaza el duplicado en la base de datos")
    void restriccionUnicaEnBaseDeDatos() {
        Almacen sede1 = almacenes.findById(1L).orElseThrow();
        assertThatThrownBy(() -> productos.saveAndFlush(new Producto(sede1, "ACE-001", "Aceite duplicado", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El mismo SKU puede existir en dos sedes")
    void mismoSkuEnDosSedes() {
        Almacen sede3 = almacenes.findById(3L).orElseThrow();
        Producto guardado = productos.saveAndFlush(new Producto(sede3, "ACE-001", "Aceite vegetal 1 L", 7));
        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("El inventario se lee con orden estable por SKU")
    void ordenEstable() {
        assertThat(productos.findByAlmacenIdOrderBySkuAscIdAsc(1L)).extracting(Producto::getSku)
                .containsExactly("ACE-001", "AZU-001", "LEC-001");
    }

    @Test
    @DisplayName("La solicitud normaliza SKU (recorta y pasa a mayúsculas) y nombre (espacios)")
    void normalizacion() {
        ProductoCreateRequest r = new ProductoCreateRequest(1L, "  arr-001 ", "  Arroz   blanco ", 0);
        assertThat(r.sku()).isEqualTo("ARR-001");
        assertThat(r.nombre()).isEqualTo("Arroz blanco");
        assertThat(new ProductoCreateRequest(1L, null, null, 0).sku()).isNull();
    }
}

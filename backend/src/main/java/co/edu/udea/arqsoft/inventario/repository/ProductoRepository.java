package co.edu.udea.arqsoft.inventario.repository;

import co.edu.udea.arqsoft.inventario.domain.Producto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Orden estable: SKU ascendente y, como desempate, id ascendente. */
    @EntityGraph(attributePaths = "almacen")
    List<Producto> findByAlmacenIdOrderBySkuAscIdAsc(Long almacenId);

    @EntityGraph(attributePaths = "almacen")
    Optional<Producto> findWithAlmacenById(Long id);

    Optional<Producto> findByAlmacenIdAndSku(Long almacenId, String sku);
}

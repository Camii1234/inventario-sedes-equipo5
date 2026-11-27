package co.edu.udea.arqsoft.inventario.repository;

import co.edu.udea.arqsoft.inventario.domain.Almacen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlmacenRepository extends JpaRepository<Almacen, Long> {

    List<Almacen> findAllByOrderByIdAsc();
}

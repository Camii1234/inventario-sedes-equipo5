package co.edu.udea.arqsoft.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;

/** Lista de sedes que alimenta el selector del frontend. */
@Schema(name = "Almacenes")
public class AlmacenesModel extends RepresentationModel<AlmacenesModel> {

    private final List<AlmacenModel> almacenes;

    public AlmacenesModel(List<AlmacenModel> almacenes) {
        this.almacenes = List.copyOf(almacenes);
    }

    public List<AlmacenModel> getAlmacenes() { return almacenes; }
}

package co.edu.udea.arqsoft.inventario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

@JsonPropertyOrder({"id", "nombre", "ciudad", "direccion"})
@Schema(name = "Almacen")
public class AlmacenModel extends RepresentationModel<AlmacenModel> {

    private final Long id;
    private final String nombre;
    private final String ciudad;
    private final String direccion;

    public AlmacenModel(Long id, String nombre, String ciudad, String direccion) {
        this.id = id;
        this.nombre = nombre;
        this.ciudad = ciudad;
        this.direccion = direccion;
    }

    public Long getId() { return id; }

    public String getNombre() { return nombre; }

    public String getCiudad() { return ciudad; }

    public String getDireccion() { return direccion; }
}

package co.edu.udea.arqsoft.inventario.web;

import co.edu.udea.arqsoft.inventario.config.ApiVersion;
import co.edu.udea.arqsoft.inventario.dto.AlmacenesModel;
import co.edu.udea.arqsoft.inventario.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint auxiliar que alimenta el selector de sedes del frontend. */
@RestController
@RequestMapping(path = "/api/almacenes", headers = ApiVersion.V1_HEADER_CONDITION,
        produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Almacenes", description = "Sedes disponibles (auxiliar para el frontend)")
public class AlmacenController {

    private final InventarioService servicio;
    private final Representaciones representaciones;

    public AlmacenController(InventarioService servicio, Representaciones representaciones) {
        this.servicio = servicio;
        this.representaciones = representaciones;
    }

    @GetMapping
    @Operation(summary = "Listar las sedes", description = "Cada sede incluye el enlace a su inventario.")
    @ApiResponse(responseCode = "200", description = "Sedes ordenadas por id",
            content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = AlmacenesModel.class),
                    examples = @ExampleObject(value = EjemplosApi.ALMACENES)))
    @ApiResponse(responseCode = "400", description = "Problema con X-API-Version",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = EjemplosApi.ERROR_VERSION_FALTANTE)))
    public ResponseEntity<AlmacenesModel> listar() {
        return ResponseEntity.ok(representaciones.almacenes(servicio.listarAlmacenes()));
    }
}

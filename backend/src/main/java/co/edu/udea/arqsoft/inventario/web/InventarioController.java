package co.edu.udea.arqsoft.inventario.web;

import co.edu.udea.arqsoft.inventario.config.ApiVersion;
import co.edu.udea.arqsoft.inventario.dto.InventarioModel;
import co.edu.udea.arqsoft.inventario.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/inventario", headers = ApiVersion.V1_HEADER_CONDITION,
        produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Inventario", description = "Consulta del inventario de un almacén por ID de sede")
public class InventarioController {

    public static final int TAMANO_POR_DEFECTO = 10;
    public static final int TAMANO_MAXIMO = 100;

    private final InventarioService servicio;
    private final Representaciones representaciones;

    public InventarioController(InventarioService servicio, Representaciones representaciones) {
        this.servicio = servicio;
        this.representaciones = representaciones;
    }

    @GetMapping
    @Operation(summary = "Consultar el inventario de una sede",
            description = "Devuelve la sede y una página de sus productos ordenados por SKU (y por id como desempate). "
                    + "Paginación con page (desde 0) y size (por defecto 10, máximo 100); enlaces first/prev/next/last. "
                    + "La respuesta se guarda en Redis; la cabecera X-Cache indica HIT o MISS. "
                    + "Una sede existente sin productos responde 200 con la colección vacía; una sede inexistente responde 404.")
    @ApiResponse(responseCode = "200", description = "Inventario de la sede",
            content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = InventarioModel.class),
                    examples = {
                            @ExampleObject(name = "Sede con productos", value = EjemplosApi.INVENTARIO),
                            @ExampleObject(name = "Sede sin productos", value = EjemplosApi.INVENTARIO_VACIO)
                    }))
    @ApiResponse(responseCode = "400", description = "Falta sedeId, sedeId inválido o problema con X-API-Version",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Falta sedeId", value = EjemplosApi.ERROR_SEDE_FALTANTE),
                            @ExampleObject(name = "Falta X-API-Version", value = EjemplosApi.ERROR_VERSION_FALTANTE),
                            @ExampleObject(name = "Versión no soportada", value = EjemplosApi.ERROR_VERSION_NO_SOPORTADA)
                    }))
    @ApiResponse(responseCode = "404", description = "La sede no existe",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = EjemplosApi.ERROR_SEDE_NO_ENCONTRADA)))
    public ResponseEntity<InventarioModel> consultar(
            @Parameter(description = "ID de la sede del almacén", required = true, example = "1")
            @RequestParam @Positive(message = "El parámetro sedeId debe ser un entero mayor que cero") Long sedeId,
            @Parameter(description = "Número de página, empieza en 0", example = "0")
            @RequestParam(required = false) @Min(value = 0, message = "El parámetro page debe ser mayor o igual a 0") Integer page,
            @Parameter(description = "Productos por página (1 a " + TAMANO_MAXIMO + ")", example = "10")
            @RequestParam(required = false) @Min(value = 1, message = "El parámetro size debe ser mayor o igual a 1")
            @Max(value = TAMANO_MAXIMO, message = "El parámetro size no puede superar " + TAMANO_MAXIMO) Integer size) {
        int pagina = page == null ? 0 : page;
        int tamano = size == null ? TAMANO_POR_DEFECTO : size;
        InventarioModel modelo = representaciones.inventario(servicio.consultarInventario(sedeId, pagina, tamano));
        return ResponseEntity.ok().header(CacheEstado.HEADER, CacheEstado.valor()).body(modelo);
    }
}

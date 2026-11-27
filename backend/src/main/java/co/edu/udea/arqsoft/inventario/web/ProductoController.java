package co.edu.udea.arqsoft.inventario.web;

import co.edu.udea.arqsoft.inventario.config.ApiVersion;
import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.dto.ProductoCreateRequest;
import co.edu.udea.arqsoft.inventario.dto.ProductoModel;
import co.edu.udea.arqsoft.inventario.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/productos", headers = ApiVersion.V1_HEADER_CONDITION,
        produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Tag(name = "Productos", description = "Registro de productos con su stock inicial y consulta de detalle")
public class ProductoController {

    private final InventarioService servicio;
    private final Representaciones representaciones;

    public ProductoController(InventarioService servicio, Representaciones representaciones) {
        this.servicio = servicio;
        this.representaciones = representaciones;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar un producto con su stock inicial en una sede",
            description = "El SKU se normaliza (sin espacios externos y en mayúsculas). Un mismo SKU puede existir en "
                    + "varias sedes, pero solo una vez por sede. stockInicial acepta enteros entre 0 y 1000000.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductoCreateRequest.class),
                            examples = {
                                    @ExampleObject(name = "Producto nuevo", value = EjemplosApi.POST_CUERPO),
                                    @ExampleObject(name = "Stock cero (válido)",
                                            value = "{\"sedeId\":3,\"sku\":\"sal-001\",\"nombre\":\"Sal refinada 500 g\",\"stockInicial\":0}"),
                                    @ExampleObject(name = "Stock negativo (400)",
                                            value = "{\"sedeId\":1,\"sku\":\"NEG-001\",\"nombre\":\"Producto inválido\",\"stockInicial\":-5}")
                            })))
    @ApiResponse(responseCode = "201", description = "Producto creado. Location apunta a GET /api/productos/{id}",
            headers = @Header(name = "Location", description = "URL del producto creado",
                    schema = @Schema(type = "string", example = "http://localhost:8080/api/productos/8")),
            content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = ProductoModel.class),
                    examples = @ExampleObject(value = EjemplosApi.PRODUCTO)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos, JSON mal formado o problema con X-API-Version",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Stock negativo", value = EjemplosApi.ERROR_VALIDACION),
                            @ExampleObject(name = "Falta X-API-Version", value = EjemplosApi.ERROR_VERSION_FALTANTE),
                            @ExampleObject(name = "Versión no soportada", value = EjemplosApi.ERROR_VERSION_NO_SOPORTADA)
                    }))
    @ApiResponse(responseCode = "404", description = "La sede indicada no existe",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = EjemplosApi.ERROR_SEDE_NO_ENCONTRADA)))
    @ApiResponse(responseCode = "409", description = "El SKU ya existe en esa sede; el stock anterior se conserva",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = EjemplosApi.ERROR_DUPLICADO)))
    public ResponseEntity<ProductoModel> registrar(@Valid @RequestBody ProductoCreateRequest solicitud) {
        Producto creado = servicio.registrar(solicitud);
        ProductoModel modelo = representaciones.producto(creado);
        return ResponseEntity.created(modelo.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(modelo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle de un producto",
            description = "Recurso al que apuntan la cabecera Location y el enlace self del producto.")
    @ApiResponse(responseCode = "200", description = "Producto encontrado",
            content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = ProductoModel.class),
                    examples = @ExampleObject(value = EjemplosApi.PRODUCTO)))
    @ApiResponse(responseCode = "400", description = "Identificador inválido o problema con X-API-Version",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Versión no soportada", value = EjemplosApi.ERROR_VERSION_NO_SOPORTADA)))
    @ApiResponse(responseCode = "404", description = "El producto no existe",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = EjemplosApi.ERROR_PRODUCTO_NO_ENCONTRADO)))
    public ResponseEntity<ProductoModel> obtener(
            @Parameter(description = "ID del producto", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(representaciones.producto(servicio.obtenerProducto(id)));
    }
}

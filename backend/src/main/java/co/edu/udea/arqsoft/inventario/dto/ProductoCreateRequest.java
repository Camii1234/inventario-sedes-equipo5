package co.edu.udea.arqsoft.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Cuerpo del POST /api/productos. Antes de validar se normaliza:
 * el SKU se recorta y pasa a mayúsculas; el nombre se recorta y se colapsan los espacios repetidos.
 */
@Schema(name = "ProductoCreateRequest", description = "Producto a registrar con su stock inicial en una sede")
public record ProductoCreateRequest(

        @Schema(description = "ID de la sede (almacén) donde se registra el inventario", example = "1")
        @NotNull(message = "El campo sedeId es obligatorio")
        @Positive(message = "El campo sedeId debe ser un entero mayor que cero")
        Long sedeId,

        @Schema(description = "Código del producto. Se guarda sin espacios externos y en mayúsculas", example = "ARR-001",
                minLength = 3, maxLength = 40)
        @NotBlank(message = "El campo sku es obligatorio")
        @Size(min = 3, max = 40, message = "El campo sku debe tener entre 3 y 40 caracteres")
        @Pattern(regexp = "^[A-Z0-9][A-Z0-9._-]*$",
                message = "El campo sku solo admite letras, números, punto, guion y guion bajo, sin espacios internos")
        String sku,

        @Schema(description = "Nombre descriptivo del producto", example = "Arroz blanco de 1 kg", minLength = 2, maxLength = 120)
        @NotBlank(message = "El campo nombre es obligatorio")
        @Size(min = 2, max = 120, message = "El campo nombre debe tener entre 2 y 120 caracteres")
        String nombre,

        @Schema(description = "Cantidad inicial en stock. Entero mayor o igual a cero", example = "20",
                minimum = "0", maximum = "1000000")
        @NotNull(message = "El campo stockInicial es obligatorio")
        @PositiveOrZero(message = "El campo stockInicial debe ser un entero mayor o igual a cero")
        @Max(value = 1_000_000, message = "El campo stockInicial no puede superar 1000000")
        Integer stockInicial) {

    public ProductoCreateRequest {
        sku = sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
        nombre = nombre == null ? null : nombre.trim().replaceAll("\\s+", " ");
    }
}

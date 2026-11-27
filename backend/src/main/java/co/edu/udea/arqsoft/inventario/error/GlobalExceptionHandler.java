package co.edu.udea.arqsoft.inventario.error;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import co.edu.udea.arqsoft.inventario.domain.Producto;
import co.edu.udea.arqsoft.inventario.web.ProductoController;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduce las excepciones a application/problem+json con mensajes que permiten corregir la petición. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.NOT_FOUND, ex.getCodigo(), "Recurso no encontrado", ex.getMessage(), ruta(req));
    }

    @ExceptionHandler(ProductoDuplicadoException.class)
    ProblemDetail duplicado(ProductoDuplicadoException ex, HttpServletRequest req) {
        ProblemDetail p = Problemas.crear(HttpStatus.CONFLICT, "SKU_DUPLICADO_EN_SEDE",
                "El SKU ya existe en la sede", ex.getMessage(), ruta(req));
        Producto existente = ex.getExistente();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", existente.getId());
        datos.put("sku", existente.getSku());
        datos.put("sedeId", existente.getAlmacen().getId());
        datos.put("stock", existente.getStock());
        datos.put("href", linkTo(methodOn(ProductoController.class).obtener(existente.getId())).toUri().toString());
        p.setProperty("productoExistente", datos);
        return p;
    }

    /** Respaldo ante carreras: la restricción única uk_producto_almacen_sku de MySQL rechaza el duplicado. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return Problemas.crear(HttpStatus.CONFLICT, "SKU_DUPLICADO_EN_SEDE", "El SKU ya existe en la sede",
                "La base de datos rechazó el registro porque ese SKU ya existe en la sede indicada.", ruta(req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail cuerpoInvalido(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, Object>> errores = ex.getBindingResult().getFieldErrors().stream()
                .sorted((a, b) -> a.getField().compareTo(b.getField()))
                .map(GlobalExceptionHandler::errorCampo)
                .toList();
        String resumen = errores.stream().map(e -> String.valueOf(e.get("mensaje"))).collect(Collectors.joining("; "));
        ProblemDetail p = Problemas.crear(HttpStatus.BAD_REQUEST, "VALIDACION_FALLIDA",
                "Datos del producto inválidos", resumen, ruta(req));
        p.setProperty("errores", errores);
        return p;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail parametroInvalido(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<Map<String, Object>> errores = new ArrayList<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e -> {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("campo", r.getMethodParameter().getParameterName());
            error.put("mensaje", e.getDefaultMessage());
            error.put("valorRechazado", r.getArgument());
            errores.add(error);
        }));
        String resumen = errores.stream().map(e -> String.valueOf(e.get("mensaje"))).collect(Collectors.joining("; "));
        ProblemDetail p = Problemas.crear(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parámetro inválido",
                resumen, ruta(req));
        p.setProperty("errores", errores);
        return p;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, "PARAMETRO_REQUERIDO", "Falta un parámetro de consulta",
                "El parámetro de consulta '" + ex.getParameterName() + "' es obligatorio. Ejemplo: /api/inventario?sedeId=1",
                ruta(req));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parámetro inválido",
                "El valor '" + ex.getValue() + "' no es válido para '" + ex.getName()
                        + "'. Debe ser un número entero mayor que cero.", ruta(req));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String detalle = "El cuerpo de la petición no es un JSON válido o falta.";
        if (ex.getCause() instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            detalle = "El campo '" + campo(ife) + "' tiene un valor con formato inválido (" + ife.getValue()
                    + "). Use un número entero.";
        } else if (ex.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            detalle = "El campo '" + campo(jme) + "' tiene un tipo de dato inválido.";
        }
        return Problemas.crear(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "Cuerpo JSON inválido", detalle, ruta(req));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaNoSoportada(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_CONTENIDO_NO_SOPORTADO",
                "Tipo de contenido no soportado", "Envíe el cuerpo con Content-Type: application/json.", ruta(req));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail noAceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.NOT_ACCEPTABLE, "FORMATO_NO_ACEPTABLE", "Formato de respuesta no disponible",
                "La API responde application/hal+json o application/json.", ruta(req));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO", "Método no permitido",
                "El método " + ex.getMethod() + " no está permitido en esta ruta.", ruta(req));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return Problemas.crear(HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA", "Ruta no encontrada",
                "No existe la ruta " + ruta(req) + ".", ruta(req));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", ruta(req), ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Error interno",
                "Ocurrió un error inesperado. Intente de nuevo o revise los registros del servidor.", ruta(req));
    }

    private static Map<String, Object> errorCampo(FieldError fe) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("campo", fe.getField());
        error.put("mensaje", fe.getDefaultMessage());
        error.put("valorRechazado", fe.getRejectedValue());
        return error;
    }

    private static String campo(JsonMappingException ex) {
        return ex.getPath().stream().map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String ruta(HttpServletRequest req) {
        String query = req.getQueryString();
        return req.getRequestURI() + (query == null ? "" : "?" + query);
    }
}

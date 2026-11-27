package co.edu.udea.arqsoft.inventario.config;

import co.edu.udea.arqsoft.inventario.error.Problemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida X-API-Version en todas las rutas /api/**. Swagger UI (/swagger-ui/**), el contrato
 * OpenAPI (/v3/api-docs) y los archivos estáticos del frontend no pasan por este filtro.
 */
@Component
public class ApiVersionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public ApiVersionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean esApi = ruta.equals("/api") || ruta.startsWith("/api/");
        return !esApi || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String version = request.getHeader(ApiVersion.HEADER);
        String soportadas = String.join(", ", ApiVersion.SOPORTADAS);

        if (version == null || version.isBlank()) {
            escribir(response, Problemas.crear(HttpStatus.BAD_REQUEST, "VERSION_REQUERIDA",
                    "Falta el encabezado de versión",
                    "La petición debe incluir el encabezado " + ApiVersion.HEADER + ". Ejemplo: "
                            + ApiVersion.HEADER + ": " + ApiVersion.V1 + ".",
                    request.getRequestURI()), soportadas);
            return;
        }
        if (!ApiVersion.SOPORTADAS.contains(version.trim())) {
            escribir(response, Problemas.crear(HttpStatus.BAD_REQUEST, "VERSION_NO_SOPORTADA",
                    "Versión de API no soportada",
                    "La versión '" + version.trim() + "' no está soportada. Versiones admitidas en "
                            + ApiVersion.HEADER + ": " + soportadas + ".",
                    request.getRequestURI()), soportadas);
            return;
        }
        chain.doFilter(request, response);
    }

    private void escribir(HttpServletResponse response, ProblemDetail problema, String soportadas) throws IOException {
        problema.setProperty("versionesSoportadas", ApiVersion.SOPORTADAS);
        response.setStatus(problema.getStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-API-Supported-Versions", soportadas);
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}

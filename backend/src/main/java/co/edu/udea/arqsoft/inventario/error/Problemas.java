package co.edu.udea.arqsoft.inventario.error;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Fábrica de respuestas application/problem+json (RFC 9457) con un código de error estable. */
public final class Problemas {

    private Problemas() {
    }

    public static ProblemDetail crear(HttpStatus status, String codigo, String titulo, String detalle, String ruta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setType(URI.create("urn:inventario:error:" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(titulo);
        if (ruta != null) {
            try {
                problema.setInstance(URI.create(ruta));
            } catch (IllegalArgumentException ignorada) {
                // Ruta con caracteres no válidos en una URI: se omite "instance".
            }
        }
        problema.setProperty("codigo", codigo);
        problema.setProperty("timestamp", Instant.now().toString());
        return problema;
    }
}

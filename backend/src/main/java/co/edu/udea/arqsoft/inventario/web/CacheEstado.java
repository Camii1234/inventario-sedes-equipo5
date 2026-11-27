package co.edu.udea.arqsoft.inventario.web;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Marca si la petición actual tuvo que consultar MySQL. El método cacheado solo se ejecuta cuando
 * la página no está en Redis, por lo que al ejecutarse marca MISS; si no se ejecuta, la respuesta es HIT.
 */
public final class CacheEstado {

    public static final String HEADER = "X-Cache";
    private static final String ATRIBUTO = CacheEstado.class.getName() + ".miss";

    private CacheEstado() {
    }

    public static void marcarMiss() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.setAttribute(ATRIBUTO, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static String valor() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs != null && attrs.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST) != null ? "MISS" : "HIT";
    }
}

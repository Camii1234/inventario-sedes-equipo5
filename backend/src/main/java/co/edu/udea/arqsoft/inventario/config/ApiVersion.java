package co.edu.udea.arqsoft.inventario.config;

import java.util.List;

/**
 * Política de versionamiento por custom request header.
 * <ul>
 *   <li>Todas las rutas de negocio bajo /api exigen el encabezado {@value #HEADER}.</li>
 *   <li>Encabezado ausente o vacío: 400 con código VERSION_REQUERIDA.</li>
 *   <li>Versión distinta de las soportadas: 400 con código VERSION_NO_SOPORTADA y la lista de versiones admitidas.</li>
 *   <li>Los controladores declaran {@code headers = "X-API-Version=1"}, de modo que la versión también
 *       interviene en la selección del handler de Spring MVC.</li>
 * </ul>
 */
public final class ApiVersion {

    public static final String HEADER = "X-API-Version";
    public static final String V1 = "1";
    /** Condición de mapeo usada por los controladores de la versión 1. */
    public static final String V1_HEADER_CONDITION = HEADER + "=" + V1;
    public static final List<String> SOPORTADAS = List.of(V1);

    private ApiVersion() {
    }
}

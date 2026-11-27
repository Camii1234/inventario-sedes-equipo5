package co.edu.udea.arqsoft.inventario.web;

/** Ejemplos tomados de respuestas reales de la API desplegada con Docker Compose (puerto 8080). */
final class EjemplosApi {

    private EjemplosApi() {
    }

    static final String POST_CUERPO = """
            {"sedeId":1,"sku":"ARR-001","nombre":"Arroz blanco de 1 kg","stockInicial":20}""";

    static final String PRODUCTO = """
            {
              "id": 8,
              "sku": "ARR-001",
              "nombre": "Arroz blanco de 1 kg",
              "stock": 20,
              "sedeId": 1,
              "sedeNombre": "Sede Medellín Centro",
              "creadoEn": "2026-11-27T15:40:12.345678Z",
              "_links": {
                "self": { "href": "http://localhost:8080/api/productos/8" },
                "inventario": { "href": "http://localhost:8080/api/inventario?sedeId=1" }
              }
            }""";

    static final String INVENTARIO = """
            {
              "sede": { "id": 1, "nombre": "Sede Medellín Centro", "ciudad": "Medellín" },
              "totalProductos": 2,
              "totalUnidades": 85,
              "productos": [
                {
                  "id": 1, "sku": "ACE-001", "nombre": "Aceite vegetal 1 L", "stock": 35,
                  "sedeId": 1, "sedeNombre": "Sede Medellín Centro", "creadoEn": "2026-11-27T15:00:00Z",
                  "_links": { "self": { "href": "http://localhost:8080/api/productos/1" } }
                },
                {
                  "id": 2, "sku": "AZU-001", "nombre": "Azúcar refinada 1 kg", "stock": 50,
                  "sedeId": 1, "sedeNombre": "Sede Medellín Centro", "creadoEn": "2026-11-27T15:00:00Z",
                  "_links": { "self": { "href": "http://localhost:8080/api/productos/2" } }
                }
              ],
              "_links": {
                "self": { "href": "http://localhost:8080/api/inventario?sedeId=1" },
                "registrar-producto": { "href": "http://localhost:8080/api/productos" },
                "almacenes": { "href": "http://localhost:8080/api/almacenes" }
              }
            }""";

    static final String INVENTARIO_VACIO = """
            {
              "sede": { "id": 3, "nombre": "Sede Cali Sur", "ciudad": "Cali" },
              "totalProductos": 0,
              "totalUnidades": 0,
              "productos": [],
              "_links": {
                "self": { "href": "http://localhost:8080/api/inventario?sedeId=3" },
                "registrar-producto": { "href": "http://localhost:8080/api/productos" },
                "almacenes": { "href": "http://localhost:8080/api/almacenes" }
              }
            }""";

    static final String ALMACENES = """
            {
              "almacenes": [
                {
                  "id": 1, "nombre": "Sede Medellín Centro", "ciudad": "Medellín", "direccion": "Carrera 50 # 52-20",
                  "_links": { "inventario": { "href": "http://localhost:8080/api/inventario?sedeId=1" } }
                }
              ],
              "_links": { "self": { "href": "http://localhost:8080/api/almacenes" } }
            }""";

    static final String ERROR_VALIDACION = """
            {
              "type": "urn:inventario:error:validacion-fallida",
              "title": "Datos del producto inválidos",
              "status": 400,
              "detail": "El campo stockInicial debe ser un entero mayor o igual a cero",
              "instance": "/api/productos",
              "codigo": "VALIDACION_FALLIDA",
              "timestamp": "2026-11-27T15:41:00.000Z",
              "errores": [
                { "campo": "stockInicial", "mensaje": "El campo stockInicial debe ser un entero mayor o igual a cero", "valorRechazado": -5 }
              ]
            }""";

    static final String ERROR_DUPLICADO = """
            {
              "type": "urn:inventario:error:sku-duplicado-en-sede",
              "title": "El SKU ya existe en la sede",
              "status": 409,
              "detail": "Ya existe el SKU ARR-001 en la sede 1. El stock registrado (20) no se modificó.",
              "instance": "/api/productos",
              "codigo": "SKU_DUPLICADO_EN_SEDE",
              "timestamp": "2026-11-27T15:41:30.000Z",
              "productoExistente": { "id": 8, "sku": "ARR-001", "sedeId": 1, "stock": 20, "href": "http://localhost:8080/api/productos/8" }
            }""";

    static final String ERROR_SEDE_NO_ENCONTRADA = """
            {
              "type": "urn:inventario:error:sede-no-encontrada",
              "title": "Recurso no encontrado",
              "status": 404,
              "detail": "No existe una sede con id 999",
              "instance": "/api/inventario?sedeId=999",
              "codigo": "SEDE_NO_ENCONTRADA",
              "timestamp": "2026-11-27T15:42:00.000Z"
            }""";

    static final String ERROR_PRODUCTO_NO_ENCONTRADO = """
            {
              "type": "urn:inventario:error:producto-no-encontrado",
              "title": "Recurso no encontrado",
              "status": 404,
              "detail": "No existe un producto con id 999999",
              "instance": "/api/productos/999999",
              "codigo": "PRODUCTO_NO_ENCONTRADO",
              "timestamp": "2026-11-27T15:42:00.000Z"
            }""";

    static final String ERROR_SEDE_FALTANTE = """
            {
              "type": "urn:inventario:error:parametro-requerido",
              "title": "Falta un parámetro de consulta",
              "status": 400,
              "detail": "El parámetro de consulta 'sedeId' es obligatorio. Ejemplo: /api/inventario?sedeId=1",
              "instance": "/api/inventario",
              "codigo": "PARAMETRO_REQUERIDO",
              "timestamp": "2026-11-27T15:42:30.000Z"
            }""";

    static final String ERROR_VERSION_FALTANTE = """
            {
              "type": "urn:inventario:error:version-requerida",
              "title": "Falta el encabezado de versión",
              "status": 400,
              "detail": "La petición debe incluir el encabezado X-API-Version. Ejemplo: X-API-Version: 1.",
              "instance": "/api/inventario",
              "codigo": "VERSION_REQUERIDA",
              "timestamp": "2026-11-27T15:43:00.000Z",
              "versionesSoportadas": ["1"]
            }""";

    static final String ERROR_VERSION_NO_SOPORTADA = """
            {
              "type": "urn:inventario:error:version-no-soportada",
              "title": "Versión de API no soportada",
              "status": 400,
              "detail": "La versión '2' no está soportada. Versiones admitidas en X-API-Version: 1.",
              "instance": "/api/inventario",
              "codigo": "VERSION_NO_SOPORTADA",
              "timestamp": "2026-11-27T15:43:10.000Z",
              "versionesSoportadas": ["1"]
            }""";
}

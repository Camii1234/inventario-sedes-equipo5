# Evidencia E2E - 20261127-104801

- API: http://localhost:8080  |  Frontend: http://localhost:3000  |  SKU de la corrida: `DEMO-20261127104801`  |  Reinicio probado: True
- Resultado: 33 de 33 comprobaciones OK

```text
NAME                  SERVICE    STATUS                        PORTS
inventario-backend    backend    Up About a minute             0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
inventario-frontend   frontend   Up About a minute             0.0.0.0:3000->80/tcp, [::]:3000->80/tcp
inventario-mysql      mysql      Up About a minute (healthy)   0.0.0.0:3308->3306/tcp, [::]:3308->3306/tcp
```

| Id | Operación | Esperado | Observado | Resultado |
|---|---|---|---|---|
| 1 | docker compose ps | 3 servicios en ejecución | mysql, backend y frontend arriba | OK |
| 2.0 | GET /api/almacenes | 200 con 3 sedes | 200 con 3 sedes | OK |
| 2.1 | GET /api/inventario?sedeId=1 | 200, sede 1, application/hal+json | 200, sede 1 Sede Medellín Centro, 4 productos | OK |
| 2.2 | GET /api/inventario?sedeId=2 | 200, sede 2 | 200, sede 2 Sede Bogotá Norte, 5 productos | OK |
| 2.3 | SKU compartido ACE-001 | stock distinto en sede 1 y 2 | sede 1 = 35, sede 2 = 12 | OK |
| 2.4 | Aislamiento entre sedes | ningún registro de otra sede; CAF-001 solo en sede 2 | ajenos en sede1=0, sede2=0; CAF-001 en sede1=0 | OK |
| 2.5 | Orden estable del inventario | productos ordenados por SKU | ACE-001, AZU-001, DEMO-20261127104641, LEC-001 | OK |
| 3.1 | POST /api/productos (DEMO-20261127104801, stock 20, sede 1) | 201 Created | 201 application/hal+json, id 10, stock 20 | OK |
| 3.2 | Cabecera Location | igual a _links.self.href | Location=http://localhost:8080/api/productos/10 | OK |
| 3.3 | Enlaces del POST | self e inventario?sedeId=1 | self=http://localhost:8080/api/productos/10 ; inventario=http://localhost:8080/api/inventario?sedeId=1 | OK |
| 4.1 | GET enlace self | 200, DEMO-20261127104801, sede 1, stock 20 | 200, DEMO-20261127104801, sede 1, stock 20 | OK |
| 4.2 | GET enlace inventario | 200, sede 1 incluye DEMO-20261127104801 con 20 | 200, sede 1, stock DEMO-20261127104801 = 20 | OK |
| 4.3 | GET enlace self de un producto del inventario | 200 con ese producto | 200, ACE-001 | OK |
| 4.4 | Seguir self sin X-API-Version | 400 VERSION_REQUERIDA | 400 VERSION_REQUERIDA | OK |
| 4.5 | HATEOAS vía proxy del frontend | self con origen http://localhost:3000 y navegable | 200; self=http://localhost:3000/api/inventario?sedeId=1 -> 200 | OK |
| 5.1 | POST mismo SKU en sede 1 (minúsculas y espacios, stock 99) | 409 SKU_DUPLICADO_EN_SEDE | 409 application/problem+json SKU_DUPLICADO_EN_SEDE: Ya existe el SKU DEMO-20261127104801 en la sede 1. El stock registrado (20) no se modificó. | OK |
| 5.2 | Stock tras el 409 | sigue en 20 | stock = 20 | OK |
| 5.3 | POST mismo SKU en sede 2 con stock 8 | 201 Created | 201, sede 2, stock 8 | OK |
| 5.4 | Ambos valores del SKU | sede 1 = 20, sede 2 = 8 | sede 1 = 20, sede 2 = 8 | OK |
| 6.1 | POST stock 0 | 201 (cero es válido) | 201, stock 0 | OK |
| 6.2 | POST stock -5 | 400 VALIDACION_FALLIDA en stockInicial | 400 VALIDACION_FALLIDA: El campo stockInicial debe ser un entero mayor o igual a cero | OK |
| 6.3 | POST sede inexistente (999) | 404 SEDE_NO_ENCONTRADA | 404 SEDE_NO_ENCONTRADA | OK |
| 6.4 | GET inventario sede inexistente (999) | 404 SEDE_NO_ENCONTRADA | 404 application/problem+json SEDE_NO_ENCONTRADA: No existe una sede con id 999 | OK |
| 6.5 | GET inventario sede vacía (3) | 200 con productos = [] | 200, Sede Cali Sur, totalProductos 0 | OK |
| 6.6 | GET inventario sin sedeId | 400 PARAMETRO_REQUERIDO | 400 PARAMETRO_REQUERIDO: El parámetro de consulta 'sedeId' es obligatorio. Ejemplo: /api/inventario?sedeId=1 | OK |
| 6.7 | GET inventario sedeId=abc | 400 PARAMETRO_INVALIDO | 400 PARAMETRO_INVALIDO | OK |
| 6.8 | GET sin X-API-Version | 400 VERSION_REQUERIDA | 400 VERSION_REQUERIDA: La petición debe incluir el encabezado X-API-Version. Ejemplo: X-API-Version: 1. | OK |
| 6.9 | GET con X-API-Version: 2 | 400 VERSION_NO_SOPORTADA (versiones admitidas: 1) | 400 VERSION_NO_SOPORTADA: La versión '2' no está soportada. Versiones admitidas en X-API-Version: 1. | OK |
| 6.10 | POST sin X-API-Version | 400 VERSION_REQUERIDA | 400 VERSION_REQUERIDA | OK |
| 7.1 | docker compose restart + GET self | API disponible y DEMO-20261127104801 con stock 20 | API=True, 200, stock 20 | OK |
| 7.2 | Carga inicial no duplicada | 3 sedes y un ACE-001 en sede 1 | 3 sedes, ACE-001 x1 | OK |
| 8.1 | Swagger UI y /v3/api-docs sin encabezado | 200 y contrato con X-API-Version | swagger 200, api-docs 200, header documentado=True | OK |
| 8.2 | Frontend y OpenAPI vía puerto web | 200 index.html con #root; api-docs 200 | index 200, api-docs 200 | OK |

## Respuestas relevantes

### 3.1 POST /api/productos (DEMO-20261127104801, stock 20, sede 1)
```json
{
    "id":  10,
    "sku":  "DEMO-20261127104801",
    "nombre":  "Arroz blanco de 1 kg",
    "stock":  20,
    "sedeId":  1,
    "sedeNombre":  "Sede Medellín Centro",
    "creadoEn":  "2026-11-27T15:48:08.141990153Z",
    "_links":  {
                   "self":  {
                                "href":  "http://localhost:8080/api/productos/10"
                            },
                   "inventario":  {
                                      "href":  "http://localhost:8080/api/inventario?sedeId=1"
                                  }
               }
}
```

### 5.1 POST mismo SKU en sede 1 (minúsculas y espacios, stock 99)
```json
{
    "type":  "urn:inventario:error:sku-duplicado-en-sede",
    "title":  "El SKU ya existe en la sede",
    "status":  409,
    "detail":  "Ya existe el SKU DEMO-20261127104801 en la sede 1. El stock registrado (20) no se modificó.",
    "instance":  "/api/productos",
    "codigo":  "SKU_DUPLICADO_EN_SEDE",
    "timestamp":  "2026-11-27T15:48:08.818565343Z",
    "productoExistente":  {
                              "id":  10,
                              "sku":  "DEMO-20261127104801",
                              "sedeId":  1,
                              "stock":  20,
                              "href":  "http://localhost:8080/api/productos/10"
                          }
}
```

### 6.2 POST stock -5
```json
{
    "type":  "urn:inventario:error:validacion-fallida",
    "title":  "Datos del producto inválidos",
    "status":  400,
    "detail":  "El campo stockInicial debe ser un entero mayor o igual a cero",
    "instance":  "/api/productos",
    "codigo":  "VALIDACION_FALLIDA",
    "timestamp":  "2026-11-27T15:48:09.108554540Z",
    "errores":  [
                    {
                        "campo":  "stockInicial",
                        "mensaje":  "El campo stockInicial debe ser un entero mayor o igual a cero",
                        "valorRechazado":  -5
                    }
                ]
}
```

### 6.4 GET inventario sede inexistente (999)
```json
{
    "type":  "urn:inventario:error:sede-no-encontrada",
    "title":  "Recurso no encontrado",
    "status":  404,
    "detail":  "No existe una sede con id 999",
    "instance":  "/api/inventario?sedeId=999",
    "codigo":  "SEDE_NO_ENCONTRADA",
    "timestamp":  "2026-11-27T15:48:09.179258234Z"
}
```

### 6.5 GET inventario sede vacía (3)
```json
{
    "sede":  {
                 "id":  3,
                 "nombre":  "Sede Cali Sur",
                 "ciudad":  "Cali"
             },
    "totalProductos":  0,
    "totalUnidades":  0,
    "productos":  [

                  ],
    "_links":  {
                   "self":  {
                                "href":  "http://localhost:8080/api/inventario?sedeId=3"
                            },
                   "registrar-producto":  {
                                              "href":  "http://localhost:8080/api/productos"
                                          },
                   "almacenes":  {
                                     "href":  "http://localhost:8080/api/almacenes"
                                 }
               }
}
```

### 6.8 GET sin X-API-Version
```json
{
    "type":  "urn:inventario:error:version-requerida",
    "title":  "Falta el encabezado de versión",
    "status":  400,
    "detail":  "La petición debe incluir el encabezado X-API-Version. Ejemplo: X-API-Version: 1.",
    "instance":  "/api/inventario",
    "codigo":  "VERSION_REQUERIDA",
    "timestamp":  "2026-11-27T15:48:09.277114145Z",
    "versionesSoportadas":  [
                                "1"
                            ]
}
```

### 6.9 GET con X-API-Version: 2
```json
{
    "type":  "urn:inventario:error:version-no-soportada",
    "title":  "Versión de API no soportada",
    "status":  400,
    "detail":  "La versión \u00272\u0027 no está soportada. Versiones admitidas en X-API-Version: 1.",
    "instance":  "/api/inventario",
    "codigo":  "VERSION_NO_SOPORTADA",
    "timestamp":  "2026-11-27T15:48:09.294863392Z",
    "versionesSoportadas":  [
                                "1"
                            ]
}
```

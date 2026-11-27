# Informe · Fase 2 · Numeral 14: Caché con Redis y paginación

**Asignatura:** Arquitectura de Software, Universidad de Antioquia · **Equipo:** 5 · **Fecha:** 27 de noviembre de 2026

## 1. Resumen

- **Caché local con Redis 7.4.** Las páginas del inventario (`GET /api/inventario`) se guardan en Redis con un TTL
  configurable (10 minutos por defecto) y se invalidan cuando se registra un producto. La cabecera `X-Cache: HIT|MISS`
  indica si la respuesta salió de Redis o de MySQL.
- **Paginación.** `GET /api/inventario?sedeId=1&page=0&size=10` devuelve una página ordenada por SKU con metadatos
  `pagina` y enlaces HATEOAS `first`, `prev`, `next` y `last`.
- **Frontend.** Botones «Anterior» y «Siguiente», que siguen los enlaces `prev`/`next`, e indicador del estado de la caché.

## 2. Diseño de la caché

| Aspecto | Decisión |
|---|---|
| Tecnología | Spring Cache (`@Cacheable` / `@CacheEvict`) con `spring-boot-starter-data-redis` (cliente Lettuce) |
| Qué se cachea | El resultado del servicio `consultarInventario(sedeId, pagina, tamano)`: un objeto `InventarioPagina` con datos simples (no entidades JPA) |
| Clave | `inventario-api::inventario::{sedeId}:{page}:{size}`, por ejemplo `inventario-api::inventario::1:0:10` |
| Expiración | `spring.cache.redis.time-to-live` = `CACHE_TTL` (10 min por defecto) |
| Invalidación | `@CacheEvict(allEntries = true)` al registrar un producto con éxito (201). Un 409 o un 400 no invalidan nada |
| Errores | No se cachean: un 404 de sede siempre se consulta en MySQL |
| Tolerancia a fallos | `CacheErrorHandler` propio: si Redis no responde (timeout de 500 ms), se registra una advertencia y la consulta se resuelve en MySQL. La API no falla |
| Configuración de Redis | Solo memoria (`--save ""`, sin AOF), `maxmemory 64mb` y expulsión `allkeys-lru` |
| Observabilidad | `redis-exporter` publica aciertos, fallos, memoria y claves. Si se integra la rama de observabilidad, Prometheus lo descubre solo y Grafana muestra la tasa de aciertos |

Flujo de lectura: el controlador valida los parámetros y llama al servicio. Si la clave está en Redis, el método no
se ejecuta y la respuesta es **HIT**. Si no está, el método consulta MySQL, marca **MISS** y Spring guarda el resultado en Redis.

## 3. Contrato de paginación

| Parámetro | Valor por defecto | Regla | Error |
|---|---|---|---|
| `page` | 0 | entero ≥ 0 | 400 `PARAMETRO_INVALIDO` |
| `size` | 10 | entero entre 1 y 100 | 400 `PARAMETRO_INVALIDO` |

Ejemplo: `GET /api/inventario?sedeId=1&page=0&size=2` con `X-API-Version: 1`.

```json
{
  "sede": { "id": 1, "nombre": "Sede Medellín Centro", "ciudad": "Medellín" },
  "totalProductos": 3,
  "totalUnidades": 125,
  "pagina": { "numero": 0, "tamano": 2, "totalElementos": 3, "totalPaginas": 2 },
  "productos": [ { "sku": "ACE-001", "...": "..." }, { "sku": "AZU-001", "...": "..." } ],
  "_links": {
    "self":  { "href": "http://localhost:8080/api/inventario?sedeId=1&page=0&size=2" },
    "first": { "href": "http://localhost:8080/api/inventario?sedeId=1&page=0&size=2" },
    "next":  { "href": "http://localhost:8080/api/inventario?sedeId=1&page=1&size=2" },
    "last":  { "href": "http://localhost:8080/api/inventario?sedeId=1&page=1&size=2" },
    "registrar-producto": { "href": "http://localhost:8080/api/productos" },
    "almacenes": { "href": "http://localhost:8080/api/almacenes" }
  }
}
```

- `totalProductos` y `totalUnidades` se calculan sobre **toda** la sede, no solo sobre la página.
- `prev` no aparece en la primera página ni `next` en la última.
- Los enlaces hacia el inventario que no indican página (por ejemplo, `inventario` de un producto) usan los valores por defecto.
- Una página fuera de rango responde 200 con `productos: []` y los enlaces `first`/`last` para volver.

## 4. Archivos modificados

| Archivo | Cambio |
|---|---|
| `backend/pom.xml` | `spring-boot-starter-cache` y `spring-boot-starter-data-redis` |
| `backend/src/main/resources/application.yml` | `spring.data.redis` (host, timeouts) y `spring.cache.redis` (TTL, prefijo) |
| `config/CacheConfig.java` (nuevo) | `@EnableCaching` y manejador de errores tolerante |
| `service/InventarioPagina.java` (nuevo) | Página serializable que se guarda en Redis |
| `service/InventarioService.java` | Consulta paginada con `@Cacheable`; `@CacheEvict` al registrar |
| `repository/ProductoRepository.java` | `findByAlmacenId(Long, Pageable)` y `sumarStock` |
| `web/InventarioController.java` | Parámetros `page` y `size` validados; cabecera `X-Cache` |
| `web/CacheEstado.java` (nuevo) | Marca HIT/MISS por petición |
| `web/Representaciones.java`, `dto/InventarioModel.java` | Metadatos `pagina` y enlaces de paginación |
| `compose.yaml` | Servicios `redis` y `redis-exporter`; el backend espera a que Redis esté *healthy* |
| `frontend/src/App.jsx`, `api.js`, `styles.css` | Controles de paginación e indicador de caché |
| `InventarioApiTest.java`, `application-test.yml` | 3 pruebas nuevas; la caché se limpia antes de cada prueba |

## 5. Pruebas y evidencias

| Verificación | Resultado |
|---|---|
| `mvnw test` (incluye paginación, parámetros inválidos e invalidación de la caché) | **29/29 OK** |
| Prueba contra Docker (`scripts/prueba-cache-paginacion.ps1`) | **11/11 OK**: MISS en 523 ms y HIT en 17 ms; TTL de 600 s; invalidación con POST; 200 con Redis detenido. Detalle en `docs/evidencias/cache-paginacion-ultimo.md` |

La prueba contra Docker comprueba que la primera consulta es MISS y la segunda HIT, que la clave existe en Redis con su TTL,
que un POST la invalida, que la navegación `next`/`prev` funciona, que los parámetros inválidos responden 400 y que la
API sigue respondiendo 200 con Redis detenido.

## 6. Cómo ejecutarlo

```powershell
docker compose -f compose.yaml up --build -d     # solo la aplicación con Redis
powershell -ExecutionPolicy Bypass -File .\scripts\prueba-cache-paginacion.ps1

curl.exe -i -H "X-API-Version: 1" "http://localhost:8080/api/inventario?sedeId=1&page=0&size=2"   # X-Cache: MISS
curl.exe -i -H "X-API-Version: 1" "http://localhost:8080/api/inventario?sedeId=1&page=0&size=2"   # X-Cache: HIT
docker exec inventario-redis redis-cli --scan --pattern "inventario-api::*"
docker exec inventario-redis redis-cli TTL "inventario-api::inventario::1:0:2"
```

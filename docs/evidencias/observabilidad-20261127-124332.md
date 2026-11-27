# Evidencia de observabilidad · 2026-11-27 12:45:06

Comando: `powershell -ExecutionPolicy Bypass -File .\scripts\prueba-observabilidad.ps1 -ProbarAlerta`  
SKU de la corrida: `OBS-20261127124332` · trace id: `707c15e518b5e28f14110fd202d9ef5f`  
Resultado: **16/16 OK**

| Id | Verificación | Esperado | Observado | Resultado |
|---|---|---|---|---|
| 1 | docker compose ps | 9 servicios en ejecución y mysql-lector-grafana terminado con código 0 | todos arriba; lector de Grafana terminado OK=True | OK |
| 2 | Tráfico de prueba (SKU OBS-20261127124332, trace 707c15e518b5e28f14110fd202d9ef5f) | 201, 409, 400, 404 | 201, 409, 400, 404 | OK |
| 3.1 | Prometheus: objetivos de scraping | todos en estado up | 7/7 arriba  | OK |
| 3.2 | Métrica http_server_request_duration_seconds_count por código | incluye 200, 201, 400, 404 y 409 | 200, 201, 400, 404, 409 | OK |
| 3.3 | Métrica HTTP por ruta | /api/inventario, /api/productos, /api/almacenes | /api/almacenes, /api/inventario, /*, /api/productos | OK |
| 3.4 | Latencia p95 de /api (histograma) | valor numérico | 388,2 ms | OK |
| 3.5 | Métricas de JVM, HikariCP, MySQL y Nginx | presentes; mysql_up=1 y nginx_up=1 | heap=145 MiB, pool máx=10, mysql_up=1, nginx_up=1 | OK |
| 3.6 | Exemplars (métrica -> traza) | al menos un exemplar con trace_id | 27 exemplars | OK |
| 3.7 | Reglas de alerta cargadas y evaluadas | 7 reglas en estado ok | 7/7 ok: ApiInventarioCaida, MySQLNoDisponible, ComponenteMonitoreadoCaido, TasaErrores5xxAlta, LatenciaP95Alta, MemoriaHeapAlta, PoolConexionesConEspera | OK |
| 4.1 | Tempo: traza 707c15e518b5e28f14110fd202d9ef5f del POST | span POST /api/productos y span INSERT en MySQL | 11 spans: SELECT inventario.almacen / SELECT inventario / INSERT inventario.producto / AlmacenRepository.findById / ProductoRepository.findByAlmacenIdAndSku / ProductoRepository.saveAndFlush / POST /api/productos / Session.find co.edu.udea.arqsoft.inventario.domain.Almacen / SELECT / Session.persist co.edu.udea.arqsoft.inventario.domain.Producto / Transaction.commit | OK |
| 4.2 | Loki: logs con el mismo trace_id | líneas "POST /api/productos" y "Completed 201" | 2 líneas: POST "/api/productos", parameters={} / Completed 201 CREATED | OK |
| 5.1 | Grafana: salud de las fuentes de datos | prometheus, loki y mysql en OK | prometheus=OK, loki=OK, mysql-inventario=OK | OK |
| 5.2 | Grafana: tablero provisionado | "Inventario por sede · Observabilidad" | 200 Inventario por sede · Observabilidad, 26 paneles | OK |
| 5.3 | Grafana -> MySQL (usuario de solo lectura) | el producto OBS-20261127124332 con stock 15 | 200: OBS-20261127124332 = 15 | OK |
| 6.1 | Backend detenido -> alerta ApiInventarioCaida | estado firing | firing a los 51 s | OK |
| 6.2 | Backend iniciado de nuevo -> alerta resuelta | API 200 y alerta inactiva | API=True, alerta resuelta=True | OK |


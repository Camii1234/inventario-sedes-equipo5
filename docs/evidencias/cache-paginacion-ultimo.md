# Evidencia de caché Redis y paginación · 2026-11-27 12:54:47

Comando: `powershell -ExecutionPolicy Bypass -File .\scripts\prueba-cache-paginacion.ps1`  
Resultado: **11/11 OK**

| Id | Verificación | Esperado | Observado | Resultado |
|---|---|---|---|---|
| 1 | Redis en ejecución (redis-cli PING) | PONG | PONG | OK |
| 2.1 | Primera consulta (sede 1, página 0, size 2) | 200 X-Cache: MISS | 200 MISS en 523 ms | OK |
| 2.2 | Segunda consulta igual | 200 X-Cache: HIT | 200 HIT en 17 ms | OK |
| 2.3 | Clave inventario-api::inventario::1:0:2 en Redis | existe con TTL entre 1 y 600 s | TTL=600 s | OK |
| 3.1 | Metadatos de página | numero 0, tamano 2, sin prev, con next y last | numero=0, tamano=2, totalElementos=5, totalPaginas=3, prev=False, next=True | OK |
| 3.2 | Seguir enlace next | página 1, otros productos, enlace prev | numero=1, SKU=LEC-001,OBS-001, prev=http://localhost:8080/api/inventario?sedeId=1&page=0&size=2 | OK |
| 3.3 | Seguir enlace last | última página sin next | numero=2 de 3, next=False | OK |
| 3.4 | page=-1, size=0, size=101, size=abc | 400 en los cuatro | 400, 400, 400, 400 | OK |
| 4.1 | POST CACHE-20261127125435 (201) invalida la caché | 201 y clave eliminada (EXISTS=0) | 201, EXISTS=0 | OK |
| 4.2 | Consulta posterior | MISS e incluye CACHE-20261127125435 con stock 9 | MISS, stock=9 | OK |
| 5 | Consulta con Redis detenido | 200 desde MySQL (MISS) | 200 MISS en 1039 ms | OK |


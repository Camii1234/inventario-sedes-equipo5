<#
.SYNOPSIS
  Verificación de extremo a extremo de la observabilidad (numeral 13): métricas, alertas, trazas, logs y Grafana.

.DESCRIPTION
  Genera tráfico real contra la API (200, 201, 400, 404 y 409). Una de las peticiones lleva una cabecera W3C
  traceparent con un trace id conocido, que luego se busca en Tempo (traza) y en Loki (logs).
  Comprueba Prometheus (objetivos, métricas HTTP, exemplars y reglas de alerta), Grafana (fuentes de datos,
  tablero y consulta de negocio en MySQL). Con -ProbarAlerta detiene el backend, espera a que la alerta
  ApiInventarioCaida pase a "firing" y vuelve a iniciarlo.
  Guarda la evidencia en docs/evidencias/observabilidad-<fecha>.md y .json.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\prueba-observabilidad.ps1
  powershell -ExecutionPolicy Bypass -File .\scripts\prueba-observabilidad.ps1 -ProbarAlerta
#>
[CmdletBinding()]
param(
    [string]$ApiUrl = 'http://localhost:8080',
    [string]$PrometheusUrl = 'http://localhost:9090',
    [string]$GrafanaUrl = 'http://localhost:3001',
    [string]$GrafanaUsuario = 'admin',
    [string]$GrafanaClave = 'admin',
    [switch]$ProbarAlerta
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$raiz = Split-Path -Parent $PSScriptRoot
$marca = Get-Date -Format 'yyyyMMdd-HHmmss'
$sku = 'OBS-' + (Get-Date -Format 'yyyyMMddHHmmss')
$resultados = New-Object System.Collections.Generic.List[object]
$cliente = New-Object System.Net.Http.HttpClient
$cliente.Timeout = [TimeSpan]::FromSeconds(30)
$basic = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${GrafanaUsuario}:${GrafanaClave}"))

function New-HexId([int]$bytes) {
    $b = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return -join ($b | ForEach-Object { $_.ToString('x2') })
}

function Invoke-Http {
    param([string]$Metodo = 'GET', [string]$Url, [string]$Cuerpo = $null, [hashtable]$Cabeceras = @{})
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), $Url)
    foreach ($k in $Cabeceras.Keys) { [void]$req.Headers.TryAddWithoutValidation($k, $Cabeceras[$k]) }
    if ($Cuerpo) { $req.Content = New-Object System.Net.Http.StringContent($Cuerpo, [System.Text.Encoding]::UTF8, 'application/json') }
    $resp = $cliente.SendAsync($req).GetAwaiter().GetResult()
    $texto = [System.Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    $datos = $null
    if ($texto -and ($texto.TrimStart().StartsWith('{') -or $texto.TrimStart().StartsWith('['))) { $datos = $texto | ConvertFrom-Json }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Datos = $datos; Texto = $texto }
}

function Get-Prom([string]$Consulta) {
    $url = "$PrometheusUrl/api/v1/query?query=" + [Uri]::EscapeDataString($Consulta)
    return (Invoke-Http -Url $url).Datos.data.result
}

function Get-Grafana([string]$Ruta) { return Invoke-Http -Url "$GrafanaUrl$Ruta" -Cabeceras @{ Authorization = $basic } }

function Registrar {
    param([string]$Id, [string]$Operacion, [string]$Esperado, [string]$Observado, [bool]$Ok)
    $resultados.Add([pscustomobject]@{
        Id = $Id; Operacion = $Operacion; Esperado = $Esperado; Observado = $Observado
        Resultado = $(if ($Ok) { 'OK' } else { 'FALLA' })
    })
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1,-5} {2} -> {3}" -f $(if ($Ok) { 'OK ' } else { 'XX ' }), $Id, $Operacion, $Observado) -ForegroundColor $color
}

function Wait-Condicion([scriptblock]$Condicion, [int]$Segundos) {
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        try { if (& $Condicion) { return $true } } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

$v1 = @{ 'X-API-Version' = '1' }
$apiLista = { (Invoke-Http -Url "$ApiUrl/api/almacenes" -Cabeceras $v1).Status -eq 200 }

Write-Host "Prueba de observabilidad - API $ApiUrl - Prometheus $PrometheusUrl - Grafana $GrafanaUrl" -ForegroundColor Cyan

# 1. Contenedores
Push-Location $raiz
$previo = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
$ps = (docker compose ps -a --format '{{.Service}}|{{.State}}|{{.Status}}' 2>$null | Out-String).Trim()
$ErrorActionPreference = $previo
Pop-Location
$esperados = 'mysql', 'backend', 'frontend', 'prometheus', 'grafana', 'loki', 'tempo', 'mysqld-exporter', 'nginx-exporter'
$faltan = @($esperados | Where-Object { $ps -notmatch "(?m)^$([regex]::Escape($_))\|running" })
$lector = $ps -match '(?m)^mysql-lector-grafana\|exited\|Exited \(0\)'
Registrar '1' 'docker compose ps' '9 servicios en ejecución y mysql-lector-grafana terminado con código 0' `
    $(if ($faltan.Count -eq 0) { "todos arriba; lector de Grafana terminado OK=$lector" } else { 'faltan: ' + ($faltan -join ', ') }) `
    ($faltan.Count -eq 0 -and $lector)
if (-not (Wait-Condicion $apiLista 180)) { Write-Host 'La API no responde en 180 s.' -ForegroundColor Red; exit 2 }

# 2. Tráfico con todos los códigos de estado y una petición con trace id conocido
$traceId = New-HexId 16
$traceparent = "00-$traceId-$(New-HexId 8)-01"
foreach ($s in 1, 2, 3) { [void](Invoke-Http -Url "$ApiUrl/api/inventario?sedeId=$s" -Cabeceras $v1) }
$c404 = Invoke-Http -Url "$ApiUrl/api/inventario?sedeId=999" -Cabeceras $v1
$c400 = Invoke-Http -Url "$ApiUrl/api/inventario?sedeId=1"
$cuerpo = (@{ sedeId = 1; sku = $sku; nombre = 'Producto de prueba de observabilidad'; stockInicial = 15 } | ConvertTo-Json -Compress)
$c201 = Invoke-Http -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo $cuerpo -Cabeceras @{ 'X-API-Version' = '1'; traceparent = $traceparent }
$c409 = Invoke-Http -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo $cuerpo -Cabeceras $v1
$codigos = "$($c201.Status), $($c409.Status), $($c400.Status), $($c404.Status)"
Registrar '2' "Tráfico de prueba (SKU $sku, trace $traceId)" '201, 409, 400, 404' $codigos ($codigos -eq '201, 409, 400, 404')
Write-Host 'Esperando 20 s para que Prometheus, Tempo y Loki reciban los datos...' -ForegroundColor DarkGray
Start-Sleep -Seconds 20

# 3. Prometheus
$objetivos = (Invoke-Http -Url "$PrometheusUrl/api/v1/targets").Datos.data.activeTargets
$caidos = @($objetivos | Where-Object { $_.health -ne 'up' } | ForEach-Object { $_.labels.job })
Registrar '3.1' 'Prometheus: objetivos de scraping' 'todos en estado up' "$(@($objetivos).Count - $caidos.Count)/$(@($objetivos).Count) arriba $(if ($caidos) { '(' + ($caidos -join ', ') + ')' })" ($caidos.Count -eq 0 -and @($objetivos).Count -ge 7)

$porCodigo = Get-Prom 'sum by (http_response_status_code) (http_server_request_duration_seconds_count{job="inventario-api"})'
$vistos = @($porCodigo | ForEach-Object { $_.metric.http_response_status_code } | Sort-Object)
$todos = @('200', '201', '400', '404', '409' | Where-Object { $vistos -contains $_ }).Count -eq 5
Registrar '3.2' 'Métrica http_server_request_duration_seconds_count por código' 'incluye 200, 201, 400, 404 y 409' ($vistos -join ', ') $todos

$rutas = @(Get-Prom 'sum by (http_route) (http_server_request_duration_seconds_count{job="inventario-api"})' | ForEach-Object { $_.metric.http_route })
Registrar '3.3' 'Métrica HTTP por ruta' '/api/inventario, /api/productos, /api/almacenes' ($rutas -join ', ') (($rutas -contains '/api/inventario') -and ($rutas -contains '/api/productos') -and ($rutas -contains '/api/almacenes'))

$p95 = Get-Prom 'histogram_quantile(0.95, sum by (le) (rate(http_server_request_duration_seconds_bucket{job="inventario-api", http_route=~"/api/.+"}[5m])))'
$valorP95 = if ($p95) { [double]$p95[0].value[1] } else { $null }
Registrar '3.4' 'Latencia p95 de /api (histograma)' 'valor numérico' $(if ($null -ne $valorP95) { '{0:N1} ms' -f ($valorP95 * 1000) } else { 'sin datos' }) ($null -ne $valorP95)

$jvm = Get-Prom 'sum(jvm_memory_used_bytes{job="inventario-api", jvm_memory_type="heap"})'
$pool = Get-Prom 'max(db_client_connections_max{job="inventario-api"})'
$mysql = Get-Prom 'mysql_up'
$nginx = Get-Prom 'nginx_up'
$infra = "heap=$([math]::Round([double]$jvm[0].value[1] / 1MB)) MiB, pool máx=$($pool[0].value[1]), mysql_up=$($mysql[0].value[1]), nginx_up=$($nginx[0].value[1])"
Registrar '3.5' 'Métricas de JVM, HikariCP, MySQL y Nginx' 'presentes; mysql_up=1 y nginx_up=1' $infra ($jvm -and $pool -and $mysql[0].value[1] -eq '1' -and $nginx[0].value[1] -eq '1')

$inicio = [DateTimeOffset]::UtcNow.AddMinutes(-15).ToUnixTimeSeconds()
$fin = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$ex = (Invoke-Http -Url ("$PrometheusUrl/api/v1/query_exemplars?query=" + [Uri]::EscapeDataString('http_server_request_duration_seconds_bucket{job="inventario-api"}') + "&start=$inicio&end=$fin")).Datos.data
$nEx = @($ex | ForEach-Object { $_.exemplars }).Count
Registrar '3.6' 'Exemplars (métrica -> traza)' 'al menos un exemplar con trace_id' "$nEx exemplars" ($nEx -gt 0)

$reglas = (Invoke-Http -Url "$PrometheusUrl/api/v1/rules").Datos.data.groups | ForEach-Object { $_.rules }
$sanas = @($reglas | Where-Object { $_.health -eq 'ok' }).Count
Registrar '3.7' 'Reglas de alerta cargadas y evaluadas' '7 reglas en estado ok' "$sanas/$(@($reglas).Count) ok: $(($reglas | ForEach-Object { $_.name }) -join ', ')" ($sanas -eq 7)

# 4. Tempo y Loki (a través de Grafana)
$traza = Get-Grafana "/api/datasources/proxy/uid/tempo/api/traces/$traceId"
$spans = @($traza.Datos.batches | ForEach-Object { $_.scopeSpans } | ForEach-Object { $_.spans })
$nombres = @($spans | ForEach-Object { $_.name })
$conInsert = @($nombres | Where-Object { $_ -match '^INSERT' }).Count -gt 0
Registrar '4.1' "Tempo: traza $traceId del POST" 'span POST /api/productos y span INSERT en MySQL' "$($spans.Count) spans: $($nombres -join ' | ')" ($traza.Status -eq 200 -and ($nombres -contains 'POST /api/productos') -and $conInsert)

$q = [Uri]::EscapeDataString("{service_name=`"inventario-api`"} | trace_id=`"$traceId`"")
$desde = [DateTimeOffset]::UtcNow.AddMinutes(-15).ToUnixTimeMilliseconds() * 1000000
$logs = Get-Grafana "/api/datasources/proxy/uid/loki/loki/api/v1/query_range?query=$q&start=$desde&limit=50"
$lineas = @($logs.Datos.data.result | ForEach-Object { $_.values } | ForEach-Object { $_[1] })
Registrar '4.2' 'Loki: logs con el mismo trace_id' 'líneas "POST /api/productos" y "Completed 201"' "$($lineas.Count) líneas: $(($lineas | Select-Object -First 3) -join ' | ')" ($lineas.Count -gt 0 -and (($lineas -join "`n") -match 'Completed 201'))

# 5. Grafana
$salud = foreach ($uid in 'prometheus', 'loki', 'mysql-inventario') { "$uid=$((Get-Grafana "/api/datasources/uid/$uid/health").Datos.status)" }
Registrar '5.1' 'Grafana: salud de las fuentes de datos' 'prometheus, loki y mysql en OK' ($salud -join ', ') (($salud | Where-Object { $_ -notmatch '=OK$' }).Count -eq 0)
$tablero = Get-Grafana '/api/dashboards/uid/inventario-api'
$nPaneles = @($tablero.Datos.dashboard.panels | Where-Object { $_.type -ne 'row' }).Count
Registrar '5.2' 'Grafana: tablero provisionado' '"Inventario por sede · Observabilidad"' "$($tablero.Status) $($tablero.Datos.dashboard.title), $nPaneles paneles" ($tablero.Status -eq 200 -and $nPaneles -ge 20)
$consulta = @{ queries = @(@{ refId = 'A'; datasource = @{ uid = 'mysql-inventario' }; format = 'table'; rawSql = "SELECT sku, stock FROM producto WHERE sku = '$sku'" }); from = 'now-1h'; to = 'now' } | ConvertTo-Json -Depth 6 -Compress
$ds = Invoke-Http -Metodo POST -Url "$GrafanaUrl/api/ds/query" -Cuerpo $consulta -Cabeceras @{ Authorization = $basic }
$valores = $ds.Datos.results.A.frames[0].data.values
Registrar '5.3' 'Grafana -> MySQL (usuario de solo lectura)' "el producto $sku con stock 15" "$($ds.Status): $(($valores | ForEach-Object { $_ -join '' }) -join ' = ')" ($ds.Status -eq 200 -and $valores[0][0] -eq $sku -and $valores[1][0] -eq 15)

# 6. Alerta (opcional): backend detenido -> ApiInventarioCaida en firing -> backend iniciado -> resuelta
if ($ProbarAlerta) {
    Push-Location $raiz
    $ErrorActionPreference = 'Continue'
    docker compose stop backend 2>$null | Out-Null
    $detenido = Get-Date
    $disparada = Wait-Condicion { @(Get-Prom 'ALERTS{alertname="ApiInventarioCaida", alertstate="firing"}').Count -gt 0 } 150
    $segundos = [int]((Get-Date) - $detenido).TotalSeconds
    Registrar '6.1' 'Backend detenido -> alerta ApiInventarioCaida' 'estado firing' $(if ($disparada) { "firing a los $segundos s" } else { 'no se disparó en 150 s' }) $disparada
    docker compose start backend 2>$null | Out-Null
    $ErrorActionPreference = 'Stop'
    Pop-Location
    $volvio = Wait-Condicion $apiLista 180
    $resuelta = Wait-Condicion { @(Get-Prom 'ALERTS{alertname="ApiInventarioCaida"}').Count -eq 0 } 120
    Registrar '6.2' 'Backend iniciado de nuevo -> alerta resuelta' 'API 200 y alerta inactiva' "API=$volvio, alerta resuelta=$resuelta" ($volvio -and $resuelta)
}

# Evidencia
$ok = @($resultados | Where-Object { $_.Resultado -eq 'OK' }).Count
$total = $resultados.Count
$dir = Join-Path $raiz 'docs\evidencias'
New-Item -ItemType Directory -Force $dir | Out-Null
$json = [pscustomobject]@{ fecha = (Get-Date).ToString('s'); sku = $sku; traceId = $traceId; ok = $ok; total = $total; resultados = $resultados }
$json | ConvertTo-Json -Depth 5 | Out-File -Encoding utf8 (Join-Path $dir "observabilidad-$marca.json")
$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# Evidencia de observabilidad · $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
[void]$md.AppendLine()
[void]$md.AppendLine("Comando: ``powershell -ExecutionPolicy Bypass -File .\scripts\prueba-observabilidad.ps1$(if ($ProbarAlerta) { ' -ProbarAlerta' })``  ")
[void]$md.AppendLine("SKU de la corrida: ``$sku`` · trace id: ``$traceId``  ")
[void]$md.AppendLine("Resultado: **$ok/$total OK**")
[void]$md.AppendLine()
[void]$md.AppendLine('| Id | Verificación | Esperado | Observado | Resultado |')
[void]$md.AppendLine('|---|---|---|---|---|')
foreach ($r in $resultados) {
    $obs = ($r.Observado -replace '\|', '/' -replace "`r?`n", ' ')
    [void]$md.AppendLine("| $($r.Id) | $($r.Operacion) | $($r.Esperado) | $obs | $($r.Resultado) |")
}
$md.ToString() | Out-File -Encoding utf8 (Join-Path $dir "observabilidad-$marca.md")
$md.ToString() | Out-File -Encoding utf8 (Join-Path $dir 'observabilidad-ultimo.md')

Write-Host ''
Write-Host "Resultado: $ok/$total OK. Evidencia en docs/evidencias/observabilidad-$marca.md" -ForegroundColor $(if ($ok -eq $total) { 'Green' } else { 'Red' })
if ($ok -ne $total) { exit 1 }

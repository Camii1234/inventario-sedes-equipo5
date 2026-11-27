<#
.SYNOPSIS
  Prueba contra Docker de la caché Redis y la paginación (numeral 14).
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\prueba-cache-paginacion.ps1
#>
[CmdletBinding()]
param([string]$ApiUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$raiz = Split-Path -Parent $PSScriptRoot
$marca = Get-Date -Format 'yyyyMMdd-HHmmss'
$sku = 'CACHE-' + (Get-Date -Format 'yyyyMMddHHmmss')
$resultados = New-Object System.Collections.Generic.List[object]
$cliente = New-Object System.Net.Http.HttpClient

function Invoke-Api([string]$Url, [string]$Metodo = 'GET', [string]$Cuerpo = $null) {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), $Url)
    [void]$req.Headers.TryAddWithoutValidation('X-API-Version', '1')
    if ($Cuerpo) { $req.Content = New-Object System.Net.Http.StringContent($Cuerpo, [System.Text.Encoding]::UTF8, 'application/json') }
    $inicio = Get-Date
    $resp = $cliente.SendAsync($req).GetAwaiter().GetResult()
    $ms = [int]((Get-Date) - $inicio).TotalMilliseconds
    $texto = [System.Text.Encoding]::UTF8.GetString($resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
    $cache = $null
    if ($resp.Headers.Contains('X-Cache')) { $cache = @($resp.Headers.GetValues('X-Cache'))[0] }
    $datos = $null
    if ($texto.TrimStart().StartsWith('{')) { $datos = $texto | ConvertFrom-Json }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Cache = $cache; Datos = $datos; Ms = $ms }
}

function Registrar([string]$Id, [string]$Operacion, [string]$Esperado, [string]$Observado, [bool]$Ok) {
    $resultados.Add([pscustomobject]@{ Id = $Id; Operacion = $Operacion; Esperado = $Esperado; Observado = $Observado; Resultado = $(if ($Ok) { 'OK' } else { 'FALLA' }) })
    Write-Host ("[{0}] {1,-4} {2} -> {3}" -f $(if ($Ok) { 'OK ' } else { 'XX ' }), $Id, $Operacion, $Observado) -ForegroundColor $(if ($Ok) { 'Green' } else { 'Red' })
}

function Redis([string[]]$Argumentos) {
    $previo = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $salida = (docker exec inventario-redis redis-cli @Argumentos 2>$null | Out-String).Trim()
    $ErrorActionPreference = $previo
    return $salida
}

function Esperar-Api {
    $limite = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $limite) {
        try { if ((Invoke-Api "$ApiUrl/api/almacenes").Status -eq 200) { return $true } } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "Prueba de caché y paginación - $ApiUrl - SKU $sku" -ForegroundColor Cyan
if (-not (Esperar-Api)) { Write-Host 'La API no responde.' -ForegroundColor Red; exit 2 }
Registrar '1' 'Redis en ejecución (redis-cli PING)' 'PONG' (Redis @('PING')) ((Redis @('PING')) -eq 'PONG')

# 2. Caché: MISS -> HIT -> clave en Redis con TTL
[void](Redis @('FLUSHDB'))
$url = "$ApiUrl/api/inventario?sedeId=1&page=0&size=2"
$a = Invoke-Api $url
$b = Invoke-Api $url
Registrar '2.1' 'Primera consulta (sede 1, página 0, size 2)' '200 X-Cache: MISS' "$($a.Status) $($a.Cache) en $($a.Ms) ms" ($a.Status -eq 200 -and $a.Cache -eq 'MISS')
Registrar '2.2' 'Segunda consulta igual' '200 X-Cache: HIT' "$($b.Status) $($b.Cache) en $($b.Ms) ms" ($b.Status -eq 200 -and $b.Cache -eq 'HIT')
$clave = 'inventario-api::inventario::1:0:2'
$ttl = [int](Redis @('TTL', $clave))
Registrar '2.3' "Clave $clave en Redis" 'existe con TTL entre 1 y 600 s' "TTL=$ttl s" ($ttl -gt 0 -and $ttl -le 600)

# 3. Paginación y HATEOAS
$p0 = $a.Datos
Registrar '3.1' 'Metadatos de página' 'numero 0, tamano 2, sin prev, con next y last' "numero=$($p0.pagina.numero), tamano=$($p0.pagina.tamano), totalElementos=$($p0.pagina.totalElementos), totalPaginas=$($p0.pagina.totalPaginas), prev=$([bool]$p0._links.prev), next=$([bool]$p0._links.next)" ($p0.pagina.numero -eq 0 -and $p0.pagina.tamano -eq 2 -and @($p0.productos).Count -le 2 -and -not $p0._links.prev -and $p0._links.next -and $p0._links.last)
$p1 = (Invoke-Api $p0._links.next.href).Datos
$solapan = @($p1.productos | Where-Object { @($p0.productos.sku) -contains $_.sku }).Count
Registrar '3.2' 'Seguir enlace next' 'página 1, otros productos, enlace prev' "numero=$($p1.pagina.numero), SKU=$(@($p1.productos.sku) -join ','), prev=$($p1._links.prev.href)" ($p1.pagina.numero -eq 1 -and $solapan -eq 0 -and $p1._links.prev)
$ultima = (Invoke-Api $p0._links.last.href).Datos
Registrar '3.3' 'Seguir enlace last' 'última página sin next' "numero=$($ultima.pagina.numero) de $($ultima.pagina.totalPaginas), next=$([bool]$ultima._links.next)" ($ultima.pagina.numero -eq $ultima.pagina.totalPaginas - 1 -and -not $ultima._links.next)
$invalidos = foreach ($q in 'page=-1', 'size=0', 'size=101', 'size=abc') { (Invoke-Api "$ApiUrl/api/inventario?sedeId=1&$q").Status }
Registrar '3.4' 'page=-1, size=0, size=101, size=abc' '400 en los cuatro' ($invalidos -join ', ') (@($invalidos | Where-Object { $_ -ne 400 }).Count -eq 0)

# 4. Invalidación al registrar
$post = Invoke-Api "$ApiUrl/api/productos" 'POST' (@{ sedeId = 1; sku = $sku; nombre = 'Producto prueba caché'; stockInicial = 9 } | ConvertTo-Json -Compress)
$existe = Redis @('EXISTS', $clave)
Registrar '4.1' "POST $sku (201) invalida la caché" '201 y clave eliminada (EXISTS=0)' "$($post.Status), EXISTS=$existe" ($post.Status -eq 201 -and $existe -eq '0')
$despues = Invoke-Api "$ApiUrl/api/inventario?sedeId=1&size=100"
$stock = @($despues.Datos.productos | Where-Object { $_.sku -eq $sku })[0].stock
Registrar '4.2' 'Consulta posterior' "MISS e incluye $sku con stock 9" "$($despues.Cache), stock=$stock" ($despues.Cache -eq 'MISS' -and $stock -eq 9)

# 5. Tolerancia a fallos: Redis detenido
Push-Location $raiz
$previo = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
docker compose -f compose.yaml stop redis 2>$null | Out-Null
$sinRedis = Invoke-Api "$ApiUrl/api/inventario?sedeId=2"
docker compose -f compose.yaml start redis 2>$null | Out-Null
$ErrorActionPreference = $previo
Pop-Location
Registrar '5' 'Consulta con Redis detenido' '200 desde MySQL (MISS)' "$($sinRedis.Status) $($sinRedis.Cache) en $($sinRedis.Ms) ms" ($sinRedis.Status -eq 200 -and $sinRedis.Cache -eq 'MISS')

$ok = @($resultados | Where-Object { $_.Resultado -eq 'OK' }).Count
$total = $resultados.Count
$dir = Join-Path $raiz 'docs\evidencias'
New-Item -ItemType Directory -Force $dir | Out-Null
$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# Evidencia de caché Redis y paginación · $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
[void]$md.AppendLine()
[void]$md.AppendLine("Comando: ``powershell -ExecutionPolicy Bypass -File .\scripts\prueba-cache-paginacion.ps1``  ")
[void]$md.AppendLine("Resultado: **$ok/$total OK**")
[void]$md.AppendLine()
[void]$md.AppendLine('| Id | Verificación | Esperado | Observado | Resultado |')
[void]$md.AppendLine('|---|---|---|---|---|')
foreach ($r in $resultados) { [void]$md.AppendLine("| $($r.Id) | $($r.Operacion) | $($r.Esperado) | $($r.Observado -replace '\|', '/') | $($r.Resultado) |") }
$md.ToString() | Out-File -Encoding utf8 (Join-Path $dir "cache-paginacion-$marca.md")
$md.ToString() | Out-File -Encoding utf8 (Join-Path $dir 'cache-paginacion-ultimo.md')
Write-Host "Resultado: $ok/$total OK" -ForegroundColor $(if ($ok -eq $total) { 'Green' } else { 'Red' })
if ($ok -ne $total) { exit 1 }

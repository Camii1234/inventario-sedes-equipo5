<#
.SYNOPSIS
  Prueba de extremo a extremo de la API de inventario desplegada con Docker Compose.

.DESCRIPTION
  Recorre la demostración de aceptación de la fase 1 contra los puertos publicados:
  inventario de las sedes iniciales, creación con 201 + Location + enlaces, navegación HATEOAS,
  409 por duplicado, mismo SKU en otra sede, validaciones, versionamiento, Swagger y frontend.
  Cada ejecución usa un SKU nuevo (DEMO-<fecha-hora>), por lo que se puede repetir sin limpiar la base.
  Con -ProbarReinicio reinicia los servicios (docker compose restart) y comprueba que los datos persisten.
  Guarda la evidencia en docs/evidencias/e2e-<fecha>.md y .json.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1
  powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1 -ProbarReinicio
#>
[CmdletBinding()]
param(
    [string]$ApiUrl = 'http://localhost:8080',
    [string]$WebUrl = 'http://localhost:3000',
    [switch]$ProbarReinicio
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$raiz = Split-Path -Parent $PSScriptRoot
$marca = Get-Date -Format 'yyyyMMdd-HHmmss'
$sku = 'DEMO-' + (Get-Date -Format 'yyyyMMddHHmmss')
$resultados = New-Object System.Collections.Generic.List[object]
$cliente = New-Object System.Net.Http.HttpClient
$cliente.Timeout = [TimeSpan]::FromSeconds(30)

function Invoke-Api {
    param(
        [string]$Metodo = 'GET',
        [string]$Url,
        [object]$Cuerpo = $null,
        [string]$Version = '1',
        [switch]$SinVersion
    )
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($Metodo), $Url)
    if (-not $SinVersion) { [void]$req.Headers.TryAddWithoutValidation('X-API-Version', $Version) }
    [void]$req.Headers.TryAddWithoutValidation('Accept', 'application/hal+json, application/problem+json, */*')
    if ($null -ne $Cuerpo) {
        $json = if ($Cuerpo -is [string]) { $Cuerpo } else { $Cuerpo | ConvertTo-Json -Compress }
        $req.Content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    $resp = $cliente.SendAsync($req).GetAwaiter().GetResult()
    $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $texto = [System.Text.Encoding]::UTF8.GetString($bytes)
    $datos = $null
    if ($texto -and $texto.TrimStart().StartsWith('{')) { $datos = $texto | ConvertFrom-Json }
    $location = $null
    if ($resp.Headers.Location) { $location = $resp.Headers.Location.ToString() }
    $tipo = $null
    if ($resp.Content.Headers.ContentType) { $tipo = $resp.Content.Headers.ContentType.MediaType }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Location = $location; Tipo = $tipo; Datos = $datos; Texto = $texto }
}

function Registrar {
    param([string]$Id, [string]$Operacion, [string]$Esperado, [string]$Observado, [bool]$Ok, [string]$Evidencia = '')
    $resultados.Add([pscustomobject]@{
        Id = $Id; Operacion = $Operacion; Esperado = $Esperado; Observado = $Observado
        Resultado = $(if ($Ok) { 'OK' } else { 'FALLA' }); Evidencia = $Evidencia
    })
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1,-4} {2} -> {3}" -f $(if ($Ok) { 'OK ' } else { 'XX ' }), $Id, $Operacion, $Observado) -ForegroundColor $color
}

function Get-Stock($inventario, [string]$codigo) {
    $p = @($inventario.productos | Where-Object { $_.sku -eq $codigo })
    if ($p.Count -eq 0) { return $null }
    return $p[0].stock
}

function Wait-Api {
    $limite = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $limite) {
        try { if ((Invoke-Api -Url "$ApiUrl/api/almacenes").Status -eq 200) { return $true } } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "Prueba E2E - API $ApiUrl - Frontend $WebUrl - SKU de esta corrida: $sku" -ForegroundColor Cyan

# 1. Contenedores en ejecución
$psSalida = ''
try {
    Push-Location $raiz
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $psSalida = (docker compose ps --format 'table {{.Name}}\t{{.Service}}\t{{.Status}}\t{{.Ports}}' 2>$null | Out-String).Trim()
    $ErrorActionPreference = $previo
    Pop-Location
    $arriba = ($psSalida -match 'inventario-backend') -and ($psSalida -match 'inventario-mysql') -and ($psSalida -match 'inventario-frontend')
    Registrar '1' 'docker compose ps' '3 servicios en ejecución' $(if ($arriba) { 'mysql, backend y frontend arriba' } else { 'faltan servicios' }) $arriba 'docker compose ps'
} catch {
    Registrar '1' 'docker compose ps' '3 servicios en ejecución' "No se pudo ejecutar docker: $($_.Exception.Message)" $false
}
if (-not (Wait-Api)) { Write-Host 'La API no responde en 180 s.' -ForegroundColor Red; exit 2 }

# 2. Inventario de las dos sedes iniciales
$almacenes = Invoke-Api -Url "$ApiUrl/api/almacenes"
Registrar '2.0' 'GET /api/almacenes' '200 con 3 sedes' "$($almacenes.Status) con $(@($almacenes.Datos.almacenes).Count) sedes" ($almacenes.Status -eq 200 -and @($almacenes.Datos.almacenes).Count -ge 3) ($almacenes.Datos._links.self.href)

$inv1 = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=1"
$inv2 = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=2"
$s1 = Get-Stock $inv1.Datos 'ACE-001'; $s2 = Get-Stock $inv2.Datos 'ACE-001'
Registrar '2.1' 'GET /api/inventario?sedeId=1' "200, sede 1, $($inv1.Tipo)" "$($inv1.Status), sede $($inv1.Datos.sede.id) $($inv1.Datos.sede.nombre), $($inv1.Datos.totalProductos) productos" ($inv1.Status -eq 200 -and $inv1.Datos.sede.id -eq 1) ($inv1.Datos._links.self.href)
Registrar '2.2' 'GET /api/inventario?sedeId=2' '200, sede 2' "$($inv2.Status), sede $($inv2.Datos.sede.id) $($inv2.Datos.sede.nombre), $($inv2.Datos.totalProductos) productos" ($inv2.Status -eq 200 -and $inv2.Datos.sede.id -eq 2) ($inv2.Datos._links.self.href)
Registrar '2.3' 'SKU compartido ACE-001' 'stock distinto en sede 1 y 2' "sede 1 = $s1, sede 2 = $s2" ($null -ne $s1 -and $null -ne $s2 -and $s1 -ne $s2)
$cruce1 = @($inv1.Datos.productos | Where-Object { $_.sedeId -ne 1 }).Count
$cruce2 = @($inv2.Datos.productos | Where-Object { $_.sedeId -ne 2 }).Count
$soloSede2 = @($inv1.Datos.productos | Where-Object { $_.sku -eq 'CAF-001' }).Count
Registrar '2.4' 'Aislamiento entre sedes' 'ningún registro de otra sede; CAF-001 solo en sede 2' "ajenos en sede1=$cruce1, sede2=$cruce2; CAF-001 en sede1=$soloSede2" ($cruce1 -eq 0 -and $cruce2 -eq 0 -and $soloSede2 -eq 0)
$skus1 = @($inv1.Datos.productos | ForEach-Object { $_.sku })
$skusOrdenados = [string[]]$skus1.Clone()
[Array]::Sort($skusOrdenados, [StringComparer]::Ordinal)
$ordenado = ($skusOrdenados -join ',') -eq ($skus1 -join ',')
Registrar '2.5' 'Orden estable del inventario' 'productos ordenados por SKU' ($skus1 -join ', ') $ordenado

# 3. Crear producto con stock 20 en la sede 1
$crear = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 1; sku = $sku; nombre = 'Arroz blanco de 1 kg'; stockInicial = 20 }
$self = $crear.Datos._links.self.href
$linkInv = $crear.Datos._links.inventario.href
Registrar '3.1' "POST /api/productos ($sku, stock 20, sede 1)" '201 Created' "$($crear.Status) $($crear.Tipo), id $($crear.Datos.id), stock $($crear.Datos.stock)" ($crear.Status -eq 201 -and $crear.Datos.stock -eq 20) $crear.Texto
Registrar '3.2' 'Cabecera Location' 'igual a _links.self.href' "Location=$($crear.Location)" ($crear.Location -and $crear.Location -eq $self) $crear.Location
Registrar '3.3' 'Enlaces del POST' 'self e inventario?sedeId=1' "self=$self ; inventario=$linkInv" ($self -match '/api/productos/\d+$' -and $linkInv -match '/api/inventario\?sedeId=1$')

# 4. Seguir self e inventario (enviando X-API-Version)
$det = Invoke-Api -Url $self
Registrar '4.1' 'GET enlace self' "200, $sku, sede 1, stock 20" "$($det.Status), $($det.Datos.sku), sede $($det.Datos.sedeId), stock $($det.Datos.stock)" ($det.Status -eq 200 -and $det.Datos.sku -eq $sku -and $det.Datos.sedeId -eq 1 -and $det.Datos.stock -eq 20) $self
$invSeguido = Invoke-Api -Url $linkInv
Registrar '4.2' 'GET enlace inventario' "200, sede 1 incluye $sku con 20" "$($invSeguido.Status), sede $($invSeguido.Datos.sede.id), stock $sku = $(Get-Stock $invSeguido.Datos $sku)" ($invSeguido.Status -eq 200 -and $invSeguido.Datos.sede.id -eq 1 -and (Get-Stock $invSeguido.Datos $sku) -eq 20) $linkInv
$primerDetalle = @($invSeguido.Datos.productos)[0]._links.self.href
$detInv = Invoke-Api -Url $primerDetalle
Registrar '4.3' 'GET enlace self de un producto del inventario' '200 con ese producto' "$($detInv.Status), $($detInv.Datos.sku)" ($detInv.Status -eq 200) $primerDetalle
$sinVersionLink = Invoke-Api -Url $self -SinVersion
Registrar '4.4' 'Seguir self sin X-API-Version' '400 VERSION_REQUERIDA' "$($sinVersionLink.Status) $($sinVersionLink.Datos.codigo)" ($sinVersionLink.Status -eq 400 -and $sinVersionLink.Datos.codigo -eq 'VERSION_REQUERIDA')

# 4b. Enlaces generados detrás del proxy del frontend (los que usa el navegador)
try {
    $invWeb = Invoke-Api -Url "$WebUrl/api/inventario?sedeId=1"
    $selfWeb = $invWeb.Datos._links.self.href
    $seguidoWeb = Invoke-Api -Url $selfWeb
    Registrar '4.5' 'HATEOAS vía proxy del frontend' "self con origen $WebUrl y navegable" "$($invWeb.Status); self=$selfWeb -> $($seguidoWeb.Status)" ($invWeb.Status -eq 200 -and $selfWeb.StartsWith($WebUrl) -and $seguidoWeb.Status -eq 200) $selfWeb
} catch {
    Registrar '4.5' 'HATEOAS vía proxy del frontend' "self con origen $WebUrl" "error: $($_.Exception.Message)" $false
}

# 5. Duplicado en la misma sede y mismo SKU en otra sede
$dup = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 1; sku = '  ' + $sku.ToLower() + ' '; nombre = 'Intento duplicado'; stockInicial = 99 }
Registrar '5.1' 'POST mismo SKU en sede 1 (minúsculas y espacios, stock 99)' '409 SKU_DUPLICADO_EN_SEDE' "$($dup.Status) $($dup.Tipo) $($dup.Datos.codigo): $($dup.Datos.detail)" ($dup.Status -eq 409 -and $dup.Datos.codigo -eq 'SKU_DUPLICADO_EN_SEDE') $dup.Texto
$trasDup = Invoke-Api -Url $self
Registrar '5.2' 'Stock tras el 409' 'sigue en 20' "stock = $($trasDup.Datos.stock)" ($trasDup.Datos.stock -eq 20) $self
$otra = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 2; sku = $sku; nombre = 'Arroz blanco de 1 kg'; stockInicial = 8 }
Registrar '5.3' 'POST mismo SKU en sede 2 con stock 8' '201 Created' "$($otra.Status), sede $($otra.Datos.sedeId), stock $($otra.Datos.stock)" ($otra.Status -eq 201 -and $otra.Datos.stock -eq 8 -and $otra.Datos.sedeId -eq 2) $otra.Location
$a = Get-Stock (Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=1").Datos $sku
$b = Get-Stock (Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=2").Datos $sku
Registrar '5.4' 'Ambos valores del SKU' 'sede 1 = 20, sede 2 = 8' "sede 1 = $a, sede 2 = $b" ($a -eq 20 -and $b -eq 8)

# 6. Validaciones, sede vacía y versionamiento
$cero = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 2; sku = "$sku-CERO"; nombre = 'Producto con stock cero'; stockInicial = 0 }
Registrar '6.1' 'POST stock 0' '201 (cero es válido)' "$($cero.Status), stock $($cero.Datos.stock)" ($cero.Status -eq 201 -and $cero.Datos.stock -eq 0) $cero.Location
$neg = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 1; sku = "$sku-NEG"; nombre = 'Producto negativo'; stockInicial = -5 }
Registrar '6.2' 'POST stock -5' '400 VALIDACION_FALLIDA en stockInicial' "$($neg.Status) $($neg.Datos.codigo): $($neg.Datos.detail)" ($neg.Status -eq 400 -and $neg.Datos.codigo -eq 'VALIDACION_FALLIDA' -and @($neg.Datos.errores | Where-Object { $_.campo -eq 'stockInicial' }).Count -eq 1) $neg.Texto
$postSede = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 999; sku = "$sku-X"; nombre = 'Sede inexistente'; stockInicial = 1 }
Registrar '6.3' 'POST sede inexistente (999)' '404 SEDE_NO_ENCONTRADA' "$($postSede.Status) $($postSede.Datos.codigo)" ($postSede.Status -eq 404 -and $postSede.Datos.codigo -eq 'SEDE_NO_ENCONTRADA')
$getSede = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=999"
Registrar '6.4' 'GET inventario sede inexistente (999)' '404 SEDE_NO_ENCONTRADA' "$($getSede.Status) $($getSede.Tipo) $($getSede.Datos.codigo): $($getSede.Datos.detail)" ($getSede.Status -eq 404 -and $getSede.Datos.codigo -eq 'SEDE_NO_ENCONTRADA') $getSede.Texto
$vacia = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=3"
Registrar '6.5' 'GET inventario sede vacía (3)' '200 con productos = []' "$($vacia.Status), $($vacia.Datos.sede.nombre), totalProductos $($vacia.Datos.totalProductos)" ($vacia.Status -eq 200 -and $vacia.Datos.totalProductos -eq 0) $vacia.Texto
$sinSede = Invoke-Api -Url "$ApiUrl/api/inventario"
Registrar '6.6' 'GET inventario sin sedeId' '400 PARAMETRO_REQUERIDO' "$($sinSede.Status) $($sinSede.Datos.codigo): $($sinSede.Datos.detail)" ($sinSede.Status -eq 400 -and $sinSede.Datos.codigo -eq 'PARAMETRO_REQUERIDO')
$sedeTxt = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=abc"
Registrar '6.7' 'GET inventario sedeId=abc' '400 PARAMETRO_INVALIDO' "$($sedeTxt.Status) $($sedeTxt.Datos.codigo)" ($sedeTxt.Status -eq 400 -and $sedeTxt.Datos.codigo -eq 'PARAMETRO_INVALIDO')
$sinVer = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=1" -SinVersion
Registrar '6.8' 'GET sin X-API-Version' '400 VERSION_REQUERIDA' "$($sinVer.Status) $($sinVer.Datos.codigo): $($sinVer.Datos.detail)" ($sinVer.Status -eq 400 -and $sinVer.Datos.codigo -eq 'VERSION_REQUERIDA') $sinVer.Texto
$ver2 = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=1" -Version '2'
Registrar '6.9' 'GET con X-API-Version: 2' '400 VERSION_NO_SOPORTADA (versiones admitidas: 1)' "$($ver2.Status) $($ver2.Datos.codigo): $($ver2.Datos.detail)" ($ver2.Status -eq 400 -and $ver2.Datos.codigo -eq 'VERSION_NO_SOPORTADA') $ver2.Texto
$postSinVer = Invoke-Api -Metodo POST -Url "$ApiUrl/api/productos" -Cuerpo @{ sedeId = 1; sku = "$sku-SV"; nombre = 'Sin version'; stockInicial = 1 } -SinVersion
Registrar '6.10' 'POST sin X-API-Version' '400 VERSION_REQUERIDA' "$($postSinVer.Status) $($postSinVer.Datos.codigo)" ($postSinVer.Status -eq 400 -and $postSinVer.Datos.codigo -eq 'VERSION_REQUERIDA')

# 7. Persistencia tras reinicio ordinario
if ($ProbarReinicio) {
    Push-Location $raiz
    $previo = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'   # docker escribe el progreso en stderr
    Write-Host 'Reiniciando servicios (docker compose restart)...' -ForegroundColor Yellow
    docker compose restart *> $null
    $ErrorActionPreference = $previo
    Pop-Location
    $vuelve = Wait-Api
    $trasReinicio = Invoke-Api -Url $self
    $inv1b = Invoke-Api -Url "$ApiUrl/api/inventario?sedeId=1"
    $aceites = @($inv1b.Datos.productos | Where-Object { $_.sku -eq 'ACE-001' }).Count
    $nAlm = @((Invoke-Api -Url "$ApiUrl/api/almacenes").Datos.almacenes).Count
    Registrar '7.1' 'docker compose restart + GET self' "API disponible y $sku con stock 20" "API=$vuelve, $($trasReinicio.Status), stock $($trasReinicio.Datos.stock)" ($vuelve -and $trasReinicio.Status -eq 200 -and $trasReinicio.Datos.stock -eq 20) $self
    Registrar '7.2' 'Carga inicial no duplicada' '3 sedes y un ACE-001 en sede 1' "$nAlm sedes, ACE-001 x$aceites" ($nAlm -eq 3 -and $aceites -eq 1)
} else {
    Write-Host 'Paso 7 omitido (use -ProbarReinicio para reiniciar los servicios y comprobar persistencia).' -ForegroundColor Yellow
}

# 8. Swagger, OpenAPI y frontend
$sw = Invoke-Api -Url "$ApiUrl/swagger-ui/index.html" -SinVersion
$docs = Invoke-Api -Url "$ApiUrl/v3/api-docs" -SinVersion
$tieneHeader = $docs.Texto -match '"name":"X-API-Version"'
Registrar '8.1' 'Swagger UI y /v3/api-docs sin encabezado' '200 y contrato con X-API-Version' "swagger $($sw.Status), api-docs $($docs.Status), header documentado=$tieneHeader" ($sw.Status -eq 200 -and $docs.Status -eq 200 -and $tieneHeader) "$ApiUrl/swagger-ui.html"
try {
    $web = Invoke-Api -Url "$WebUrl/" -SinVersion
    $swWeb = Invoke-Api -Url "$WebUrl/v3/api-docs" -SinVersion
    Registrar '8.2' 'Frontend y OpenAPI vía puerto web' '200 index.html con #root; api-docs 200' "index $($web.Status), api-docs $($swWeb.Status)" ($web.Status -eq 200 -and $web.Texto -match 'id="root"' -and $swWeb.Status -eq 200) "$WebUrl/"
} catch {
    Registrar '8.2' 'Frontend vía puerto web' '200' "error: $($_.Exception.Message)" $false
}

# Resumen y evidencia
$fallas = @($resultados | Where-Object { $_.Resultado -ne 'OK' }).Count
$dir = Join-Path $raiz 'docs\evidencias'
New-Item -ItemType Directory -Force $dir | Out-Null
$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# Evidencia E2E - $marca")
[void]$md.AppendLine('')
[void]$md.AppendLine("- API: $ApiUrl  |  Frontend: $WebUrl  |  SKU de la corrida: ``$sku``  |  Reinicio probado: $([bool]$ProbarReinicio)")
[void]$md.AppendLine("- Resultado: $($resultados.Count - $fallas) de $($resultados.Count) comprobaciones OK")
[void]$md.AppendLine('')
if ($psSalida) { [void]$md.AppendLine('```text'); [void]$md.AppendLine($psSalida); [void]$md.AppendLine('```'); [void]$md.AppendLine('') }
[void]$md.AppendLine('| Id | Operación | Esperado | Observado | Resultado |')
[void]$md.AppendLine('|---|---|---|---|---|')
foreach ($r in $resultados) {
    $obs = ($r.Observado -replace '\|', '/') -replace "`r?`n", ' '
    [void]$md.AppendLine("| $($r.Id) | $($r.Operacion) | $($r.Esperado) | $obs | $($r.Resultado) |")
}
[void]$md.AppendLine('')
[void]$md.AppendLine('## Respuestas relevantes')
foreach ($r in $resultados | Where-Object { $_.Evidencia -and $_.Evidencia.TrimStart().StartsWith('{') }) {
    [void]$md.AppendLine('')
    [void]$md.AppendLine("### $($r.Id) $($r.Operacion)")
    [void]$md.AppendLine('```json')
    [void]$md.AppendLine(($r.Evidencia | ConvertFrom-Json | ConvertTo-Json -Depth 10))
    [void]$md.AppendLine('```')
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $dir "e2e-$marca.md"), $md.ToString(), $utf8)
[System.IO.File]::WriteAllText((Join-Path $dir "e2e-$marca.json"), ($resultados | ConvertTo-Json -Depth 5), $utf8)
[System.IO.File]::WriteAllText((Join-Path $dir 'e2e-ultimo.md'), $md.ToString(), $utf8)

Write-Host ''
Write-Host ("Resultado: {0} de {1} comprobaciones OK. Evidencia: docs\evidencias\e2e-{2}.md" -f ($resultados.Count - $fallas), $resultados.Count, $marca) -ForegroundColor $(if ($fallas -eq 0) { 'Green' } else { 'Red' })
$cliente.Dispose()
if ($fallas -gt 0) { exit 1 } else { exit 0 }

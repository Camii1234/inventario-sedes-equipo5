// Capturas de la pila de observabilidad (numeral 13) contra la aplicación levantada con Docker Compose.
// Usa el Chrome o Edge instalado (playwright-core no descarga navegadores).
//   cd scripts/ui ; npm ci ; node capturas-observabilidad.mjs
// Variables opcionales: GRAFANA_URL (http://localhost:3001), PROMETHEUS_URL (http://localhost:9090),
// API_URL (http://localhost:8080), BROWSER_PATH.
import { chromium } from 'playwright-core';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes } from 'node:crypto';

const GRAFANA = process.env.GRAFANA_URL || 'http://localhost:3001';
const PROM = process.env.PROMETHEUS_URL || 'http://localhost:9090';
const API = process.env.API_URL || 'http://localhost:8080';
const GRAFANA_USUARIO = process.env.GRAFANA_ADMIN_USER || 'admin';
const GRAFANA_CLAVE = process.env.GRAFANA_ADMIN_PASSWORD || 'admin';
const autorizacionGrafana = { Authorization: `Basic ${Buffer.from(`${GRAFANA_USUARIO}:${GRAFANA_CLAVE}`).toString('base64')}` };
const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const dirCapturas = join(raiz, 'docs', 'capturas', 'observabilidad');
const dirEvidencias = join(raiz, 'docs', 'evidencias');
mkdirSync(dirCapturas, { recursive: true });
mkdirSync(dirEvidencias, { recursive: true });

const candidatos = [
  process.env.BROWSER_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);
const navegador = candidatos.find((r) => existsSync(r));
if (!navegador) throw new Error('No se encontró Chrome/Edge. Defina BROWSER_PATH.');

const pasos = [];
function registrar(id, operacion, observado, ok, captura) {
  pasos.push({ id, operacion, observado, resultado: ok ? 'OK' : 'FALLA', captura });
  console.log(`${ok ? '[OK ]' : '[XX ]'} ${id} ${operacion} -> ${observado}`);
}

// 1. Petición con un trace id conocido (cabecera W3C traceparent) para abrir luego esa misma traza.
const traceId = randomBytes(16).toString('hex');
const respuesta = await fetch(`${API}/api/inventario?sedeId=1`, {
  headers: { 'X-API-Version': '1', traceparent: `00-${traceId}-${randomBytes(8).toString('hex')}-01` },
});
registrar('OBS-UI-0', 'GET /api/inventario?sedeId=1 con traceparent', `${respuesta.status}, trace ${traceId}`, respuesta.status === 200);
await new Promise((r) => setTimeout(r, 12000)); // Tempo y Loki reciben los datos en lotes de unos segundos.

const browser = await chromium.launch({ executablePath: navegador, headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, locale: 'es-CO' });

async function capturar(nombre, fullPage = true) {
  await page.screenshot({ path: join(dirCapturas, nombre), fullPage });
  return `docs/capturas/observabilidad/${nombre}`;
}

try {
  // Explore requiere un usuario con rol Editor o superior; el acceso anónimo solo permite ver tableros.
  // Se inicia sesión por la API para no pasar por la pantalla de cambio de la contraseña por defecto.
  const login = await page.request.post(`${GRAFANA}/login`, { data: { user: GRAFANA_USUARIO, password: GRAFANA_CLAVE } });
  if (!login.ok()) throw new Error(`No se pudo iniciar sesión en Grafana (${login.status()}).`);

  // 2. Tablero principal de Grafana (modo kiosco, sin barra lateral).
  await page.goto(`${GRAFANA}/d/inventario-api/?orgId=1&from=now-30m&to=now&kiosk`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(6000);
  // Grafana usa un contenedor con scroll propio: se agranda la ventana para capturar el tablero completo.
  const alto = await page.evaluate(() => {
    const el = document.querySelector('.react-grid-layout');
    return el ? Math.ceil(el.getBoundingClientRect().height) + 120 : 3200;
  });
  await page.setViewportSize({ width: 1600, height: Math.max(1000, alto) });
  await page.waitForTimeout(5000);
  const sinDatos = await page.locator('text=No data').count();
  const c1 = await capturar('01-grafana-tablero.png', false);
  registrar('OBS-UI-1', 'Grafana: tablero "Inventario por sede · Observabilidad"', `paneles sin datos: ${sinDatos}`, true, c1);
  await page.setViewportSize({ width: 1600, height: 1000 });

  // 3. Prometheus: objetivos y alertas.
  await page.goto(`${PROM}/targets`, { waitUntil: 'load' });
  await page.waitForTimeout(2500);
  const c2 = await capturar('02-prometheus-objetivos.png');
  const objetivos = await (await fetch(`${PROM}/api/v1/targets`)).json();
  const activos = objetivos.data.activeTargets;
  const arriba = activos.filter((t) => t.health === 'up').length;
  registrar('OBS-UI-2', 'Prometheus: objetivos de scraping', `${arriba}/${activos.length} arriba`, arriba === activos.length, c2);

  await page.goto(`${PROM}/alerts`, { waitUntil: 'load' });
  await page.waitForTimeout(2500);
  const c3 = await capturar('03-prometheus-reglas-alerta.png');
  const reglas = await (await fetch(`${PROM}/api/v1/rules`)).json();
  const nReglas = reglas.data.groups.reduce((n, g) => n + g.rules.length, 0);
  registrar('OBS-UI-3', 'Prometheus: reglas de alerta cargadas', `${nReglas} reglas`, nReglas >= 7, c3);

  // 4. Grafana Explore: logs del backend en Loki.
  const panelLoki = {
    datasource: 'loki',
    queries: [{ refId: 'A', expr: '{service_name="inventario-api"}', queryType: 'range', datasource: { type: 'loki', uid: 'loki' } }],
    range: { from: 'now-30m', to: 'now' },
  };
  await page.goto(`${GRAFANA}/explore?schemaVersion=1&panes=${encodeURIComponent(JSON.stringify({ a: panelLoki }))}&orgId=1`,
    { waitUntil: 'networkidle' });
  await page.waitForTimeout(6000);
  const c4 = await capturar('04-grafana-logs-loki.png', false);
  registrar('OBS-UI-4', 'Grafana Explore: logs del backend (Loki)', 'logs con trace_id y span_id', true, c4);

  // 5. Grafana Explore: la traza de la petición del paso 1 en Tempo.
  const panelTempo = {
    datasource: 'tempo',
    queries: [{ refId: 'A', queryType: 'traceql', query: traceId, datasource: { type: 'tempo', uid: 'tempo' } }],
    range: { from: 'now-30m', to: 'now' },
  };
  await page.goto(`${GRAFANA}/explore?schemaVersion=1&panes=${encodeURIComponent(JSON.stringify({ a: panelTempo }))}&orgId=1`,
    { waitUntil: 'networkidle' });
  await page.waitForTimeout(6000);
  const traza = await (await fetch(`${GRAFANA}/api/datasources/proxy/uid/tempo/api/traces/${traceId}`,
    { headers: autorizacionGrafana })).json().catch(() => null);
  const spans = traza?.batches?.flatMap((b) => (b.scopeSpans || b.instrumentationLibrarySpans || []).flatMap((s) => s.spans)) || [];
  const c5 = await capturar('05-grafana-traza-tempo.png', false);
  registrar('OBS-UI-5', 'Grafana Explore: traza de la petición en Tempo', `${spans.length} spans (${spans.map((s) => s.name).join(' | ')})`,
    spans.length >= 2, c5);
} finally {
  await browser.close();
}

const fallas = pasos.filter((p) => p.resultado !== 'OK').length;
const sufijo = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
const informe = { fecha: new Date().toISOString(), traceId, pasos, total: pasos.length, fallas };
writeFileSync(join(dirEvidencias, `observabilidad-ui-${sufijo}.json`), JSON.stringify(informe, null, 2));
writeFileSync(join(dirEvidencias, 'observabilidad-ui-ultimo.json'), JSON.stringify(informe, null, 2));
console.log(`\n${pasos.length - fallas}/${pasos.length} pasos OK`);
process.exit(fallas === 0 ? 0 : 1);

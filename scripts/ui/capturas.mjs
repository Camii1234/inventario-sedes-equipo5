// Recorrido de aceptación en el navegador contra la aplicación levantada con Docker Compose.
// Usa el Chrome o Edge instalado (playwright-core no descarga navegadores).
//   cd scripts/ui ; npm ci ; node capturas.mjs
// Variables opcionales: WEB_URL (http://localhost:3000), API_URL (http://localhost:8080), BROWSER_PATH.
import { chromium } from 'playwright-core';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const WEB = process.env.WEB_URL || 'http://localhost:3000';
const API = process.env.API_URL || 'http://localhost:8080';
const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const dirCapturas = join(raiz, 'docs', 'capturas');
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

const sufijo = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
const sku = `UI-${sufijo}`;
const pasos = [];
const peticionesApi = [];

function registrar(id, operacion, esperado, observado, ok, captura) {
  pasos.push({ id, operacion, esperado, observado, resultado: ok ? 'OK' : 'FALLA', captura });
  console.log(`${ok ? '[OK ]' : '[XX ]'} ${id} ${operacion} -> ${observado}`);
}

const browser = await chromium.launch({ executablePath: navegador, headless: true });
const page = await browser.newPage({ viewport: { width: 1366, height: 820 }, locale: 'es-CO' });
page.on('request', (r) => {
  if (r.url().includes('/api/')) peticionesApi.push({ metodo: r.method(), url: r.url(), version: r.headers()['x-api-version'] || null });
});

async function capturar(nombre) {
  const ruta = join(dirCapturas, nombre);
  await page.screenshot({ path: ruta, fullPage: true });
  return `docs/capturas/${nombre}`;
}

async function elegirSede(id) {
  await page.selectOption('#sede', String(id));
  await page.waitForFunction((sid) => {
    const t = document.querySelector('[data-testid="nombre-sede"]');
    const s = document.querySelector('#sede');
    return t && s && s.value === sid && !document.querySelector('[data-testid="cargando"]');
  }, String(id));
}

const filaStock = async (codigo) => {
  const fila = page.locator('[data-testid="tabla-inventario"] tbody tr', { hasText: codigo });
  if ((await fila.count()) === 0) return null;
  return Number((await fila.first().locator('td').nth(2).innerText()).trim());
};

try {
  // 1. Inventario sede 1
  await page.goto(WEB, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="tabla-inventario"]');
  const titulo1 = await page.locator('[data-testid="nombre-sede"]').innerText();
  const ace1 = await filaStock('ACE-001');
  registrar('UI-1', 'Abrir frontend, sede 1', 'tabla con nombre de sede, SKU, producto y stock', `${titulo1}; ACE-001=${ace1}`,
    titulo1.includes('Medellín') && ace1 === 35, await capturar('01-inventario-sede-1.png'));

  // 2. Inventario sede 2 (SKU compartido con stock distinto)
  await elegirSede(2);
  const ace2 = await filaStock('ACE-001');
  const lec2 = await filaStock('LEC-001');
  registrar('UI-2', 'Seleccionar sede 2', 'ACE-001 con stock distinto; sin productos de la sede 1', `ACE-001=${ace2}; LEC-001 presente=${lec2 !== null}`,
    ace2 === 12 && lec2 === null, await capturar('02-inventario-sede-2.png'));

  // 3. Sede vacía
  await elegirSede(3);
  const vacia = await page.locator('[data-testid="inventario-vacio"]').isVisible();
  registrar('UI-3', 'Seleccionar sede 3', 'estado visual de sede vacía', `mensaje de sede vacía visible=${vacia}`, vacia,
    await capturar('03-sede-vacia.png'));

  // 4. Registro exitoso en sede 1
  await elegirSede(1);
  await page.fill('#sku', sku.toLowerCase());
  await page.fill('#nombre', 'Arroz blanco de 1 kg');
  await page.fill('#stockInicial', '20');
  await page.click('button[type="submit"]');
  await page.waitForSelector('[data-testid="aviso-exito"]');
  await page.waitForSelector('[data-testid="tabla-inventario"]');
  const stockNuevo = await filaStock(sku);
  const aviso = await page.locator('[data-testid="aviso-exito"]').innerText();
  registrar('UI-4', `Registrar ${sku.toLowerCase()} con stock 20 en sede 1`, 'confirmación 201, Location e inventario actualizado',
    `${aviso.split('\n')[0]}; fila ${sku}=${stockNuevo}`, stockNuevo === 20 && aviso.includes('Location'),
    await capturar('04-registro-exitoso.png'));

  // 5. Detalle mediante enlace self y regreso mediante enlace inventario
  await page.click('[data-testid="aviso-exito"] button.boton-enlace');
  await page.waitForSelector('[data-testid="detalle-producto"]');
  const detalle = await page.locator('[data-testid="detalle-producto"]').innerText();
  registrar('UI-5', 'Abrir detalle (enlace self)', `detalle de ${sku} con stock 20`, detalle.split('\n').slice(0, 8).join(' | '),
    detalle.includes(sku) && detalle.includes('20'), await capturar('05-detalle-producto.png'));
  await page.click('text=Volver al inventario de la sede');
  await page.waitForSelector('[data-testid="tabla-inventario"]');
  const volver = await filaStock(sku);
  registrar('UI-6', 'Volver con el enlace inventario', 'inventario de la sede 1 con el producto', `fila ${sku}=${volver}`, volver === 20,
    await capturar('06-volver-inventario.png'));

  // 6. SKU duplicado
  await page.fill('#sku', ` ${sku} `);
  await page.fill('#nombre', 'Intento duplicado');
  await page.fill('#stockInicial', '99');
  await page.click('button[type="submit"]');
  await page.waitForSelector('[data-testid="aviso-error"]');
  const dup = await page.locator('[data-testid="aviso-error"]').innerText();
  const trasDup = await filaStock(sku);
  registrar('UI-7', 'Registrar el mismo SKU otra vez', 'mensaje comprensible de SKU duplicado; stock sigue en 20',
    `${dup.replace(/\s+/g, ' ')}; fila=${trasDup}`, dup.includes('409') && trasDup === 20, await capturar('07-sku-duplicado.png'));

  // 7. Stock inválido
  await page.fill('#sku', `${sku}-NEG`);
  await page.fill('#nombre', 'Producto con stock negativo');
  await page.fill('#stockInicial', '-5');
  await page.click('button[type="submit"]');
  await page.waitForSelector('.error-campo');
  const errCampo = await page.locator('.error-campo').first().innerText();
  registrar('UI-8', 'Registrar con stock -5', 'mensaje de stock inválido junto al campo', errCampo, errCampo.includes('stockInicial'),
    await capturar('08-stock-invalido.png'));

  // 8. Todas las peticiones del navegador enviaron X-API-Version: 1
  const sinVersion = peticionesApi.filter((p) => p.version !== '1');
  registrar('UI-9', 'Encabezado en peticiones del navegador (incluye enlaces seguidos)', 'todas con X-API-Version: 1',
    `${peticionesApi.length} peticiones, ${sinVersion.length} sin versión`, peticionesApi.length > 0 && sinVersion.length === 0);

  // 9. Swagger UI: ejecutar GET /api/inventario con X-API-Version
  await page.setViewportSize({ width: 1366, height: 1000 });
  await page.goto(`${API}/swagger-ui.html`, { waitUntil: 'networkidle' });
  const bloque = page.locator('#operations-Inventario-consultar');
  await bloque.locator('.opblock-summary').click();
  // springdoc tiene try-it-out-enabled=true: solo se pulsa si el botón aún dice "Try it out".
  const botonTry = bloque.locator('button.try-out__btn');
  if ((await botonTry.innerText()).trim().toLowerCase() === 'try it out') await botonTry.click();
  await bloque.locator('tr[data-param-name="sedeId"] input').fill('1');
  const version = await bloque.locator('tr[data-param-name="X-API-Version"] input').inputValue();
  await bloque.locator('button.execute').click();
  await bloque.locator('.live-responses-table tbody .response-col_status').first().waitFor();
  const estado = (await bloque.locator('.live-responses-table tbody .response-col_status').first().innerText()).trim();
  await bloque.scrollIntoViewIfNeeded();
  const ruta = join(dirCapturas, '09-swagger-get-inventario.png');
  await bloque.screenshot({ path: ruta });
  registrar('UI-10', 'Swagger UI: Try it out GET /api/inventario?sedeId=1', '200 con X-API-Version precargado en 1',
    `X-API-Version=${version}; status ${estado}`, estado === '200' && version === '1', 'docs/capturas/09-swagger-get-inventario.png');
} catch (e) {
  registrar('UI-ERR', 'Recorrido interrumpido', 'sin errores', String(e.message || e).split('\n')[0], false, await capturar('99-error.png'));
} finally {
  await browser.close();
}

const ok = pasos.filter((p) => p.resultado === 'OK').length;
const salida = { fecha: new Date().toISOString(), web: WEB, api: API, navegador, sku, resultado: `${ok}/${pasos.length}`, pasos, peticionesApi };
writeFileSync(join(dirEvidencias, `ui-e2e-${sufijo}.json`), JSON.stringify(salida, null, 2));
writeFileSync(join(dirEvidencias, 'ui-e2e-ultimo.json'), JSON.stringify(salida, null, 2));
console.log(`\nResultado UI: ${ok} de ${pasos.length} OK. Evidencia: docs/evidencias/ui-e2e-${sufijo}.json`);
process.exit(ok === pasos.length ? 0 : 1);


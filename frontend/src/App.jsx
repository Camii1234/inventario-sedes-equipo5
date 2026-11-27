import { useCallback, useEffect, useState } from 'react';
import { API_VERSION, listarAlmacenes, registrarProducto, seguirEnlace } from './api.js';

const FORMULARIO_VACIO = { sku: '', nombre: '', stockInicial: '' };

function mensajeDeError(error) {
  const p = error?.problema;
  if (error?.status === 409 && p?.productoExistente) {
    return `El SKU ${p.productoExistente.sku} ya existe en esta sede con stock ${p.productoExistente.stock}. No se modificó el inventario.`;
  }
  return p?.detail || error?.message || 'Ocurrió un error inesperado.';
}

function erroresPorCampo(error) {
  const lista = error?.problema?.errores || [];
  return lista.reduce((acc, e) => ({ ...acc, [e.campo]: acc[e.campo] ? `${acc[e.campo]} · ${e.mensaje}` : e.mensaje }), {});
}

function Aviso({ tipo, titulo, children, onCerrar }) {
  return (
    <div className={`aviso aviso-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'} data-testid={`aviso-${tipo}`}>
      <div>
        <strong>{titulo}</strong>
        {children && <div className="aviso-cuerpo">{children}</div>}
      </div>
      {onCerrar && (
        <button type="button" className="aviso-cerrar" onClick={onCerrar} aria-label="Cerrar aviso">×</button>
      )}
    </div>
  );
}

function Cargando({ texto }) {
  return (
    <div className="cargando" role="status" data-testid="cargando">
      <span className="spinner" aria-hidden="true" /> {texto}
    </div>
  );
}

function Enlaces({ links }) {
  if (!links) return null;
  return (
    <details className="enlaces">
      <summary>Enlaces HATEOAS ({Object.keys(links).length})</summary>
      <ul>
        {Object.entries(links).map(([rel, link]) => (
          <li key={rel}><code className="rel">{rel}</code> <code>{link.href}</code></li>
        ))}
      </ul>
    </details>
  );
}

function TablaInventario({ inventario, resaltadoId, onVerDetalle }) {
  if (inventario.productos.length === 0) {
    return (
      <div className="vacio" data-testid="inventario-vacio">
        <p><strong>Esta sede todavía no tiene productos.</strong></p>
        <p>Registre el primero con el formulario «Registrar producto».</p>
      </div>
    );
  }
  return (
    <table className="tabla" data-testid="tabla-inventario">
      <thead>
        <tr>
          <th>SKU</th>
          <th>Producto</th>
          <th className="num">Stock</th>
          <th aria-label="Acciones" />
        </tr>
      </thead>
      <tbody>
        {inventario.productos.map((p) => (
          <tr key={p.id} className={p.id === resaltadoId ? 'resaltado' : undefined}>
            <td><code>{p.sku}</code></td>
            <td>{p.nombre}</td>
            <td className="num">{p.stock}</td>
            <td className="acciones">
              <button type="button" className="boton-enlace" onClick={() => onVerDetalle(p)}>Ver detalle</button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function DetalleProducto({ producto, cargando, onVolver }) {
  return (
    <div className="detalle" data-testid="detalle-producto">
      <dl>
        <dt>ID</dt><dd>{producto.id}</dd>
        <dt>SKU</dt><dd><code>{producto.sku}</code></dd>
        <dt>Producto</dt><dd>{producto.nombre}</dd>
        <dt>Stock</dt><dd className="stock-grande">{producto.stock}</dd>
        <dt>Sede</dt><dd>{producto.sedeNombre} (ID {producto.sedeId})</dd>
        <dt>Registrado</dt><dd>{new Date(producto.creadoEn).toLocaleString('es-CO')}</dd>
      </dl>
      <p className="nota">Cargado siguiendo el enlace <code>self</code>: <code>{producto._links?.self?.href}</code></p>
      <button type="button" className="boton secundario" onClick={onVolver} disabled={cargando}>
        ← Volver al inventario de la sede
      </button>
      <Enlaces links={producto._links} />
    </div>
  );
}

export default function App() {
  const [almacenes, setAlmacenes] = useState([]);
  const [errorAlmacenes, setErrorAlmacenes] = useState(null);
  const [sedeId, setSedeId] = useState('');
  const [inventario, setInventario] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [errorConsulta, setErrorConsulta] = useState(null);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [enviando, setEnviando] = useState(false);
  const [registro, setRegistro] = useState(null);
  const [errorRegistro, setErrorRegistro] = useState(null);
  const [estadoCache, setEstadoCache] = useState(null);

  const sede = almacenes.find((a) => String(a.id) === String(sedeId));

  useEffect(() => {
    listarAlmacenes()
      .then(({ datos }) => {
        setAlmacenes(datos.almacenes);
        if (datos.almacenes.length > 0) setSedeId(String(datos.almacenes[0].id));
      })
      .catch(setErrorAlmacenes);
  }, []);

  /** Carga una representación siguiendo un enlace HAL y la muestra como inventario o como detalle. */
  const navegar = useCallback(async (recurso, rel, destino) => {
    setCargando(true);
    setErrorConsulta(null);
    try {
      const { datos, cache } = await seguirEnlace(recurso, rel);
      if (destino === 'detalle') {
        setDetalle(datos);
      } else {
        setDetalle(null);
        setInventario(datos);
        setEstadoCache(cache);
        setSedeId(String(datos.sede.id));
      }
    } catch (e) {
      setErrorConsulta(e);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    if (sede) navegar(sede, 'inventario', 'inventario');
  }, [sede, navegar]);

  function cambiarSede(e) {
    setSedeId(e.target.value);
    setInventario(null);
    setDetalle(null);
    setRegistro(null);
    setErrorRegistro(null);
  }

  function cambiarCampo(e) {
    setFormulario({ ...formulario, [e.target.name]: e.target.value });
  }

  async function enviar(e) {
    e.preventDefault();
    setEnviando(true);
    setRegistro(null);
    setErrorRegistro(null);
    const stockTexto = formulario.stockInicial.trim();
    const stock = stockTexto === '' ? null : Number(stockTexto);
    try {
      const respuesta = await registrarProducto({
        sedeId: Number(sedeId),
        sku: formulario.sku,
        nombre: formulario.nombre,
        stockInicial: Number.isNaN(stock) ? stockTexto : stock,
      });
      setRegistro({ producto: respuesta.datos, location: respuesta.location });
      setFormulario(FORMULARIO_VACIO);
      await navegar(respuesta.datos, 'inventario', 'inventario');
    } catch (err) {
      setErrorRegistro(err);
    } finally {
      setEnviando(false);
    }
  }

  const camposConError = erroresPorCampo(errorRegistro);

  return (
    <div className="app">
      <header className="cabecera">
        <div>
          <h1>Inventario por sede</h1>
          <p>Arquitectura de Software · Universidad de Antioquia · Equipo 5</p>
        </div>
        <span className="insignia" title="Encabezado enviado en todas las peticiones">X-API-Version: {API_VERSION}</span>
      </header>

      {errorAlmacenes && (
        <Aviso tipo="error" titulo="No se pudieron cargar las sedes">{mensajeDeError(errorAlmacenes)}</Aviso>
      )}

      <div className="selector">
        <label htmlFor="sede">Sede</label>
        <select id="sede" value={sedeId} onChange={cambiarSede} disabled={almacenes.length === 0}>
          {almacenes.length === 0 && <option value="">Cargando sedes…</option>}
          {almacenes.map((a) => (
            <option key={a.id} value={a.id}>{a.id} · {a.nombre} ({a.ciudad})</option>
          ))}
        </select>
      </div>

      <main className="rejilla">
        <section className="tarjeta" aria-labelledby="titulo-inventario">
          <div className="tarjeta-cabecera">
            <div>
              <h2 id="titulo-inventario">{detalle ? 'Detalle del producto' : 'Inventario'}</h2>
              {inventario && !detalle && (
                <p className="subtitulo" data-testid="nombre-sede">
                  {inventario.sede.nombre} · {inventario.sede.ciudad} · {inventario.totalProductos} productos ·{' '}
                  {inventario.totalUnidades} unidades
                </p>
              )}
            </div>
            {inventario && !detalle && (
              <button type="button" className="boton secundario" disabled={cargando}
                onClick={() => navegar(inventario, 'self', 'inventario')}>Actualizar</button>
            )}
          </div>

          {errorConsulta && (
            <Aviso tipo="error" titulo={`Error ${errorConsulta.status || ''} al consultar`} onCerrar={() => setErrorConsulta(null)}>
              {mensajeDeError(errorConsulta)}
            </Aviso>
          )}
          {cargando && <Cargando texto={detalle ? 'Cargando…' : 'Cargando inventario…'} />}

          {!cargando && detalle && (
            <DetalleProducto producto={detalle} cargando={cargando}
              onVolver={() => navegar(detalle, 'inventario', 'inventario')} />
          )}
          {!cargando && !detalle && inventario && (
            <>
              <TablaInventario inventario={inventario} resaltadoId={registro?.producto.id}
                onVerDetalle={(p) => navegar(p, 'self', 'detalle')} />
              {inventario.pagina && (
                <nav className="paginacion" aria-label="Paginación del inventario" data-testid="paginacion">
                  <button type="button" className="boton secundario" disabled={cargando || !inventario._links?.prev}
                    onClick={() => navegar(inventario, 'prev', 'inventario')}>← Anterior</button>
                  <span data-testid="pagina-actual">
                    Página {inventario.pagina.numero + 1} de {Math.max(inventario.pagina.totalPaginas, 1)} ·{' '}
                    {inventario.pagina.tamano} por página
                  </span>
                  <button type="button" className="boton secundario" disabled={cargando || !inventario._links?.next}
                    onClick={() => navegar(inventario, 'next', 'inventario')}>Siguiente →</button>
                  {estadoCache && (
                    <span className={`insignia cache-${estadoCache.toLowerCase()}`} data-testid="estado-cache"
                      title="Cabecera X-Cache: HIT = servido desde Redis, MISS = consultado en MySQL">
                      Caché: {estadoCache}
                    </span>
                  )}
                </nav>
              )}
              <Enlaces links={inventario._links} />
            </>
          )}
        </section>

        <section className="tarjeta" aria-labelledby="titulo-registro">
          <h2 id="titulo-registro">Registrar producto</h2>
          <p className="subtitulo">Se registrará en: <strong>{sede ? sede.nombre : '—'}</strong></p>

          {registro && (
            <Aviso tipo="exito" titulo="Producto registrado (201 Created)" onCerrar={() => setRegistro(null)}>
              <code>{registro.producto.sku}</code> · {registro.producto.nombre} · stock {registro.producto.stock}
              <div className="nota">Location: <code>{registro.location}</code></div>
              <button type="button" className="boton-enlace" onClick={() => navegar(registro.producto, 'self', 'detalle')}>
                Abrir detalle (enlace self)
              </button>
            </Aviso>
          )}
          {errorRegistro && (
            <Aviso tipo="error" titulo={errorRegistro.status === 409 ? 'SKU duplicado (409)' : `No se pudo registrar (${errorRegistro.status || 'sin conexión'})`}
              onCerrar={() => setErrorRegistro(null)}>
              {mensajeDeError(errorRegistro)}
            </Aviso>
          )}

          <form onSubmit={enviar} noValidate className="formulario">
            <label htmlFor="sku">SKU</label>
            <input id="sku" name="sku" value={formulario.sku} onChange={cambiarCampo} placeholder="ARR-001"
              maxLength={60} autoComplete="off" aria-invalid={Boolean(camposConError.sku)} />
            {camposConError.sku && <span className="error-campo">{camposConError.sku}</span>}

            <label htmlFor="nombre">Nombre del producto</label>
            <input id="nombre" name="nombre" value={formulario.nombre} onChange={cambiarCampo}
              placeholder="Arroz blanco de 1 kg" maxLength={150} aria-invalid={Boolean(camposConError.nombre)} />
            {camposConError.nombre && <span className="error-campo">{camposConError.nombre}</span>}

            <label htmlFor="stockInicial">Stock inicial</label>
            <input id="stockInicial" name="stockInicial" type="number" step="1" value={formulario.stockInicial}
              onChange={cambiarCampo} placeholder="20" aria-invalid={Boolean(camposConError.stockInicial)} />
            {camposConError.stockInicial && <span className="error-campo">{camposConError.stockInicial}</span>}

            <button type="submit" className="boton" disabled={enviando || !sede}>
              {enviando ? 'Registrando…' : 'Registrar producto'}
            </button>
          </form>
        </section>
      </main>

      <footer className="pie">
        API: <a href="/swagger-ui.html" target="_blank" rel="noreferrer">Swagger UI</a> ·{' '}
        <a href="/v3/api-docs" target="_blank" rel="noreferrer">Contrato OpenAPI</a>
      </footer>
    </div>
  );
}

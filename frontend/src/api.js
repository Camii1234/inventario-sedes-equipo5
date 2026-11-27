// Cliente de la API. Todas las peticiones, incluidas las que siguen enlaces HATEOAS,
// envían el encabezado de versión X-API-Version.
export const API_VERSION = '1';

export class ApiError extends Error {
  constructor(status, problema) {
    super(problema?.detail || `Error HTTP ${status}`);
    this.status = status;
    this.problema = problema;
  }
}

async function solicitar(url, opciones = {}) {
  let respuesta;
  try {
    respuesta = await fetch(url, {
      ...opciones,
      headers: {
        'X-API-Version': API_VERSION,
        Accept: 'application/hal+json, application/problem+json',
        ...(opciones.headers || {}),
      },
    });
  } catch {
    throw new ApiError(0, { detail: 'No fue posible conectar con la API. Verifique que el backend esté en ejecución.' });
  }
  const texto = await respuesta.text();
  let datos = null;
  if (texto) {
    try {
      datos = JSON.parse(texto);
    } catch {
      datos = { detail: texto };
    }
  }
  if (!respuesta.ok) throw new ApiError(respuesta.status, datos);
  return { datos, status: respuesta.status, location: respuesta.headers.get('Location') };
}

export const listarAlmacenes = () => solicitar('/api/almacenes');

export const registrarProducto = (producto) =>
  solicitar('/api/productos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(producto),
  });

/** Sigue el enlace `rel` de una representación HAL usando su href tal como lo devolvió la API. */
export function seguirEnlace(recurso, rel) {
  const href = recurso?._links?.[rel]?.href;
  if (!href) return Promise.reject(new ApiError(0, { detail: `La respuesta no incluye el enlace "${rel}".` }));
  return solicitar(href);
}

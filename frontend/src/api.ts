export interface Destino {
  id: string
  nombre: string
  region: string
  pais: string
  latitud: number
  longitud: number
}

export interface FechaHora {
  llegada: string
  salida: string
  duracionDias: number
}

function esDestino(valor: unknown): valor is Destino {
  if (typeof valor !== 'object' || valor === null) return false
  const destino = valor as Record<string, unknown>
  return typeof destino.id === 'string' && typeof destino.nombre === 'string'
    && typeof destino.region === 'string' && typeof destino.pais === 'string'
    && typeof destino.latitud === 'number' && typeof destino.longitud === 'number'
}

export async function buscarDestinos(texto: string, signal: AbortSignal): Promise<Destino[]> {
  const parametros = new URLSearchParams({ texto })
  const respuesta = await fetch(`/api/destinos?${parametros}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { Accept: 'application/json' },
  })
  const datos: unknown = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    const mensaje = typeof datos === 'object' && datos !== null && 'mensaje' in datos
      && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se ha podido buscar el destino.'
    throw new Error(mensaje)
  }
  if (!Array.isArray(datos) || !datos.every(esDestino)) {
    throw new Error('El servidor ha devuelto una respuesta no válida.')
  }
  return datos
}

export async function validarFechaHora(
  llegada: string | null,
  salida: string | null,
  signal?: AbortSignal
): Promise<FechaHora> {

  const respuesta = await fetch('/api/itinerarios/validar-fecha-hora', {
    method: 'POST',
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(10_000)])
      : AbortSignal.timeout(10_000),
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      llegada,
      salida,
    }),
  })

  const datos: unknown = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    const mensaje = typeof datos === 'object' && datos !== null && 'mensaje' in datos
      && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se han podido validar las fechas y horas. Comprueba que el backend esté arrancado.'
    throw new Error(mensaje)
  }

  if (typeof datos !== 'object' || datos === null
    || !('llegada' in datos) || typeof datos.llegada !== 'string'
    || !('salida' in datos) || typeof datos.salida !== 'string'
    || !('duracionDias' in datos) || typeof datos.duracionDias !== 'number') {
    throw new Error('El servidor ha devuelto una respuesta no válida.')
  }

  return {
    llegada: datos.llegada,
    salida: datos.salida,
    duracionDias: datos.duracionDias,
  }
}

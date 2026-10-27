export interface Destino {
  id: string
  nombre: string
  region: string
  pais: string
  latitud: number
  longitud: number
}

export interface FechasItinerario {
  fechaInicio: string
  fechaFin: string
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

export async function validarFechas(
  fechaInicio: string,
  fechaFin: string,
  signal?: AbortSignal
): Promise<FechasItinerario> {

  const respuesta = await fetch('/api/itinerarios/validar-fechas', {
    method: 'POST',
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(10_000)])
      : AbortSignal.timeout(10_000),
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      fechaInicio: fechaInicio || null,
      fechaFin: fechaFin || null,
    }),
  })

  const datos: unknown = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    const mensaje = typeof datos === 'object' && datos !== null && 'mensaje' in datos
      && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se han podido validar las fechas. Comprueba que el backend esté arrancado.'
    throw new Error(mensaje)
  }

  if (typeof datos !== 'object' || datos === null
    || !('fechaInicio' in datos) || typeof datos.fechaInicio !== 'string'
    || !('fechaFin' in datos) || typeof datos.fechaFin !== 'string'
    || !('duracionDias' in datos) || typeof datos.duracionDias !== 'number') {
    throw new Error('El servidor ha devuelto una respuesta no válida.')
  }

  return {
    fechaInicio: datos.fechaInicio,
    fechaFin: datos.fechaFin,
    duracionDias: datos.duracionDias,
  }
}

import type { Destino } from '../types/destino'

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

import { useEffect, useState } from 'react'
import type { DiaItinerario } from './api'
import { peticionApi } from './sesion'

export type OrigenTrazados = { tipo: 'generacion'; id: string }
  | { tipo: 'viaje'; id: string; revision: number }
  | { tipo: 'edicion'; id: string; revision: number; propuestaId: string }
export interface TramoMapa {
  clave: string
  origenId: string
  destinoId: string
  estado: 'DISPONIBLE' | 'NO_DISPONIBLE' | 'CUOTA_AGOTADA' | 'COORDENADAS_AUSENTES' | 'MISMO_PUNTO'
  geometria: { coordenadas: [number, number][]; fuente: string } | null
}
function objeto(x: unknown): x is Record<string, unknown> { return typeof x === 'object' && x !== null }
function esTramo(x: unknown): x is TramoMapa {
  if (!objeto(x) || typeof x.clave !== 'string' || typeof x.origenId !== 'string' || typeof x.destinoId !== 'string'
    || !['DISPONIBLE', 'NO_DISPONIBLE', 'CUOTA_AGOTADA', 'COORDENADAS_AUSENTES', 'MISMO_PUNTO'].includes(String(x.estado))) return false
  if (x.estado !== 'DISPONIBLE') return x.geometria === null
  return objeto(x.geometria) && x.geometria.fuente === 'openrouteservice' && Array.isArray(x.geometria.coordenadas)
    && x.geometria.coordenadas.length >= 2 && x.geometria.coordenadas.length <= 20000
    && x.geometria.coordenadas.every(p => Array.isArray(p) && p.length === 2 && p.every(n => typeof n === 'number' && Number.isFinite(n))
      && Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90)
}
async function solicitar(origen: OrigenTrazados, dia: string, signal: AbortSignal): Promise<{ tramos: TramoMapa[]; reintentarDesde: string | null }> {
  const id = encodeURIComponent(origen.id)
  const ruta = origen.tipo === 'generacion' ? `/api/itinerarios/${id}/trazados`
    : origen.tipo === 'viaje' ? `/api/viajes/${id}/trazados`
    : `/api/viajes/${id}/edicion/${encodeURIComponent(origen.propuestaId)}/trazados`
  const respuesta = await peticionApi(ruta, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ dia, ...(origen.tipo === 'generacion' ? {} : { revision: origen.revision }) }),
    signal: AbortSignal.any([signal, AbortSignal.timeout(25000)]),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new Error('No se ha podido cargar el recorrido. La lista del itinerario sigue disponible.')
  if (!objeto(datos) || datos.dia !== dia || !Array.isArray(datos.tramos) || !datos.tramos.every(esTramo))
    throw new Error('El recorrido recibido no es válido. Puedes consultar el plan desde la lista.')
  const reintentarDesde = datos.reintentarDesde ?? null
  if (reintentarDesde !== null && (typeof reintentarDesde !== 'string' || !Number.isFinite(Date.parse(reintentarDesde))))
    throw new Error('La fecha de reintento recibida no es válida.')
  if (datos.tramos.some(t => t.estado === 'CUOTA_AGOTADA') && reintentarDesde === null)
    throw new Error('Falta la fecha de renovación de la cuota.')
  return { tramos: datos.tramos, reintentarDesde }
}

export function useTrazados(origen: OrigenTrazados | undefined, dia: DiaItinerario, activo: boolean) {
  // La versión, el día y sus bloques atan cada respuesta al plan que la solicitó.
  // Una edición retira las líneas anteriores ya en el primer render, antes del efecto.
  const clave = JSON.stringify([origen, dia])
  const [intento, setIntento] = useState(0)
  const [estado, setEstado] = useState<{ clave: string; tramos: TramoMapa[]; cargando: boolean; error: string; reintentarDesde: string | null } | null>(null)
  useEffect(() => {
    if (!activo || !origen) return
    const controlador = new AbortController()
    setEstado({ clave, tramos: [], cargando: true, error: '', reintentarDesde: null })
    solicitar(origen, dia.fecha, controlador.signal).then(datos => {
      if (!controlador.signal.aborted) setEstado({ clave, ...datos, cargando: false, error: '' })
    }).catch(() => {
      if (!controlador.signal.aborted) setEstado({ clave, tramos: [], cargando: false,
        error: 'No se ha podido cargar el recorrido. La lista sigue disponible.', reintentarDesde: null })
    })
    return () => controlador.abort()
  // El valor serializado incluye todos los datos de origen y día usados por la petición.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, activo, intento])
  const actual = estado?.clave === clave ? estado : null
  const [, actualizarReloj] = useState(0)
  const reintentarDesde = actual?.reintentarDesde ?? null
  useEffect(() => {
    if (!reintentarDesde) return
    const restante = Date.parse(reintentarDesde) - Date.now()
    if (restante <= 0) return
    const temporizador = window.setTimeout(() => actualizarReloj(v => v + 1), Math.min(restante + 50, 2147483647))
    return () => window.clearTimeout(temporizador)
  }, [reintentarDesde])
  const esperandoCuota = reintentarDesde !== null && Date.parse(reintentarDesde) > Date.now()
  return { reintentarDesde, esperandoCuota, tramos: actual?.tramos ?? [], cargando: actual?.cargando ?? false,
    error: actual?.error ?? '', reintentar: () => { if (!esperandoCuota) setIntento(v => v + 1) } }
}

import { esCandidatoEvaluado, esItinerarioGenerado } from './api'
import type { CandidatoEvaluado, ItinerarioGenerado } from './api'
import { peticionApi } from './sesion'
import type { ViajeActualizado } from './viajes'

export interface IncidenciaEdicion {
  nivel: 'AVISO' | 'CONFLICTO'
  codigo: string
  dia: string | null
  actividadId: string | null
  lugarId: string | null
  mensaje: string
}
export interface VisitaEditable { actividadId: string; candidato: CandidatoEvaluado; obligatorio: boolean }
export interface DiaEditable { fecha: string; visitas: VisitaEditable[] }
export interface EstadoEdicion {
  propuesta: { propuestaId: string; revisionBase: number; itinerario: ItinerarioGenerado; incidencias: IncidenciaEdicion[]; puedeGuardar: boolean }
  dias: DiaEditable[]
  eliminadas: { dia: string; visita: VisitaEditable }[]
  modificada: boolean
}
export interface AlternativasEdicion { candidatos: CandidatoEvaluado[]; mensaje: string }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const fecha = /^\d{4}-\d{2}-\d{2}$/
function objeto(x: unknown): x is Record<string, unknown> { return typeof x === 'object' && x !== null }
function id(x: unknown): x is string { return typeof x === 'string' && uuid.test(x) }
function esIncidencia(x: unknown): x is IncidenciaEdicion {
  return objeto(x) && (x.nivel === 'AVISO' || x.nivel === 'CONFLICTO') && typeof x.codigo === 'string'
    && (x.dia === null || typeof x.dia === 'string' && fecha.test(x.dia))
    && (x.actividadId === null || id(x.actividadId)) && (x.lugarId === null || typeof x.lugarId === 'string') && typeof x.mensaje === 'string'
}
function esEstado(x: unknown): x is EstadoEdicion {
  if (!objeto(x) || !objeto(x.propuesta) || !id(x.propuesta.propuestaId)
    || typeof x.propuesta.revisionBase !== 'number' || !Number.isSafeInteger(x.propuesta.revisionBase) || x.propuesta.revisionBase < 1
    || !esItinerarioGenerado(x.propuesta.itinerario) || !Array.isArray(x.propuesta.incidencias)
    || !x.propuesta.incidencias.every(esIncidencia) || typeof x.propuesta.puedeGuardar !== 'boolean'
    || x.propuesta.puedeGuardar !== x.propuesta.incidencias.every(i => i.nivel !== 'CONFLICTO')
    || typeof x.modificada !== 'boolean' || !Array.isArray(x.dias) || !Array.isArray(x.eliminadas)
    || !x.eliminadas.every(e => objeto(e) && typeof e.dia === 'string' && fecha.test(e.dia) && objeto(e.visita)
      && id(e.visita.actividadId) && esCandidatoEvaluado(e.visita.candidato) && typeof e.visita.obligatorio === 'boolean')) return false
  return x.dias.every(d => objeto(d) && typeof d.fecha === 'string' && fecha.test(d.fecha) && Array.isArray(d.visitas)
    && d.visitas.every(v => objeto(v) && id(v.actividadId) && esCandidatoEvaluado(v.candidato) && typeof v.obligatorio === 'boolean'))
}
async function enviar(viaje: string, ruta: string, cuerpo: unknown, signal: AbortSignal): Promise<unknown> {
  const respuesta = await peticionApi('/api/viajes/' + encodeURIComponent(viaje) + '/edicion/' + ruta, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(cuerpo), signal: AbortSignal.any([signal, AbortSignal.timeout(180000)]),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new Error(objeto(datos) && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se pudo completar la edición. El viaje guardado se conserva.')
  return datos
}
function leerEstado(datos: unknown): EstadoEdicion {
  if (!esEstado(datos)) throw new Error('La propuesta recibida no es válida. El viaje guardado se conserva.')
  return datos
}
function visitaRuta(estado: EstadoEdicion, actividad: string, accion: string) {
  return encodeURIComponent(estado.propuesta.propuestaId) + '/visitas/' + encodeURIComponent(actividad) + '/' + accion
}
export async function iniciarEdicion(viaje: string, revision: number, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, 'iniciar', { revision }, signal))
}
export async function eliminarVisita(viaje: string, estado: EstadoEdicion, actividad: string, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, visitaRuta(estado, actividad, 'eliminar'), { revision: estado.propuesta.revisionBase }, signal))
}
export async function reordenarVisitas(viaje: string, estado: EstadoEdicion, dia: string, actividades: string[], signal: AbortSignal) {
  return leerEstado(await enviar(viaje, encodeURIComponent(estado.propuesta.propuestaId) + '/reordenar',
    { revision: estado.propuesta.revisionBase, dia, actividades }, signal))
}
export async function obtenerAlternativas(viaje: string, estado: EstadoEdicion, actividad: string, signal: AbortSignal): Promise<AlternativasEdicion> {
  const datos = await enviar(viaje, visitaRuta(estado, actividad, 'alternativas'), { revision: estado.propuesta.revisionBase }, signal)
  if (!objeto(datos) || !Array.isArray(datos.candidatos) || !datos.candidatos.every(esCandidatoEvaluado) || typeof datos.mensaje !== 'string')
    throw new Error('No se pudieron leer las alternativas. La visita original se conserva.')
  return { candidatos: datos.candidatos, mensaje: datos.mensaje }
}
export async function sustituirVisita(viaje: string, estado: EstadoEdicion, actividad: string, candidatoId: string, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, visitaRuta(estado, actividad, 'sustituir'), { revision: estado.propuesta.revisionBase, candidatoId }, signal))
}
export async function recalcularEdicion(viaje: string, estado: EstadoEdicion, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, encodeURIComponent(estado.propuesta.propuestaId) + '/recalcular', { revision: estado.propuesta.revisionBase }, signal))
}
export async function guardarEdicion(viaje: string, estado: EstadoEdicion, aceptarAvisos: boolean, signal: AbortSignal): Promise<ViajeActualizado> {
  let datos: unknown
  try {
    datos = await enviar(viaje, encodeURIComponent(estado.propuesta.propuestaId) + '/guardar', { revision: estado.propuesta.revisionBase, aceptarAvisos }, signal)
  } catch (error) {
    if (error instanceof TypeError || error instanceof DOMException)
      throw new Error('No hemos podido confirmar el guardado. Tus cambios siguen aquí. Comprueba Mis viajes antes de reintentar.')
    throw error
  }
  if (!objeto(datos) || !id(datos.id) || typeof datos.revision !== 'number' || !Number.isSafeInteger(datos.revision)
    || datos.id !== viaje || datos.revision !== estado.propuesta.revisionBase + 1)
    throw new Error('No se pudo confirmar el guardado. Comprueba Mis viajes antes de volver a intentarlo.')
  return { id: datos.id, revision: datos.revision }
}

export async function recuperarEdicion(viaje: string, revision: number, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, 'recuperar', { revision }, signal))
}

export async function restaurarVisita(viaje: string, estado: EstadoEdicion, actividad: string, signal: AbortSignal) {
  return leerEstado(await enviar(viaje, visitaRuta(estado, actividad, 'restaurar'), { revision: estado.propuesta.revisionBase }, signal))
}

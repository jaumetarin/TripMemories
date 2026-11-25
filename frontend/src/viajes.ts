import { esConfiguracionItinerario, esItinerarioGenerado } from './api'
import type { ConfiguracionItinerario, ItinerarioGenerado } from './api'
import { peticionApi } from './sesion'

export interface ResumenViaje {
  id: string
  titulo: string
  destino: string
  fechaInicio: string | null
  fechaFin: string | null
  actualizadoEn: string
  revision: number
}
export interface ViajeGuardado {
  resumen: ResumenViaje
  configuracion: ConfiguracionItinerario
  itinerario: ItinerarioGenerado
  actividades: { id: string; dia: string; orden: number }[]
}
export interface ViajeActualizado { id: string; revision: number }
export interface DestinoGuardado { id: string; revision: number; titulo: string }
export class ErrorViaje extends Error {
  readonly codigo: string
  readonly estado: number
  constructor(mensaje: string, codigo: string, estado: number) { super(mensaje); this.codigo = codigo; this.estado = estado }
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const fecha = /^\d{4}-\d{2}-\d{2}$/
function objeto(valor: unknown): valor is Record<string, unknown> { return typeof valor === 'object' && valor !== null }
function identificador(valor: unknown): valor is string { return typeof valor === 'string' && uuid.test(valor) }
function revision(valor: unknown): valor is number { return typeof valor === 'number' && Number.isSafeInteger(valor) && valor > 0 }
function esResumen(valor: unknown): valor is ResumenViaje {
  return objeto(valor) && identificador(valor.id) && typeof valor.titulo === 'string' && typeof valor.destino === 'string'
    && (valor.fechaInicio === null || typeof valor.fechaInicio === 'string' && fecha.test(valor.fechaInicio))
    && (valor.fechaFin === null || typeof valor.fechaFin === 'string' && fecha.test(valor.fechaFin))
    && typeof valor.actualizadoEn === 'string' && Number.isFinite(Date.parse(valor.actualizadoEn)) && revision(valor.revision)
}
function esViaje(valor: unknown): valor is ViajeGuardado {
  if (!objeto(valor) || !esResumen(valor.resumen) || !esConfiguracionItinerario(valor.configuracion)
    || !esItinerarioGenerado(valor.itinerario) || !Array.isArray(valor.actividades)) return false
  return valor.actividades.every(a => objeto(a) && identificador(a.id) && typeof a.dia === 'string'
    && fecha.test(a.dia) && typeof a.orden === 'number' && Number.isInteger(a.orden) && a.orden > 0)
    && valor.actividades.length === valor.itinerario.dias.reduce((total, dia) => total + dia.bloques.length, 0)
}
async function solicitar(ruta: string, signal: AbortSignal, cuerpo?: unknown, metodo = 'POST'): Promise<unknown> {
  const respuesta = await peticionApi('/api/viajes' + ruta, {
    method: cuerpo === undefined ? 'GET' : metodo,
    headers: { Accept: 'application/json', ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    signal: AbortSignal.any([signal, AbortSignal.timeout(20000)]),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new ErrorViaje(objeto(datos) && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se ha podido completar la operación del viaje.',
    objeto(datos) && typeof datos.codigo === 'string' ? datos.codigo : 'ERROR_VIAJE', respuesta.status)
  return datos
}
export async function listarViajes(signal: AbortSignal): Promise<ResumenViaje[]> {
  const datos = await solicitar('', signal)
  if (!Array.isArray(datos) || !datos.every(esResumen)) throw new Error('El listado de viajes recibido no es válido.')
  return datos
}
export async function obtenerViaje(id: string, signal: AbortSignal): Promise<ViajeGuardado> {
  const datos = await solicitar('/' + encodeURIComponent(id), signal)
  if (!esViaje(datos)) throw new Error('El viaje recibido no es válido. No se han modificado tus datos guardados.')
  return datos
}
export async function guardarViaje(generacionId: string, titulo: string, signal: AbortSignal): Promise<{ id: string }> {
  const datos = await solicitar('/desde-generacion/' + encodeURIComponent(generacionId), signal, { titulo })
  if (!objeto(datos) || !identificador(datos.id)) throw new Error('No se ha podido confirmar el guardado. Comprueba Mis viajes antes de reintentar.')
  return { id: datos.id }
}
export async function actualizarViaje(generacionId: string, viaje: DestinoGuardado, titulo: string, signal: AbortSignal): Promise<ViajeActualizado> {
  return leerActualizacion(await solicitar('/' + encodeURIComponent(viaje.id) + '/desde-generacion/' + encodeURIComponent(generacionId), signal,
    { titulo, revision: viaje.revision }))
}
export async function actualizarTitulo(viaje: ResumenViaje, titulo: string, signal: AbortSignal): Promise<ViajeActualizado> {
  return leerActualizacion(await solicitar('/' + encodeURIComponent(viaje.id), signal, { titulo, revision: viaje.revision }, 'PUT'))
}
function leerActualizacion(datos: unknown): ViajeActualizado {
  if (!objeto(datos) || !identificador(datos.id) || !revision(datos.revision))
    throw new Error('No se ha podido confirmar la actualización. Consulta Mis viajes antes de reintentar.')
  return { id: datos.id, revision: datos.revision }
}

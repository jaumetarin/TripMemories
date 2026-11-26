import { peticionApi } from './sesion'

export interface Album {
  id: string; viajeId: string; titulo: string; viajeTitulo: string; destino: string
  fechaInicio: string | null; fechaFin: string | null; fotos: number; portadaId: string | null; viajeEliminado: boolean; eliminando: boolean
}
export interface Foto {
  id: string; nombre: string; mime: string; bytesOriginal: number; bytesMiniatura: number
  ancho: number; alto: number; estado: 'PENDIENTE' | 'DISPONIBLE' | 'ERROR' | 'ELIMINANDO'; subidaEn: string
}
export interface ContenidoAlbum {
  album: Album; fotos: Foto[]; bytesUsados: number
  limites: { formatos: string[]; maxArchivoBytes: number; maxFotosAlbum: number; cuotaBytes: number; maxPixeles: number }
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function objeto(x: unknown): x is Record<string, unknown> { return typeof x === 'object' && x !== null }
function esId(x: unknown): x is string { return typeof x === 'string' && uuid.test(x) }
function numero(x: unknown): x is number { return typeof x === 'number' && Number.isSafeInteger(x) && x >= 0 }
function esAlbum(x: unknown): x is Album {
  return objeto(x) && esId(x.id) && esId(x.viajeId) && typeof x.titulo === 'string' && typeof x.viajeTitulo === 'string'
    && typeof x.destino === 'string' && numero(x.fotos) && (x.portadaId === null || esId(x.portadaId))
    && typeof x.viajeEliminado === 'boolean' && typeof x.eliminando === 'boolean'
    && (x.fechaInicio === null || typeof x.fechaInicio === 'string') && (x.fechaFin === null || typeof x.fechaFin === 'string')
}
function esFoto(x: unknown): x is Foto {
  return objeto(x) && esId(x.id) && typeof x.nombre === 'string' && ['image/jpeg','image/png'].includes(String(x.mime))
    && numero(x.bytesOriginal) && numero(x.bytesMiniatura) && numero(x.ancho) && numero(x.alto)
    && ['PENDIENTE','DISPONIBLE','ERROR','ELIMINANDO'].includes(String(x.estado)) && typeof x.subidaEn === 'string'
}
async function leer(respuesta: Response): Promise<unknown> {
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new Error(objeto(datos) && typeof datos.mensaje === 'string' ? datos.mensaje
    : 'No hemos podido confirmar la operación. Actualiza el álbum antes de reintentar.')
  return datos
}
export const urlMiniatura = (id: string) => `/api/fotos/${encodeURIComponent(id)}/miniatura`
export const urlOriginal = (id: string) => `/api/fotos/${encodeURIComponent(id)}/contenido`
export function megabytes(bytes: number) { return new Intl.NumberFormat('es-ES',{ maximumFractionDigits: 1 }).format(bytes / 1048576) + ' MB' }
export async function listarAlbumes(signal: AbortSignal): Promise<Album[]> {
  const datos = await leer(await peticionApi('/api/albumes',{ signal }))
  if (!Array.isArray(datos) || !datos.every(esAlbum)) throw new Error('El listado de álbumes recibido no es válido.')
  return datos
}
export async function abrirAlbum(id: string, signal: AbortSignal): Promise<ContenidoAlbum> {
  const datos = await leer(await peticionApi(`/api/albumes/${encodeURIComponent(id)}`,{ signal }))
  if (!objeto(datos) || !esAlbum(datos.album) || !Array.isArray(datos.fotos) || !datos.fotos.every(esFoto)
    || !numero(datos.bytesUsados) || !objeto(datos.limites) || !Array.isArray(datos.limites.formatos)
    || !datos.limites.formatos.every(f => typeof f === 'string') || !numero(datos.limites.maxArchivoBytes)
    || !numero(datos.limites.maxFotosAlbum) || !numero(datos.limites.cuotaBytes) || !numero(datos.limites.maxPixeles))
    throw new Error('El álbum recibido no es válido.')
  return datos as unknown as ContenidoAlbum
}
export async function subirFoto(albumId: string, foto: File, signal: AbortSignal): Promise<Foto> {
  const cuerpo = new FormData(); cuerpo.append('foto',foto)
  const datos = await leer(await peticionApi(`/api/albumes/${encodeURIComponent(albumId)}/fotos`,{ method:'POST', body:cuerpo, signal }))
  if (!esFoto(datos) || datos.estado !== 'DISPONIBLE') throw new Error('La subida no está confirmada. Actualiza el álbum para comprobar su estado.')
  return datos
}
export async function eliminarAlbum(id: string, signal: AbortSignal): Promise<void> {
  const respuesta = await peticionApi(`/api/albumes/${encodeURIComponent(id)}`,{ method:'DELETE', signal })
  if (!respuesta.ok) await leer(respuesta)
}
export async function eliminarFoto(id: string, signal: AbortSignal): Promise<void> {
  const respuesta = await peticionApi(`/api/fotos/${encodeURIComponent(id)}`,{ method:'DELETE', signal })
  if (!respuesta.ok) await leer(respuesta)
}
export async function descargarFoto(foto: Foto, signal: AbortSignal): Promise<void> {
  const respuesta = await peticionApi(`/api/fotos/${encodeURIComponent(foto.id)}/descargar`,{ signal })
  if (!respuesta.ok) { await leer(respuesta); return }
  const archivo = await respuesta.blob()
  if (archivo.size !== foto.bytesOriginal || archivo.type !== foto.mime) throw new Error('No se ha recibido la foto completa. Reintenta la descarga.')
  if (signal.aborted) return
  const enlace = document.createElement('a'), url = URL.createObjectURL(archivo)
  enlace.href=url; enlace.download=foto.nombre; enlace.click()
  setTimeout(() => URL.revokeObjectURL(url),1000)
}

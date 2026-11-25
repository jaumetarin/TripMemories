export interface UsuarioCuenta { id: string; nombre: string; correo: string }

export class ErrorCuenta extends Error {
  readonly campos: Record<string, string>
  constructor(mensaje: string, campos: Record<string, string> = {}) { super(mensaje); this.campos = campos }
}

let csrfPendiente: Promise<{ token: string; cabecera: string }> | null = null
export function olvidarCsrf() { csrfPendiente = null }

async function obtenerCsrf() {
  if (!csrfPendiente) csrfPendiente = fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) })
    .then(async respuesta => {
      const datos = await respuesta.json()
      if (!respuesta.ok || typeof datos?.token !== 'string' || datos.cabecera !== 'X-CSRF-TOKEN') throw new Error('No se ha podido preparar la sesión de seguridad. Vuelve a intentarlo.')
      return { token: datos.token as string, cabecera: datos.cabecera as string }
    }).catch(error => { csrfPendiente = null; throw error })
  return csrfPendiente
}

/** Todas las API usan la cookie del servidor y CSRF en las operaciones que modifican datos. */
export async function peticionApi(ruta: string, opciones: RequestInit = {}): Promise<Response> {
  const modifica = !['GET', 'HEAD', 'OPTIONS'].includes((opciones.method ?? 'GET').toUpperCase())
  const ejecutar = async () => {
    const cabeceras = new Headers(opciones.headers)
    if (modifica) { const csrf = await obtenerCsrf(); cabeceras.set(csrf.cabecera, csrf.token) }
    return fetch(ruta, { ...opciones, headers: cabeceras, credentials: 'same-origin', cache: 'no-store' })
  }
  let respuesta = await ejecutar()
  if (modifica && respuesta.status === 403) {
    const error = await respuesta.clone().json().catch(() => null)
    if (error?.codigo === 'CSRF_INVALIDO') {
      // Este código solo se emite antes de ejecutar el controlador: se puede renovar y repetir una vez.
      olvidarCsrf()
      respuesta = await ejecutar()
    }
  }
  if (respuesta.status === 401 && !ruta.startsWith('/api/auth/')) {
    olvidarCsrf()
    window.dispatchEvent(new Event('sesion-requerida'))
  }
  return respuesta
}

async function datosCuenta(ruta: string, cuerpo?: unknown) {
  const respuesta = await peticionApi('/api/auth/' + ruta, {
    method: cuerpo === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo), signal: AbortSignal.timeout(20000),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    const objeto = typeof datos === 'object' && datos !== null ? datos as Record<string, unknown> : {}
    const campos = typeof objeto.errores === 'object' && objeto.errores !== null
      ? Object.fromEntries(Object.entries(objeto.errores).filter((entrada): entrada is [string, string] => typeof entrada[1] === 'string')) : {}
    throw new ErrorCuenta(typeof objeto.mensaje === 'string' ? objeto.mensaje : 'No hemos podido completar la operación. Vuelve a intentarlo.', campos)
  }
  return datos
}

function esUsuario(datos: unknown): datos is UsuarioCuenta {
  if (typeof datos !== 'object' || datos === null) return false
  const objeto = datos as Record<string, unknown>
  return typeof objeto.id === 'string' && typeof objeto.nombre === 'string' && typeof objeto.correo === 'string'
}

export async function obtenerSesion(): Promise<UsuarioCuenta | null> {
  const respuesta = await peticionApi('/api/auth/sesion', { signal: AbortSignal.timeout(10000) })
  if (respuesta.status === 401) return null
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok || !esUsuario(datos)) throw new Error('No podemos comprobar tu sesión. Comprueba que el backend esté disponible.')
  return datos
}

export async function iniciarSesion(correo: string, contrasena: string) {
  const datos = await datosCuenta('login', { correo: correo.trim(), contrasena })
  olvidarCsrf() // El login renueva la protección y el identificador de sesión.
  if (!esUsuario(datos)) throw new Error('La respuesta de inicio de sesión no es válida.')
  return datos
}
export async function registrarCuenta(nombre: string, correo: string, contrasena: string, confirmacionContrasena: string) {
  await datosCuenta('registro', { nombre: nombre.trim(), correo: correo.trim(), contrasena, confirmacionContrasena })
}
export async function reenviarVerificacion(correo: string) { await datosCuenta('reenviar', { correo: correo.trim() }) }
export async function verificarCorreo(token: string, contrasena: string) { await datosCuenta('verificar', { token, contrasena }) }
export async function cerrarSesion() { await datosCuenta('logout', {}); olvidarCsrf() }

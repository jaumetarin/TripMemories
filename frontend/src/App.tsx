import { useCallback, useEffect, useRef, useState } from 'react'
import { Icon } from './assets/Icon'
import { Home } from './pages/Home'
import { CrearItinerario } from './pages/CrearItinerario'
import { Cuenta } from './pages/Cuenta'
import { MisViajes } from './pages/MisViajes'
import type { ViajeGuardado } from './viajes'
import type { ModoCuenta } from './pages/Cuenta'
import { cerrarSesion, obtenerSesion, olvidarCsrf } from './sesion'
import type { UsuarioCuenta } from './sesion'
import './App.css'

type Pantalla = 'inicio' | 'crear' | 'viajes' | 'recuerdos' | 'perfil' | 'cuenta'

// Se captura antes de montar React; no se guarda el token en almacenamiento persistente.
const fragmentoInicial = new URLSearchParams(window.location.hash.slice(1))
const vieneDeVerificacion = fragmentoInicial.has('verificar-correo')
const tokenInicial = fragmentoInicial.get('verificar-correo') ?? ''
if (vieneDeVerificacion) window.history.replaceState(null, '', window.location.pathname + window.location.search)

function App() {
  const [pantalla, setPantalla] = useState<Pantalla>(vieneDeVerificacion ? 'cuenta' : 'inicio')
  const [modoCuenta, setModoCuenta] = useState<ModoCuenta>(vieneDeVerificacion ? 'verificar' : 'login')
  const [tokenVerificacion, setTokenVerificacion] = useState(tokenInicial)
  const [usuario, setUsuario] = useState<UsuarioCuenta | null>(null)
  const [edicionViaje, setEdicionViaje] = useState<{ usuarioId: string; viaje: ViajeGuardado } | null>(null)
  const [comprobandoSesion, setComprobandoSesion] = useState(true)
  const [mensajeSesion, setMensajeSesion] = useState('')
  const [cerrando, setCerrando] = useState(false)
  const destinoPendiente = useRef<Pantalla>('perfil')
  const versionSesion = useRef(0)
  const cierreEnCurso = useRef(false)
  const usuarioActual = useRef<UsuarioCuenta | null>(null)
  const contenido = useRef<HTMLElement>(null)
  const edicionPendiente = useRef(false)
  useEffect(() => {
    const actualizar = (evento: Event) => { edicionPendiente.current = (evento as CustomEvent<boolean>).detail === true }
    window.addEventListener('rute-edicion-pendiente', actualizar)
    return () => window.removeEventListener('rute-edicion-pendiente', actualizar)
  }, [])
  useEffect(() => { usuarioActual.current = usuario }, [usuario])

  const perderSesion = useCallback(() => {
    versionSesion.current++
    olvidarCsrf()
    if (cierreEnCurso.current) return
    const teniaUsuario = usuarioActual.current !== null
    usuarioActual.current = null
    setUsuario(null)
    setEdicionViaje(null)
    setComprobandoSesion(false)
    if (!teniaUsuario) return
    setPantalla('cuenta')
    setModoCuenta('login')
    setMensajeSesion('Tu sesión ha terminado. Inicia sesión para acceder a tus datos.')
  }, [])

  const comprobarSesion = useCallback(async () => {
    if (cierreEnCurso.current) return
    const version = ++versionSesion.current
    try {
      const actual = await obtenerSesion()
      if (version !== versionSesion.current) return
      if (usuarioActual.current && !actual) perderSesion()
      else { usuarioActual.current = actual; setUsuario(actual) }
    } catch (error) {
      if (version === versionSesion.current) setMensajeSesion(error instanceof Error ? error.message : 'No se ha podido comprobar la sesión.')
    } finally { if (version === versionSesion.current) setComprobandoSesion(false) }
  }, [perderSesion])

  useEffect(() => {
    let vigente = true
    const version = ++versionSesion.current
    obtenerSesion().then(actual => {
      if (vigente && version === versionSesion.current) { usuarioActual.current = actual; setUsuario(actual) }
    }).catch(error => {
      if (vigente && version === versionSesion.current) setMensajeSesion(error instanceof Error ? error.message : 'No se ha podido comprobar la sesión.')
    }).finally(() => { if (vigente && version === versionSesion.current) setComprobandoSesion(false) })
    const alVolver = () => { if (document.visibilityState === 'visible') void comprobarSesion() }
    const alAbrirEnlace = () => {
      const fragmento = new URLSearchParams(window.location.hash.slice(1))
      if (!fragmento.has('verificar-correo')) return
      setTokenVerificacion(fragmento.get('verificar-correo') ?? '')
      setModoCuenta('verificar'); setPantalla('cuenta'); setMensajeSesion('')
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
    }
    const canal = new BroadcastChannel('rute-sesion')
    canal.onmessage = evento => { if (evento.data === 'cerrada') perderSesion() }
    window.addEventListener('sesion-requerida', perderSesion)
    window.addEventListener('focus', alVolver)
    window.addEventListener('hashchange', alAbrirEnlace)
    document.addEventListener('visibilitychange', alVolver)
    return () => {
      vigente = false
      canal.close()
      window.removeEventListener('sesion-requerida', perderSesion)
      window.removeEventListener('focus', alVolver)
      window.removeEventListener('hashchange', alAbrirEnlace)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [comprobarSesion, perderSesion])

  useEffect(() => { contenido.current?.focus(); window.scrollTo({ top: 0, behavior: 'instant' }) }, [pantalla, modoCuenta])

  function navegar(destino: Pantalla) {
    if (cerrando) return
    if (destino !== pantalla && edicionPendiente.current
      && !window.confirm('Hay cambios del itinerario pendientes. ¿Salir del editor sin guardarlos?')) return
    if (!usuario && destino !== 'inicio' && destino !== 'cuenta') {
      destinoPendiente.current = destino
      setModoCuenta('login')
      setPantalla('cuenta')
      setMensajeSesion('Inicia sesión o crea una cuenta para continuar con tu viaje.')
      return
    }
    setPantalla(destino)
    contenido.current?.focus()
    window.scrollTo({ top: 0, behavior: 'instant' })
  }

  const crearItinerario = () => { setEdicionViaje(null); navegar('crear') }

  async function salir() {
    if (cierreEnCurso.current) return
    if (edicionPendiente.current && !window.confirm('Hay cambios del itinerario pendientes. ¿Cerrar sesión sin guardarlos?')) return
    cierreEnCurso.current = true
    versionSesion.current++
    setCerrando(true)
    try {
      await cerrarSesion()
      versionSesion.current++
      usuarioActual.current = null
      setUsuario(null); setEdicionViaje(null); setPantalla('inicio'); setModoCuenta('login'); setMensajeSesion('Has cerrado sesión.')
      const canal = new BroadcastChannel('rute-sesion'); canal.postMessage('cerrada'); canal.close()
    } catch { setMensajeSesion('No hemos podido cerrar la sesión en el servidor. Vuelve a intentarlo.') }
    finally { cierreEnCurso.current = false; setCerrando(false) }
  }

  return (
    <>
      <a className="saltar-contenido" href="#contenido">Saltar al contenido</a>
      <header className="cabecera">
        <div className="cabecera-interior">
          <button className="marca" type="button" onClick={() => navegar('inicio')} aria-label="Rute, inicio">
            <span className="simbolo-marca"><Icon name="ubicacion" /></span>
            <span>Rute</span>
          </button>
          <nav className="navegacion" aria-label="Navegación principal">
            <button type="button" onClick={() => navegar('inicio')} aria-current={pantalla === 'inicio' ? 'page' : undefined}>Inicio</button>
            <button type="button" onClick={() => navegar('viajes')} aria-current={pantalla === 'viajes' ? 'page' : undefined}>Mis viajes</button>
            <button type="button" onClick={() => navegar('recuerdos')} aria-current={pantalla === 'recuerdos' ? 'page' : undefined}>Mis recuerdos</button>
          </nav>
          <div className="acciones-cabecera">
            <button className="boton boton-cabecera" type="button" onClick={crearItinerario} aria-label="Crear itinerario" aria-current={pantalla === 'crear' ? 'page' : undefined}>
              <Icon name="sumar" /><span>Crear itinerario</span>
            </button>
            <div className="cabecera-sesion">{usuario ? <>
              <button className="avatar" type="button" onClick={() => navegar('perfil')} aria-label="Mi perfil" title="Mi perfil"><Icon name="usuario" /></button>
              <button className="cerrar-sesion" type="button" disabled={cerrando} onClick={() => void salir()}>{cerrando ? 'Cerrando…' : 'Cerrar sesión'}</button>
            </> : <button className="boton boton-cabecera" type="button" disabled={comprobandoSesion} onClick={() => { setModoCuenta('login'); navegar('cuenta') }}>Iniciar sesión</button>}</div>
          </div>
        </div>
      </header>

      <main className="contenido" id="contenido" ref={contenido} tabIndex={-1}>
        {mensajeSesion && <div className="sesion-aviso" role="status">{mensajeSesion}<button type="button" onClick={() => setMensajeSesion('')}>Entendido</button></div>}
        {pantalla === 'cuenta' && (comprobandoSesion ? <p role="status">Comprobando tu sesión…</p>
          : <Cuenta key={modoCuenta + tokenVerificacion} modo={modoCuenta} token={tokenVerificacion} onCambiar={setModoCuenta} onEntrar={actual => {
            versionSesion.current++; usuarioActual.current = actual; setUsuario(actual); setMensajeSesion(''); setPantalla(destinoPendiente.current)
          }} />)}
        {pantalla === 'perfil' && usuario && <section className="perfil-cuenta" aria-labelledby="perfil-titulo">
          <p className="antetitulo">TU ESPACIO EN RUTE</p><h1 id="perfil-titulo">Hola, {usuario.nombre}.</h1>
          <dl><dt>Nombre</dt><dd>{usuario.nombre}</dd><dt>Correo verificado</dt><dd>{usuario.correo}</dd></dl>
          <button className="boton boton-primario" type="button" onClick={crearItinerario}>Crear mi itinerario <Icon name="flecha" /></button>
        </section>}
        {pantalla === 'inicio' && (
          <Home
            onCrearItinerario={crearItinerario}
            onVerViajes={() => navegar('viajes')}
            onVerRecuerdos={() => navegar('recuerdos')}
          />
        )}

        {pantalla === 'crear' && usuario && (
          <CrearItinerario key={usuario.id + ':' + (edicionViaje?.usuarioId === usuario.id ? edicionViaje.viaje.resumen.id + ':' + edicionViaje.viaje.resumen.revision : 'nuevo')}
            viajeInicial={edicionViaje?.usuarioId === usuario.id ? edicionViaje.viaje : undefined}
            onVerViajes={() => navegar('viajes')}
            onVolver={() => navegar(edicionViaje?.usuarioId === usuario.id ? 'viajes' : 'inicio')} />
        )}

        {pantalla === 'viajes' && usuario && <MisViajes key={usuario.id} onCrear={crearItinerario} onEditar={viaje => {
          setEdicionViaje({ usuarioId: usuario.id, viaje }); navegar('crear')
        }} />}
        {pantalla === 'recuerdos' && usuario && (
          <section className="vista-lista" aria-labelledby="titulo-lista">
            <button className="boton-volver" type="button" onClick={() => navegar('inicio')}>← Volver al inicio</button>
            <p className="antetitulo">TU COLECCIÓN PERSONAL</p>
            <h1 id="titulo-lista">Mis recuerdos.</h1>
            <p className="subtitulo">Hay momentos que merecen quedarse.</p>
            <div className="estado-vacio">
              <span className="ilustracion-vacia icono-ambar"><Icon name="album" /></span>
              <h2>El principio de muchos recuerdos.</h2>
              <p>Tus fotos y vídeos se reunirán aquí, organizados en álbumes de cada viaje.</p>
              <button className="boton boton-primario" type="button" onClick={crearItinerario}>Crear un itinerario <Icon name="flecha" /></button>
            </div>
          </section>
        )}
      </main>
      <footer className="pie-pagina"><span>Rute</span><span>Planea. Viaja. Recuerda.</span></footer>
    </>
  )
}

export default App

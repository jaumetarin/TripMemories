import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { actualizarTitulo, listarViajes, obtenerViaje } from '../viajes'
import type { ResumenViaje, ViajeGuardado } from '../viajes'
import ItinerarioPorDias from '../components/ItinerarioPorDias'
import { CandidatosLugares } from '../components/CandidatosLugares'
import { EditorActividades } from '../components/EditorActividades'
import './MisViajes.css'

type Props = { onCrear: () => void; onEditar: (viaje: ViajeGuardado) => void }
function fechaLocal(valor: string | null) { return valor ? valor.split('-').reverse().join('/') : 'Sin fecha' }
export function MisViajes({ onCrear, onEditar }: Props) {
  const [lista, setLista] = useState<ResumenViaje[]>([])
  const [cargando, setCargando] = useState(true)
  const [reintento, setReintento] = useState(0)
  const [viaje, setViaje] = useState<ViajeGuardado | null>(null)
  const [titulo, setTitulo] = useState('')
  const [abriendo, setAbriendo] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [vista, setVista] = useState<'plan' | 'candidatos'>('plan')
  const [editandoActividades, setEditandoActividades] = useState(false)
  const peticion = useRef<AbortController | null>(null)
  const cabecera = useRef<HTMLHeadingElement>(null)
  const errores = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    const controlador = new AbortController()
    listarViajes(controlador.signal).then(datos => { if (!controlador.signal.aborted) setLista(datos) })
      .catch(fallo => { if (!controlador.signal.aborted) setError(fallo instanceof Error ? fallo.message : 'No se puede consultar Mis viajes.') })
      .finally(() => { if (!controlador.signal.aborted) setCargando(false) })
    return () => controlador.abort()
  }, [reintento])
  useEffect(() => () => peticion.current?.abort(), [])
  const viajeId = viaje?.resumen.id
  useEffect(() => { if (viajeId) { cabecera.current?.focus(); window.scrollTo({ top: 0, behavior: 'instant' }) } }, [viajeId])
  useEffect(() => { if (error) errores.current?.focus() }, [error])

  async function abrir(id: string) {
    if (guardando) return
    peticion.current?.abort()
    const controlador = new AbortController(); peticion.current = controlador
    setAbriendo(id); setError(''); setMensaje('')
    try {
      const datos = await obtenerViaje(id, controlador.signal)
      if (controlador.signal.aborted) return
      setViaje(datos); setTitulo(datos.resumen.titulo); setVista('plan')
    } catch (fallo) { if (!controlador.signal.aborted) setError(fallo instanceof Error ? fallo.message : 'No se ha podido abrir el viaje.') }
    finally { if (peticion.current === controlador) { peticion.current = null; setAbriendo(null) } }
  }
  async function guardarNombre(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!viaje || peticion.current || titulo.trim() === viaje.resumen.titulo) return
    const controlador = new AbortController(); peticion.current = controlador
    setGuardando(true); setError(''); setMensaje('')
    try {
      const actualizado = await actualizarTitulo(viaje.resumen, titulo.trim(), controlador.signal)
      if (controlador.signal.aborted) return
      setViaje(actual => actual ? { ...actual, resumen: { ...actual.resumen, titulo: titulo.trim(), revision: actualizado.revision } } : actual)
      setTitulo(titulo.trim()); setMensaje('Título guardado correctamente. El itinerario se ha conservado sin regenerarlo.')
      try {
        const recargado = await obtenerViaje(actualizado.id, controlador.signal)
        if (!controlador.signal.aborted) { setViaje(recargado); setTitulo(recargado.resumen.titulo) }
      } catch { if (!controlador.signal.aborted) setMensaje('El guardado está confirmado. No se ha podido recargar la vista; puedes volver al listado.') }
    } catch (fallo) { if (!controlador.signal.aborted) setError(fallo instanceof Error && fallo.name === 'Error' ? fallo.message
      : 'No hemos podido confirmar la actualización. Tus cambios siguen aquí. Consulta la versión guardada antes de reintentar.') }
    finally { peticion.current = null; if (!controlador.signal.aborted) setGuardando(false) }
  }
  function recargarListado() { setCargando(true); setError(''); setReintento(v => v + 1) }
  function volver() { peticion.current?.abort(); peticion.current = null; setViaje(null); setAbriendo(null); setMensaje(''); recargarListado() }
  if (viaje && editandoActividades) return <EditorActividades viaje={viaje}
    onCerrar={() => setEditandoActividades(false)}
    onGuardado={() => { setEditandoActividades(false); volver(); setMensaje('Cambios del itinerario guardados correctamente. Ya puedes abrir la versión actualizada.') }} />
  const programados = new Set(viaje?.itinerario.dias.flatMap(dia => dia.bloques.flatMap(bloque => bloque.candidato ? [bloque.candidato.lugar.id] : [])) ?? [])
  return <section className="mis-viajes" aria-labelledby="mis-viajes-titulo">
    <p className="antetitulo">TU COLECCIÓN PERSONAL</p>
    <h1 id="mis-viajes-titulo" ref={cabecera} tabIndex={-1}>{viaje ? viaje.resumen.titulo : 'Mis viajes.'}</h1>
    {error && <p className="viajes-error" role="alert" ref={errores} tabIndex={-1}>{error}</p>}
    {mensaje && <p className="viajes-confirmacion" role="status">{mensaje}</p>}
    {viaje ? <>
      <div className="viajes-acciones">
        <button className="boton" type="button" disabled={guardando} onClick={volver}>← Volver a Mis viajes</button>
        <button className="boton boton-primario" type="button" disabled={guardando || !!abriendo}
          onClick={() => onEditar({ ...viaje, resumen: { ...viaje.resumen, titulo: titulo.trim() || viaje.resumen.titulo } })}>Editar configuración</button>
        <button className="boton boton-primario" type="button" disabled={guardando || !!abriendo || titulo.trim() !== viaje.resumen.titulo}
          onClick={() => setEditandoActividades(true)}>Editar actividades</button>
      </div>
      <p>Guardado en tu cuenta. Esta consulta no genera otra propuesta ni consume una generación.</p>
      {titulo.trim() !== viaje.resumen.titulo && <p>Guarda o descarta el título pendiente antes de editar las actividades.</p>}
      <form className="guardar-viaje" onSubmit={guardarNombre}>
        <label htmlFor="editar-titulo-viaje">Título del viaje</label>
        <input id="editar-titulo-viaje" value={titulo} onChange={e => setTitulo(e.target.value)} maxLength={160} required disabled={guardando || !!abriendo} />
        <div className="viajes-acciones">
          <button className="boton boton-primario" disabled={guardando || !!abriendo || !titulo.trim() || titulo.trim() === viaje.resumen.titulo}>
            {guardando ? 'Guardando…' : 'Guardar título'}
          </button>
          <button className="boton" type="button" disabled={guardando || !!abriendo} onClick={() => void abrir(viaje.resumen.id)}>
            {titulo.trim() !== viaje.resumen.titulo ? 'Descartar cambios pendientes y recargar' : 'Recargar viaje'}
          </button>
        </div>
        {titulo.trim() !== viaje.resumen.titulo && <p role="status">El nuevo título está pendiente de guardar.</p>}
      </form>
      <nav className="resultado-vistas" aria-label="Vistas del viaje guardado">
        <button type="button" aria-current={vista === 'plan' ? 'page' : undefined} onClick={() => setVista('plan')}>Itinerario por días</button>
        <button type="button" aria-current={vista === 'candidatos' ? 'page' : undefined} onClick={() => setVista('candidatos')}>Lugares considerados</button>
      </nav>
      {vista === 'plan' ? <ItinerarioPorDias key={viaje.resumen.id + ':' + viaje.resumen.revision} itinerario={viaje.itinerario} configuracion={viaje.configuracion}
        origenTrazados={{ tipo: 'viaje', id: viaje.resumen.id, revision: viaje.resumen.revision }} />
        : <CandidatosLugares candidatos={viaje.itinerario.candidatos} fallback={viaje.itinerario.evaluacionFallback} programados={programados} />}
    </> : <>
      <p className="subtitulo">Tus planes, listos para volver a abrirlos.</p>
      <div className="viajes-acciones">
        <button className="boton boton-primario" type="button" onClick={onCrear}>Crear itinerario</button>
        <button className="boton" type="button" disabled={cargando} onClick={recargarListado}>Actualizar listado</button>
      </div>
      {cargando ? <p role="status">Cargando tus viajes…</p> : error ? null : lista.length === 0
        ? <div className="estado-vacio"><h2>Tu primer destino te espera.</h2><p>Cuando guardes un itinerario aparecerá aquí.</p></div>
        : <ul className="viajes-listado">{lista.map(resumen => <li key={resumen.id} className="viaje-tarjeta">
          <h2>{resumen.titulo}</h2><p>{resumen.destino}</p><p>{fechaLocal(resumen.fechaInicio)} — {fechaLocal(resumen.fechaFin)}</p>
          <p className="viaje-fecha">Último guardado: {new Date(resumen.actualizadoEn).toLocaleString('es-ES')}</p>
          <button className="boton boton-primario" type="button" disabled={!!abriendo} onClick={() => void abrir(resumen.id)}>
            {abriendo === resumen.id ? 'Abriendo…' : 'Abrir viaje'}
          </button>
        </li>)}</ul>}
    </>}
  </section>
}

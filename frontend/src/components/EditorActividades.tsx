import { useEffect, useRef, useState } from 'react'
import type { ViajeGuardado } from '../viajes'
import type { AlternativasEdicion, DiaEditable, EstadoEdicion, VisitaEditable } from '../edicion'
import { eliminarVisita, guardarEdicion, iniciarEdicion, obtenerAlternativas, recalcularEdicion, reordenarVisitas, sustituirVisita } from '../edicion'
import ItinerarioPorDias from './ItinerarioPorDias'
import './EditorActividades.css'

type Props = { viaje: ViajeGuardado; onCerrar: () => void; onGuardado: () => void }
const costes = { FREE: 'Gratuito', LOW: 'Coste bajo', MEDIUM: 'Coste moderado', HIGH: 'Coste alto', UNKNOWN: 'Precio pendiente de confirmar' }
export function EditorActividades({ viaje, onCerrar, onGuardado }: Props) {
  const [estado, setEstado] = useState<EstadoEdicion | null>(null)
  const [ocupado, setOcupado] = useState(true)
  const [error, setError] = useState('')
  const [aceptarAvisos, setAceptarAvisos] = useState(false)
  const [opciones, setOpciones] = useState<(AlternativasEdicion & { actividad: string }) | null>(null)
  const [seleccion, setSeleccion] = useState('')
  const [reintento, setReintento] = useState(0)
  const peticion = useRef<AbortController | null>(null)
  const aviso = useRef<HTMLParagraphElement>(null)
  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => { titulo.current?.focus() }, [])
  useEffect(() => {
    const controlador = new AbortController()
    peticion.current = controlador
    iniciarEdicion(viaje.resumen.id, viaje.resumen.revision, controlador.signal)
      .then(datos => { if (!controlador.signal.aborted) setEstado(datos) })
      .catch(fallo => { if (!controlador.signal.aborted) setError(fallo instanceof Error ? fallo.message : 'No se puede abrir el editor.') })
      .finally(() => { if (!controlador.signal.aborted) { setOcupado(false); peticion.current = null } })
    return () => controlador.abort()
  }, [viaje.resumen.id, viaje.resumen.revision, reintento])
  useEffect(() => () => peticion.current?.abort(), [])
  useEffect(() => { if (error) aviso.current?.focus() }, [error])
  const pendiente = estado?.modificada ?? false
  useEffect(() => {
    window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: pendiente }))
    const alSalir = (evento: BeforeUnloadEvent) => { if (pendiente) { evento.preventDefault(); evento.returnValue = '' } }
    window.addEventListener('beforeunload', alSalir)
    return () => {
      window.removeEventListener('beforeunload', alSalir)
      window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: false }))
    }
  }, [pendiente])

  async function ejecutar(accion: (signal: AbortSignal) => Promise<void>) {
    if (peticion.current) return
    const controlador = new AbortController(); peticion.current = controlador
    setOcupado(true); setError('')
    try { await accion(controlador.signal) }
    catch (fallo) { if (!controlador.signal.aborted) setError(fallo instanceof Error ? fallo.message : 'No se pudo completar la edición.') }
    finally { if (!controlador.signal.aborted) { setOcupado(false); peticion.current = null } }
  }
  function actualizar(datos: EstadoEdicion) { setEstado(datos); setOpciones(null); setSeleccion(''); setAceptarAvisos(false) }
  function cerrar() {
    if (!pendiente || window.confirm('¿Descartar los cambios pendientes? El viaje guardado se conservará.')) onCerrar()
  }
  function eliminar(visita: VisitaEditable) {
    if (!estado) return
    if (visita.obligatorio && !window.confirm('Esta visita es obligatoria. Al eliminarla quedará un obligatorio incumplido; su prioridad se conservará. ¿Continuar con la propuesta?')) return
    void ejecutar(async signal => { const datos = await eliminarVisita(viaje.resumen.id, estado, visita.actividadId, signal); if (!signal.aborted) actualizar(datos) })
  }
  function mover(dia: DiaEditable, indice: number, desplazamiento: number) {
    if (!estado) return
    const orden = dia.visitas.map(v => v.actividadId)
    const destino = indice + desplazamiento
    if (destino < 0 || destino >= orden.length) return
    ;[orden[indice], orden[destino]] = [orden[destino], orden[indice]]
    void ejecutar(async signal => { const datos = await reordenarVisitas(viaje.resumen.id, estado, dia.fecha, orden, signal); if (!signal.aborted) actualizar(datos) })
  }
  function alternativas(visita: VisitaEditable) {
    if (!estado) return
    void ejecutar(async signal => {
      const datos = await obtenerAlternativas(viaje.resumen.id, estado, visita.actividadId, signal)
      if (!signal.aborted) { setOpciones({ ...datos, actividad: visita.actividadId }); setSeleccion('') }
    })
  }
  function confirmarSustitucion() {
    if (!estado || !opciones || !seleccion) return
    void ejecutar(async signal => { const datos = await sustituirVisita(viaje.resumen.id, estado, opciones.actividad, seleccion, signal); if (!signal.aborted) actualizar(datos) })
  }
  function guardar() {
    if (!estado) return
    void ejecutar(async signal => {
      await guardarEdicion(viaje.resumen.id, estado, aceptarAvisos, signal)
      if (!signal.aborted) {
        window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: false }))
        onGuardado()
      }
    })
  }

  return <section className="editor-actividades" aria-labelledby="editor-titulo" aria-busy={ocupado}>
    <p className="antetitulo">EDITA TU ITINERARIO</p>
    <h1 id="editor-titulo" tabIndex={-1} ref={titulo}>{viaje.resumen.titulo}</h1>
    <p>Elimina, sube, baja o sustituye visitas dentro de cada día. El viaje guardado se conserva hasta confirmar los cambios.</p>
    <button className="boton" type="button" disabled={ocupado} onClick={cerrar}>Cancelar edición y volver</button>
    {error && <p className="viajes-error" role="alert" tabIndex={-1} ref={aviso}>{error}</p>}
    {ocupado && <p role="status">{estado ? 'Comprobando recorridos y horarios…' : 'Abriendo el editor…'}</p>}
    {!estado && !ocupado && <button className="boton" type="button" onClick={() => { setOcupado(true); setError(''); setReintento(v => v + 1) }}>Reintentar</button>}
    {estado && <>
      <p role="status">{estado.modificada ? 'Cambios pendientes de guardar.' : 'Todavía no has modificado el itinerario.'}</p>
      {estado.propuesta.incidencias.length > 0 && <section className="editor-incidencias" aria-labelledby="editor-incidencias-titulo">
        <h2 id="editor-incidencias-titulo">Revisión de los cambios</h2>
        <ul>{estado.propuesta.incidencias.map((i, indice) => <li key={indice} className={i.nivel === 'CONFLICTO' ? 'viajes-error' : 'mapa-aviso'}>
          <strong>{i.nivel === 'CONFLICTO' ? 'Conflicto: ' : 'Aviso: '}</strong>{i.dia && `${i.dia.split('-').reverse().join('/')} · `}{i.mensaje}
        </li>)}</ul>
      </section>}
      {estado.dias.map(dia => <section key={dia.fecha} className="editor-dia" aria-label={'Visitas del ' + dia.fecha}>
        <h2>{dia.fecha.split('-').reverse().join('/')}</h2>
        {dia.visitas.length === 0 ? <p>No hay visitas seleccionadas para este día.</p> : <ol className="editor-visitas">{dia.visitas.map((visita, indice) => {
          const bloque = estado.propuesta.itinerario.dias.find(d => d.fecha === dia.fecha)?.bloques.find(b => b.tipo === 'VISITA' && b.candidato?.lugar.id === visita.candidato.lugar.id)
          return <li key={visita.actividadId}>
            <h3>{visita.candidato.lugar.nombre}</h3>
            <p>{visita.obligatorio ? 'Obligatoria · ' : ''}{visita.candidato.duracionMinutos} min · {costes[visita.candidato.coste]}</p>
            <p>{bloque ? `${bloque.inicio.slice(11, 16)}–${bloque.fin.slice(11, 16)}` : 'No ha podido colocarse: revisa los conflictos.'}</p>
            <div className="viajes-acciones">
              <button className="boton" type="button" disabled={ocupado || indice === 0} aria-label={'Subir ' + visita.candidato.lugar.nombre} onClick={() => mover(dia, indice, -1)}>↑ Subir</button>
              <button className="boton" type="button" disabled={ocupado || indice === dia.visitas.length - 1} aria-label={'Bajar ' + visita.candidato.lugar.nombre} onClick={() => mover(dia, indice, 1)}>↓ Bajar</button>
              <button className="boton" type="button" disabled={ocupado || visita.obligatorio} onClick={() => alternativas(visita)}>Sustituir</button>
              <button className="boton" type="button" disabled={ocupado} aria-label={'Eliminar ' + visita.candidato.lugar.nombre} onClick={() => eliminar(visita)}>Eliminar</button>
            </div>
            {visita.obligatorio && <p>Una visita obligatoria no se sustituye automáticamente ni cambia de prioridad.</p>}
            {opciones?.actividad === visita.actividadId && <section className="editor-alternativas" aria-label={'Alternativas a ' + visita.candidato.lugar.nombre}>
              <p role="status">{opciones.mensaje}</p>
              <fieldset disabled={ocupado}><legend>Elige una alternativa</legend>{opciones.candidatos.map(c => <label key={c.lugar.id}>
                <input type="radio" name="alternativa" value={c.lugar.id} checked={seleccion === c.lugar.id} onChange={() => setSeleccion(c.lugar.id)} />
                <span><strong>{c.lugar.nombre}</strong> · {c.duracionMinutos} min · {costes[c.coste]}<br />{c.motivo}{c.horario && <><br />Horario publicado: {c.horario}</>}</span>
              </label>)}</fieldset>
              <div className="viajes-acciones"><button className="boton boton-primario" type="button" disabled={ocupado || !seleccion} onClick={confirmarSustitucion}>Confirmar sustitución</button>
                <button className="boton" type="button" disabled={ocupado} onClick={() => { setOpciones(null); setSeleccion('') }}>Cancelar sustitución</button></div>
            </section>}
          </li>
        })}</ol>}
      </section>)}
      <section className="guardar-viaje" aria-labelledby="editor-guardar-titulo">
        <h2 id="editor-guardar-titulo">Guardar la propuesta</h2>
        <button className="boton" type="button" disabled={ocupado || !estado.modificada} onClick={() => {
          void ejecutar(async signal => { const datos = await recalcularEdicion(viaje.resumen.id, estado, signal); if (!signal.aborted) actualizar(datos) })
        }}>Recalcular recorridos</button>
        {!estado.propuesta.puedeGuardar && <p role="status">Corrige los conflictos para poder guardar. Puedes cambiar de nuevo el orden o cancelar la edición.</p>}
        {estado.propuesta.incidencias.some(i => i.nivel === 'AVISO') && <label className="editor-aceptacion"><input type="checkbox" checked={aceptarAvisos} disabled={ocupado} onChange={e => setAceptarAvisos(e.target.checked)} />He leído y acepto los avisos, incluidos los obligatorios incumplidos si los hay.</label>}
        <button className="boton boton-primario" type="button" disabled={ocupado || !estado.modificada || !estado.propuesta.puedeGuardar || (estado.propuesta.incidencias.length > 0 && !aceptarAvisos)} onClick={guardar}>Guardar cambios del itinerario</button>
      </section>
      <h2>Vista previa del recorrido</h2>
      <ItinerarioPorDias key={estado.propuesta.propuestaId} itinerario={estado.propuesta.itinerario} configuracion={viaje.configuracion} />
    </>}
  </section>
}

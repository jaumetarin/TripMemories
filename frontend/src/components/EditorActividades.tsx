import { useEffect, useRef, useState } from 'react'
import type { BloqueItinerario, DiaItinerario } from '../api'
import type { ViajeGuardado } from '../viajes'
import type { AlternativasEdicion, DiaEditable, EstadoEdicion, IncidenciaEdicion, VisitaEditable } from '../edicion'
import { eliminarVisita, guardarEdicion, iniciarEdicion, obtenerAlternativas, recalcularEdicion, recuperarEdicion, reordenarVisitas, restaurarVisita, sustituirVisita } from '../edicion'
import ItinerarioPorDias from './ItinerarioPorDias'
import './EditorActividades.css'

type Props = { viaje: ViajeGuardado; onCerrar: () => void; onGuardado: () => void }
const costes = { FREE: 'Gratuito', LOW: 'Coste bajo', MEDIUM: 'Coste moderado', HIGH: 'Coste alto', UNKNOWN: 'Precio por confirmar' }
const formatoFecha = (fecha: string) => fecha.split('-').reverse().join('/')
export function EditorActividades({ viaje, onCerrar, onGuardado }: Props) {
  const [estado, setEstado] = useState<EstadoEdicion | null>(null)
  const [ocupado, setOcupado] = useState(true)
  const [operacion, setOperacion] = useState('Abriendo tu itinerario…')
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [aceptarAvisos, setAceptarAvisos] = useState(false)
  const [opciones, setOpciones] = useState<(AlternativasEdicion & { actividad: string }) | null>(null)
  const [moviendo, setMoviendo] = useState<{ actividad: string; posicion: number } | null>(null)
  const [fecha, setFecha] = useState<string | null>(null)
  const [reintento, setReintento] = useState(0)
  const peticion = useRef<AbortController | null>(null)
  const aviso = useRef<HTMLParagraphElement>(null)
  const titulo = useRef<HTMLHeadingElement>(null)
  const zona = useRef<HTMLElement>(null)
  const tituloAlternativas = useRef<HTMLHeadingElement>(null)
  const focoTrasCambio = useRef<string | null>(null)
  useEffect(() => { titulo.current?.focus() }, [])
  useEffect(() => { if (opciones) tituloAlternativas.current?.focus() }, [opciones])
  useEffect(() => {
    if (!focoTrasCambio.current || ocupado) return
    const destino = [...(zona.current?.querySelectorAll<HTMLButtonElement>('[data-editor-id]') ?? [])]
      .find(elemento => elemento.dataset.editorId === focoTrasCambio.current && !elemento.disabled)
    destino?.focus({ preventScroll: true })
    focoTrasCambio.current = null
  }, [estado, moviendo, opciones, ocupado])
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
    // También proteger la navegación durante una modificación cuya respuesta aún no ha llegado.
    const hayCambios = pendiente || ocupado && estado !== null
    window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: hayCambios }))
    const alSalir = (evento: BeforeUnloadEvent) => { if (hayCambios) { evento.preventDefault(); evento.returnValue = '' } }
    window.addEventListener('beforeunload', alSalir)
    return () => {
      window.removeEventListener('beforeunload', alSalir)
      window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: false }))
    }
  }, [pendiente, ocupado, estado])

  async function ejecutar(texto: string, accion: (signal: AbortSignal) => Promise<void>) {
    if (peticion.current) return
    const controlador = new AbortController(); peticion.current = controlador
    setOcupado(true); setOperacion(texto); setError(''); setMensaje('')
    try { await accion(controlador.signal) }
    catch (fallo) { if (!controlador.signal.aborted) setError(fallo instanceof Error ? fallo.message : 'No se pudo completar el cambio.') }
    finally { if (!controlador.signal.aborted) { setOcupado(false); peticion.current = null } }
  }
  function actualizar(datos: EstadoEdicion, texto: string, foco?: string) {
    focoTrasCambio.current = foco ?? null
    setEstado(datos); setOpciones(null); setMoviendo(null); setAceptarAvisos(false); setMensaje(texto)
  }
  function cerrar() {
    if (!pendiente || window.confirm('¿Descartar todos los cambios pendientes? El viaje guardado se conservará.')) onCerrar()
  }
  function eliminar(visita: VisitaEditable) {
    if (!estado) return
    if (visita.obligatorio && !window.confirm('Esta visita es obligatoria. Si la eliminas quedará incumplida, aunque conservará su prioridad. Puedes deshacerlo antes de guardar. ¿Eliminarla del borrador?')) return
    void ejecutar('Quitando la visita y ajustando el recorrido…', async signal => {
      const datos = await eliminarVisita(viaje.resumen.id, estado, visita.actividadId, signal)
      if (!signal.aborted) actualizar(datos, `${visita.candidato.lugar.nombre} eliminada. Puedes deshacerlo.`, visita.actividadId)
    })
  }
  function deshacer(visita: VisitaEditable) {
    if (!estado) return
    void ejecutar('Restaurando la visita y sus trayectos…', async signal => {
      const datos = await restaurarVisita(viaje.resumen.id, estado, visita.actividadId, signal)
      if (!signal.aborted) actualizar(datos, `${visita.candidato.lugar.nombre} restaurada.`, visita.actividadId)
    })
  }
  function mover(dia: DiaEditable, actividad: string, posicion: number) {
    if (!estado) return
    const orden = dia.visitas.map(v => v.actividadId)
    const desde = orden.indexOf(actividad)
    if (desde < 0 || posicion < 0 || posicion >= orden.length || desde === posicion) { setMoviendo(null); return }
    orden.splice(desde, 1); orden.splice(posicion, 0, actividad)
    void ejecutar('Actualizando el orden, los horarios y los trayectos…', async signal => {
      const datos = await reordenarVisitas(viaje.resumen.id, estado, dia.fecha, orden, signal)
      if (!signal.aborted) actualizar(datos, 'Orden actualizado. Los horarios y trayectos ya reflejan el cambio.', actividad)
    })
  }
  function alternativas(visita: VisitaEditable) {
    if (!estado) return
    setMoviendo(null); setOpciones(null)
    void ejecutar('Buscando actividades que encajen en tu día…', async signal => {
      const datos = await obtenerAlternativas(viaje.resumen.id, estado, visita.actividadId, signal)
      if (!signal.aborted) setOpciones({ ...datos, actividad: visita.actividadId })
    })
  }
  function elegir(visita: VisitaEditable, candidato: string) {
    if (!estado) return
    void ejecutar('Aplicando la alternativa y ajustando el recorrido…', async signal => {
      const datos = await sustituirVisita(viaje.resumen.id, estado, visita.actividadId, candidato, signal)
      if (!signal.aborted) actualizar(datos, 'Actividad sustituida en el borrador. Revisa su nuevo horario.', visita.actividadId)
    })
  }
  function guardar() {
    if (!estado) return
    void ejecutar('Guardando los cambios de tu viaje…', async signal => {
      await guardarEdicion(viaje.resumen.id, estado, aceptarAvisos, signal)
      if (!signal.aborted) { window.dispatchEvent(new CustomEvent('rute-edicion-pendiente', { detail: false })); onGuardado() }
    })
  }
  function avisos(lista: IncidenciaEdicion[]) {
    return lista.length > 0 && <ul className="editor-avisos-locales">{lista.map((i, indice) => <li key={indice} className={i.nivel === 'CONFLICTO' ? 'editor-conflicto' : 'editor-aviso'}>
      <strong>{i.nivel === 'CONFLICTO' ? 'Hay que revisarlo: ' : 'Ten en cuenta: '}</strong>{i.mensaje}
    </li>)}</ul>
  }
  function deVisita(i: IncidenciaEdicion, visita: VisitaEditable) {
    return i.actividadId === visita.actividadId || i.lugarId === visita.candidato.lugar.id
  }
  function controles(visita: VisitaEditable, dia: DiaEditable) {
    const indice = dia.visitas.findIndex(v => v.actividadId === visita.actividadId)
    return <div className="editor-controles">
      <div className="editor-datos"><span>{visita.candidato.duracionMinutos} min · {costes[visita.candidato.coste]}</span>{visita.obligatorio && <strong className="editor-obligatoria">Obligatoria</strong>}</div>
      {avisos(estado?.propuesta.incidencias.filter(i => (!i.dia || i.dia === dia.fecha) && deVisita(i, visita)) ?? [])}
      <div className="editor-acciones" role="group" aria-label={'Editar ' + visita.candidato.lugar.nombre}>
        <button className="boton editor-arrastrar" type="button" disabled={ocupado || dia.visitas.length < 2} draggable={!ocupado && dia.visitas.length > 1}
          onDragStart={evento => { evento.dataTransfer.setData('application/x-rute-visita', visita.actividadId); evento.dataTransfer.effectAllowed = 'move' }}
          onClick={() => { setMoviendo({ actividad: visita.actividadId, posicion: indice }); setOpciones(null) }}
          data-editor-id={visita.actividadId} aria-label={'Mover ' + visita.candidato.lugar.nombre}
          aria-expanded={moviendo?.actividad === visita.actividadId} title="Arrastra hasta otra visita o pulsa para elegir una posición">↕ Mover</button>
        <button className="boton" type="button" disabled={ocupado || visita.obligatorio} onClick={() => alternativas(visita)} data-editor-id={visita.actividadId} aria-label={'Sustituir ' + visita.candidato.lugar.nombre}>Sustituir</button>
        <button className="boton editor-eliminar" type="button" disabled={ocupado} onClick={() => eliminar(visita)} data-editor-id={visita.actividadId} aria-label={'Eliminar ' + visita.candidato.lugar.nombre}>Eliminar</button>
      </div>
      {visita.obligatorio && <p className="editor-ayuda">Puedes moverla. Para sustituirla tendrías que revisar antes su prioridad.</p>}
      {moviendo?.actividad === visita.actividadId && <div className="editor-mover">
        <label>¿Dónde quieres colocarla?<select autoFocus disabled={ocupado} value={moviendo.posicion} onChange={e => setMoviendo({ actividad: visita.actividadId, posicion: Number(e.target.value) })}>
          {dia.visitas.map((otra, posicion) => <option key={otra.actividadId} value={posicion}>{posicion + 1}. {posicion === indice ? 'Posición actual' : (posicion < indice ? 'Antes de ' : 'Después de ') + otra.candidato.lugar.nombre}</option>)}
        </select></label>
        <button className="boton boton-primario" type="button" disabled={ocupado || moviendo.posicion === indice} onClick={() => mover(dia, visita.actividadId, moviendo.posicion)}>Mover aquí</button>
        <button className="boton" type="button" onClick={() => { focoTrasCambio.current = visita.actividadId; setMoviendo(null) }}>Cancelar movimiento</button>
      </div>}
      {opciones?.actividad === visita.actividadId && <section className="editor-alternativas" aria-label={'Alternativas a ' + visita.candidato.lugar.nombre}>
        <h4 tabIndex={-1} ref={tituloAlternativas}>¿Qué prefieres hacer en su lugar?</h4>
        <p>{opciones.mensaje}</p>
        <p className="editor-ayuda">Elegir una actividad la cambia en el borrador. El viaje solo se actualiza cuando guardes.</p>
        <ul>{opciones.candidatos.map(c => {
          const diferencia = c.duracionMinutos - visita.candidato.duracionMinutos
          return <li key={c.lugar.id}><h5>{c.lugar.nombre}</h5><p>{c.duracionMinutos} min · {costes[c.coste]} · {diferencia === 0 ? 'Misma duración' : `${Math.abs(diferencia)} min ${diferencia > 0 ? 'más' : 'menos'}`}</p>
            <p>{c.motivo}</p>{c.horario && <p>Horario: {c.horario}</p>}
            <button className="boton boton-primario" type="button" disabled={ocupado} onClick={() => elegir(visita, c.lugar.id)} aria-label={'Elegir ' + c.lugar.nombre}>Elegir esta actividad</button></li>
        })}</ul>
        <button className="boton" type="button" disabled={ocupado} onClick={() => { focoTrasCambio.current = visita.actividadId; setOpciones(null); setMensaje('Se conserva la actividad actual.') }}>Conservar la actual</button>
      </section>}
    </div>
  }
  function tarjeta(bloque: BloqueItinerario, fechaDia: string) {
    const dia = estado?.dias.find(d => d.fecha === fechaDia)
    const visita = dia?.visitas.find(v => v.candidato.lugar.id === bloque.candidato?.lugar.id)
    return visita && dia ? controles(visita, dia) : null
  }
  function pieDia(dia: DiaItinerario) {
    const editable = estado?.dias.find(d => d.fecha === dia.fecha)
    const colocadas = new Set(dia.bloques.filter(b => b.tipo === 'VISITA').map(b => b.candidato?.lugar.id))
    const sinColocar = editable?.visitas.filter(v => !colocadas.has(v.candidato.lugar.id)) ?? []
    const delDia = estado?.propuesta.incidencias.filter(i => i.dia === dia.fecha && !editable?.visitas.some(v => deVisita(i, v))) ?? []
    return <>
      {avisos(delDia)}
      {sinColocar.length > 0 && <section className="editor-sin-colocar" aria-label="Visitas pendientes de encajar"><h4>Estas visitas todavía no encajan</h4><p>Muévelas a otra posición, sustitúyelas o elimínalas para resolver el día.</p>
        {sinColocar.map(visita => <article key={visita.actividadId}><h4>{visita.candidato.lugar.nombre}</h4>{editable && controles(visita, editable)}</article>)}
      </section>}
      {estado?.eliminadas.filter(e => e.dia === dia.fecha).map(e => <div className="editor-deshacer" key={e.visita.actividadId}>
        <span><strong>{e.visita.candidato.lugar.nombre}</strong> eliminada del día.</span>
        <button className="boton" type="button" disabled={ocupado} data-editor-id={e.visita.actividadId} onClick={() => deshacer(e.visita)} aria-label={'Deshacer eliminación de ' + e.visita.candidato.lugar.nombre}>↶ Deshacer</button>
      </div>)}
    </>
  }
  const incidencias = estado?.propuesta.incidencias ?? []
  const conflictos = incidencias.filter(i => i.nivel === 'CONFLICTO')
  const advertencias = incidencias.filter(i => i.nivel === 'AVISO')
  const consejos = estado?.propuesta.itinerario.advertencias.filter(a => !incidencias.some(i => a === `Edición: ${i.mensaje}`)) ?? []
  const globales = incidencias.filter(i => !i.dia && !estado?.dias.some(d => d.visitas.some(v => deVisita(i, v))))
  return <section ref={zona} className="editor-actividades" aria-labelledby="editor-titulo">
    <p className="antetitulo">TU VIAJE, A TU MANERA</p>
    <h1 id="editor-titulo" tabIndex={-1} ref={titulo}>{viaje.resumen.titulo}</h1>
    <p className="editor-intro">Edita directamente cada visita. Horarios, trayectos y mapa se actualizan al hacer un cambio.</p>
    <p className="editor-ayuda">Pulsa «Mover» para elegir una posición o arrastra su control hasta otra visita del mismo día. Puedes deshacer las eliminaciones antes de guardar.</p>
    {error && <p className="viajes-error" role="alert" tabIndex={-1} ref={aviso}>{error}</p>}
    {error && estado && <button className="boton" type="button" disabled={ocupado} onClick={() => {
      void ejecutar('Recuperando tus cambios…', async signal => { const datos = await recuperarEdicion(viaje.resumen.id, viaje.resumen.revision, signal); if (!signal.aborted) actualizar(datos, 'Borrador recuperado. Puedes continuar editando.') })
    }}>Recuperar cambios pendientes</button>}
    <p className="editor-estado" role="status" aria-live="polite">{ocupado ? operacion : mensaje || 'El viaje guardado se conserva hasta que pulses «Guardar cambios».'}</p>
    {!estado && !ocupado && <button className="boton" type="button" onClick={() => { setOcupado(true); setError(''); setReintento(v => v + 1) }}>Reintentar</button>}
    {estado && <>
      {consejos.length > 0 && <details className="itinerario-aviso"><summary>Consejos de tu viaje</summary><ul>{consejos.map((consejo, indice) => <li key={indice}>{consejo}</li>)}</ul></details>}
      <ItinerarioPorDias itinerario={estado.propuesta.itinerario} configuracion={viaje.configuracion} edicion={{
        fecha, onDia: nueva => { setFecha(nueva); setMoviendo(null); setOpciones(null) }, tarjeta, pieDia,
        resumenDia: fechaDia => {
          const cantidad = conflictos.filter(i => i.dia === fechaDia).length
          return cantidad ? `${cantidad} ${cantidad === 1 ? 'conflicto' : 'conflictos'}` : ''
        },
        soltar: (bloque, fechaDia, actividad) => {
          if (ocupado) return
          const dia = estado.dias.find(d => d.fecha === fechaDia)
          const posicion = dia?.visitas.findIndex(v => v.candidato.lugar.id === bloque.candidato?.lugar.id) ?? -1
          if (dia) mover(dia, actividad, posicion)
        },
      }} />
      <aside className="editor-barra" aria-label="Cambios del itinerario">
        <div className="editor-barra-resumen"><strong>{ocupado ? operacion : pendiente ? 'Cambios sin guardar' : 'Tu viaje está guardado'}</strong>
          <span>{conflictos.length ? `${conflictos.length} ${conflictos.length === 1 ? 'conflicto pendiente' : 'conflictos pendientes'}. Revisa las visitas señaladas.` : pendiente ? 'Revisa el resultado y guarda cuando esté listo.' : 'Haz un cambio en cualquier visita para empezar.'}</span>
        </div>
        {globales.length > 0 && <div className="editor-globales">{avisos(globales)}</div>}
        {conflictos.length > 0 && <div className="editor-dias-conflictos">{[...new Set(conflictos.flatMap(i => i.dia ? [i.dia] : []))].map(dia => <button className="boton" type="button" key={dia} onClick={() => { setFecha(dia); zona.current?.querySelector('.itinerario-navegacion')?.scrollIntoView({ block: 'start', behavior: 'smooth' }) }}>Revisar {formatoFecha(dia)}</button>)}</div>}
        {advertencias.length > 0 && <label className="editor-aceptacion"><input type="checkbox" checked={aceptarAvisos} disabled={ocupado} onChange={e => setAceptarAvisos(e.target.checked)} />He revisado los avisos y acepto guardar, incluidos los obligatorios incumplidos si los hay.</label>}
        <div className="editor-barra-acciones">
          <button className="boton" type="button" disabled={ocupado} onClick={cerrar}>{pendiente ? 'Descartar cambios' : 'Volver a Mis viajes'}</button>
          {incidencias.some(i => ['CONSULTA_RUTAS_FALLIDA', 'RUTA_NO_DISPONIBLE'].includes(i.codigo)) && <button className="boton" type="button" disabled={ocupado} onClick={() => {
            void ejecutar('Reintentando los recorridos…', async signal => { const datos = await recalcularEdicion(viaje.resumen.id, estado, signal); if (!signal.aborted) actualizar(datos, 'Recorridos comprobados de nuevo.') })
          }}>Reintentar recorridos</button>}
          <button className="boton boton-primario" type="button" disabled={ocupado || !pendiente || conflictos.length > 0 || advertencias.length > 0 && !aceptarAvisos} onClick={guardar}>Guardar cambios</button>
        </div>
      </aside>
    </>}
    {!estado && <button className="boton" type="button" onClick={onCerrar}>Volver a Mis viajes</button>}
  </section>
}

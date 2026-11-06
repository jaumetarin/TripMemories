import { Component, lazy, Suspense, useEffect, useId, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { ItinerarioGenerado, BloqueItinerario, DiaItinerario, CandidatoEvaluado, OpcionComida } from '../api'
import { horaLocal as hora, visitasDelDia } from './visitasItinerario'
import type { VisitaMapa } from './visitasItinerario'
import './ItinerarioPorDias.css'

const MapaItinerario = lazy(() => import('./MapaItinerario'))

class MapaProtegido extends Component<{ children: ReactNode; onVerLista: () => void }, { error: boolean }> {
  state = { error: false }
  static getDerivedStateFromError() { return { error: true } }
  render() {
    return this.state.error ? <div className="itinerario-aviso" role="alert">
      <p>No se ha podido mostrar el mapa. Puedes consultar todas las visitas en la lista.</p>
      <button type="button" onClick={this.props.onVerLista}>Ver lista</button>
    </div> : this.props.children
  }
}

interface Props {
  itinerario: ItinerarioGenerado
  destinoNombre: string
}

const nombresTipo: Record<BloqueItinerario['tipo'], string> = {
  VISITA: 'Visita', TRASLADO: 'Traslado', TIEMPO_LIBRE: 'Tiempo libre',
  COMIDA: 'Comida', DESCANSO: 'Descanso', MARGEN: 'Margen',
}

function fechaLocal(fecha: string): string {
  const partes = fecha.split('-')
  return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : fecha
}

function enlaceSeguro(valor: string | null): string | null {
  if (!valor) return null
  try {
    const url = new URL(valor)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null
  } catch {
    return null
  }
}

function Fuente({ valor, texto = 'Consultar fuente' }: { valor: string | null; texto?: string }) {
  const url = enlaceSeguro(valor)
  return url ? <a href={url} target="_blank" rel="noopener noreferrer">{texto}</a> : valor ? <span>{valor}</span> : null
}

function SobreLugar({ candidato }: { candidato: CandidatoEvaluado }) {
  const descripcion = candidato.descripcion?.trim()
  const fuente = enlaceSeguro(candidato.fuenteDescripcion ?? null)
  return <div className="visita-sobre">
    <h5>Sobre este lugar</h5>
    {descripcion && fuente ? <>
      <p>{descripcion}</p>
      <p className="visita-descripcion-fuente">Extracto de una fuente externa · <Fuente valor={fuente} texto="Consultar descripción" /></p>
    </> : <p className="visita-descripcion-ausente">Todavía no hay una descripción disponible de este lugar.</p>}
  </div>
}

function DetallesVisita({ candidato }: { candidato: CandidatoEvaluado }) {
  const coste: Record<CandidatoEvaluado['coste'], string> = {
    FREE: 'Gratis', LOW: 'Bajo', MEDIUM: 'Medio', HIGH: 'Alto', UNKNOWN: 'Desconocido',
  }
  const confianza: Record<CandidatoEvaluado['confianzaCoste'], string> = {
    CONFIRMED: 'Confirmado', ESTIMATED: 'Estimado', UNKNOWN: 'Sin confirmar',
  }
  const horario: Record<CandidatoEvaluado['estadoHorario'], string> = {
    VERIFIED: 'Verificado', PARTIAL: 'Parcial', UNVERIFIED: 'Sin verificar',
  }
  const precios = [candidato.precioMinimo, candidato.precioMaximo]
  const precio = precios[0] !== null && precios[1] !== null
    ? precios[0] === precios[1] ? `${precios[0]}` : `${precios[0]}–${precios[1]}`
    : precios[0] !== null ? `Desde ${precios[0]}` : precios[1] !== null ? `Hasta ${precios[1]}` : null
  const fuentes = candidato.fuentes.map(enlaceSeguro).filter((url): url is string => url !== null)
  return <details className="itinerario-detalles">
    <summary>Datos prácticos de la visita</summary>
    <div className="itinerario-detalles-contenido">
      <dl className="visita-datos">
        <div><dt>Duración estimada</dt><dd>≈ {candidato.duracionMinutos} min</dd></div>
        <div><dt>Afinidad con tus intereses</dt><dd>{candidato.afinidad}<small> / 100</small></dd></div>
        <div><dt>Coste</dt><dd>{coste[candidato.coste]}<small>{confianza[candidato.confianzaCoste]}</small></dd></div>
        {precio !== null && <div><dt>Precio disponible</dt><dd>{precio}{candidato.moneda ? ` ${candidato.moneda}` : ' (moneda no indicada)'}</dd></div>}
      </dl>
      <div className="visita-horario">
        <h5>Horario de apertura</h5>
        <p>{candidato.horario || 'No disponible'}</p>
        <span className={`visita-estado${candidato.estadoHorario === 'VERIFIED' ? ' visita-estado-verificado' : ''}`}>{horario[candidato.estadoHorario]}</span>
        {candidato.horarioFuente && <p className="visita-fuente">Fuente: <Fuente valor={candidato.horarioFuente} texto="Consultar horario" /></p>}
      </div>
      {candidato.motivo && <div className="visita-motivo"><h5>Por qué se propone</h5><p>{candidato.motivo}</p></div>}
      {candidato.informacion && <div className="visita-notas"><h5>Información adicional</h5><p>{candidato.informacion}</p></div>}
      <p className="visita-fuente">Evaluación: {candidato.origenEvaluacion === 'GEMINI' ? 'Gemini' : 'estimación local'}</p>
      {fuentes.length > 0 && <p className="visita-fuente">Fuentes: {fuentes.map((url, indice) => <span key={`${url}-${indice}`}>{indice > 0 && ' · '}<Fuente valor={url} texto={`Fuente ${indice + 1}`} /></span>)}</p>}
    </div>
  </details>
}

function Restaurante({ opcion }: { opcion: OpcionComida }) {
  return <div className="itinerario-restaurante"><strong>{opcion.nombre}</strong>
    {opcion.direccion && <p>{opcion.direccion}</p>}
    {opcion.distanciaMetros != null && opcion.duracionSegundos != null && <p className="comida-trayecto">
      Desde la visita anterior: {opcion.distanciaMetros < 1000 ? `${Math.round(opcion.distanciaMetros)} m`
        : `${(opcion.distanciaMetros / 1000).toLocaleString('es-ES', { maximumFractionDigits: 1 })} km`}
      {' · '}{Math.ceil(opcion.duracionSegundos / 60)} min a pie
    </p>}
    <p>Horario: {opcion.horario || 'Pendiente de confirmar'}</p>
    {opcion.horarioFuente && <p>Fuente del horario: <Fuente valor={opcion.horarioFuente} texto="Consultar fuente" /></p>}
  </div>
}

function Bloque({ bloque, nombres, visita, seleccionada, onSeleccionar }: {
  bloque: BloqueItinerario
  nombres: ReadonlyMap<string, string>
  visita?: VisitaMapa
  seleccionada: boolean
  onSeleccionar: (clave: string) => void
}) {
  const origen = bloque.origenId ? nombres.get(bloque.origenId) : null
  const destino = bloque.destinoId ? nombres.get(bloque.destinoId) : null
  return <li data-tipo={bloque.tipo} className={`itinerario-bloque${visita ? ' itinerario-visita' : ''}${seleccionada ? ' itinerario-visita-activa' : ''}`}>
    <div className="itinerario-horas"><time dateTime={bloque.inicio}>{hora(bloque.inicio)}</time><span aria-hidden="true"> – </span><time dateTime={bloque.fin}>{hora(bloque.fin)}</time></div>
    <div className="itinerario-bloque-contenido">
      <p className="itinerario-tipo">{visita && <span className="visita-numero" aria-hidden="true">{visita.numero}</span>}{nombresTipo[bloque.tipo]}
        {visita && bloque.candidato?.etiqueta && <span className="visita-categoria">{bloque.candidato.etiqueta}</span>}</p>
      {visita ? <>
        <h4><button type="button" className="visita-seleccionar" data-visita={visita.clave}
          aria-pressed={seleccionada} aria-label={`Visita ${visita.numero}: ${visita.nombre}. ${visita.posicion ? 'Ver en el mapa' : 'Ubicación no disponible en el mapa'}`}
          onClick={() => onSeleccionar(visita.clave)}>{visita.nombre}</button></h4>
        <p className="visita-direccion"><span>Dirección</span>{visita.direccion || 'Dirección no disponible'}</p>
        <p className="visita-accion">{visita.posicion ? 'Ver en el mapa ↗' : 'Ubicación no disponible en el mapa'}</p>
      </> : bloque.candidato && <h4>{bloque.candidato.lugar.nombre}</h4>}
      {bloque.descripcion && bloque.tipo !== 'COMIDA' && <p>{bloque.descripcion}</p>}
      {bloque.condicionada && bloque.tipo !== 'COMIDA' && <p className="itinerario-condicion">Propuesta condicionada: confirma el horario o la disponibilidad antes de ir.</p>}
      {visita && bloque.candidato && <SobreLugar candidato={bloque.candidato} />}
      {bloque.tipo === 'TRASLADO' && <>
        <p>{origen && destino ? `De ${origen} a ${destino}` : 'Traslado a pie entre actividades'}</p>
        <p>{bloque.distanciaMetros !== null && <>{bloque.distanciaMetros < 1000
          ? `${Math.round(bloque.distanciaMetros)} m`
          : `${(bloque.distanciaMetros / 1000).toLocaleString('es-ES', { maximumFractionDigits: 1 })} km`}</>}
          {bloque.distanciaMetros !== null && bloque.duracionSegundos !== null && ' · '}
          {bloque.duracionSegundos !== null && <>{Math.ceil(bloque.duracionSegundos / 60)} min</>}
          {bloque.fuente && <> · Fuente: {bloque.fuente}</>}
          {bloque.distanciaMetros === null && bloque.duracionSegundos === null && !bloque.fuente && 'Datos del trayecto no disponibles'}</p>
      </>}
      {bloque.candidato && <DetallesVisita candidato={bloque.candidato} />}
      {bloque.tipo === 'COMIDA' && <div className="itinerario-comida">
        {bloque.restaurante ? <><p className="comida-recomendacion">Un sitio para comer</p><Restaurante opcion={bloque.restaurante} /></>
          : <p>Pausa para comer. Puedes elegir un restaurante por la zona.</p>}
        {bloque.restaurante && bloque.alternativasComida.length > 0 && <details><summary>Otras opciones cercanas ({Math.min(3, bloque.alternativasComida.length)})</summary><ul>{bloque.alternativasComida.slice(0, 3).map((opcion, indice) => <li key={`${opcion.id}-${indice}`}><Restaurante opcion={opcion} /></li>)}</ul>
          <p>Si eliges otra opción, revisa los tiempos de desplazamiento del plan.</p>
        </details>}
        {bloque.restaurante && <p className="comida-confirmacion">Sugerencia sin reserva. Confirma el horario y la disponibilidad antes de ir.</p>}
      </div>}
    </div>
  </li>
}

function ContenidoDia({ dia, nombres, visitas, seleccionada, onSeleccionar }: {
  dia: DiaItinerario
  nombres: ReadonlyMap<string, string>
  visitas: VisitaMapa[]
  seleccionada: string | null
  onSeleccionar: (clave: string) => void
}) {
  const tieneVentana = dia.inicioDisponible !== null && dia.finDisponible !== null
  const porClave = new Map(visitas.map(visita => [visita.clave, visita]))
  return <div className="itinerario-dia" role="region" aria-label={`Plan del ${fechaLocal(dia.fecha)}`} aria-live="polite">
    <h3>Plan del {fechaLocal(dia.fecha)}</h3>
    {!tieneVentana ? <p>No hay tiempo disponible para actividades este día.</p>
      : dia.bloques.length > 0 ? <ol className="itinerario-bloques">{[...dia.bloques].sort((a, b) => a.inicio.localeCompare(b.inicio)).map((bloque, indice) => {
        const clave = bloque.tipo === 'VISITA' && bloque.candidato ? JSON.stringify([bloque.inicio, bloque.candidato.lugar.id]) : ''
        return <Bloque key={`${bloque.inicio}-${indice}`} bloque={bloque} nombres={nombres} visita={porClave.get(clave)}
          seleccionada={clave === seleccionada} onSeleccionar={onSeleccionar} />
      })}</ol>
        : <p>No hay bloques programados para este día.</p>}
  </div>
}

export default function ItinerarioPorDias({ itinerario, destinoNombre }: Props) {
  const [fechaSeleccionada, setFechaSeleccionada] = useState<string | null>(null)
  const [seleccion, setSeleccion] = useState<{ dia: DiaItinerario; clave: string } | null>(null)
  const [vistaMovil, setVistaMovil] = useState<'lista' | 'mapa'>('lista')
  const lista = useRef<HTMLDivElement>(null)
  const botonMapa = useRef<HTMLButtonElement>(null)
  const id = useId()
  const dia = itinerario.dias.find(elemento => elemento.fecha === fechaSeleccionada) ?? itinerario.dias[0]
  const visitas = useMemo(() => dia ? visitasDelDia(dia.bloques) : [], [dia])
  // Vincular la selección al día impide reutilizarla al recibir un plan nuevo.
  const seleccionActual = seleccion?.dia === dia && visitas.some(visita => visita.clave === seleccion.clave) ? seleccion : null
  const visitaSeleccionada = visitas.find(visita => visita.clave === seleccionActual?.clave)

  function tarjeta(clave: string) {
    return [...(lista.current?.querySelectorAll<HTMLButtonElement>('[data-visita]') ?? [])]
      .find(elemento => elemento.dataset.visita === clave)
  }

  useEffect(() => {
    if (!seleccionActual) return
    const frame = requestAnimationFrame(() => {
      const elemento = tarjeta(seleccionActual.clave)
      if (elemento?.getClientRects().length) elemento.scrollIntoView({
        block: 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
      })
    })
    return () => cancelAnimationFrame(frame)
  }, [seleccionActual, vistaMovil])

  function seleccionar(clave: string, origen: 'lista' | 'mapa') {
    if (!dia) return
    setSeleccion({ dia, clave })
    if (origen === 'lista' && visitas.find(visita => visita.clave === clave)?.posicion
      && window.matchMedia('(max-width: 800px)').matches) {
      setVistaMovil('mapa')
      requestAnimationFrame(() => botonMapa.current?.focus())
    }
  }

  function verTarjeta(clave: string) {
    seleccionar(clave, 'mapa')
    setVistaMovil('lista')
    requestAnimationFrame(() => tarjeta(clave)?.focus({ preventScroll: true }))
  }

  const nombres = new Map(itinerario.candidatos.map(candidato => [candidato.lugar.id, candidato.lugar.nombre]))
  itinerario.dias.forEach(d => d.bloques.forEach(bloque => {
    if (bloque.restaurante) nombres.set(bloque.restaurante.id, bloque.restaurante.nombre)
  }))
  return <section className="itinerario-por-dias" aria-labelledby="itinerario-titulo">
    <h2 id="itinerario-titulo">Tu itinerario en {destinoNombre}</h2>
    <p>Las horas corresponden a la hora local del destino.</p>
    {(itinerario.parcial || itinerario.advertencias.length > 0) && <aside className="itinerario-aviso itinerario-consejos" aria-label="Antes de tu viaje">
      <h3>Antes de tu viaje</h3>
      {itinerario.parcial && <p className="itinerario-plan-parcial">El plan puede dejar tiempo libre o algunos lugares fuera. Revisa las visitas de cada día.</p>}
      {itinerario.advertencias.length > 0 && <ul>{[...new Set(itinerario.advertencias)].map(aviso => <li key={aviso}>{aviso}</li>)}</ul>}
    </aside>}
    {dia ? <>
      <nav className="itinerario-navegacion" aria-label="Seleccionar día del itinerario">{itinerario.dias.map((elemento, indice) => <button type="button" key={elemento.fecha}
        aria-pressed={elemento.fecha === dia.fecha} onClick={() => { setFechaSeleccionada(elemento.fecha); setSeleccion(null) }}>
        Día {indice + 1}<span>{fechaLocal(elemento.fecha)}</span></button>)}</nav>
      <nav className="itinerario-vistas-movil" aria-label="Vista del día">
        <button type="button" aria-pressed={vistaMovil === 'lista'} aria-controls={`${id}-lista`} onClick={() => setVistaMovil('lista')}>Lista</button>
        <button ref={botonMapa} type="button" aria-pressed={vistaMovil === 'mapa'} aria-controls={`${id}-mapa`} onClick={() => setVistaMovil('mapa')}>Mapa</button>
      </nav>
      <p className="itinerario-seleccion" role="status">{visitaSeleccionada
        ? `Visita ${visitaSeleccionada.numero} seleccionada: ${visitaSeleccionada.nombre}.${visitaSeleccionada.posicion ? '' : ' Ubicación no disponible en el mapa.'}`
        : 'Selecciona una visita en la lista o en el mapa para localizarla.'}</p>
      <div className="itinerario-distribucion" data-vista-movil={vistaMovil}>
        <div id={`${id}-lista`} ref={lista} className="itinerario-lista-panel">
          <ContenidoDia key={dia.fecha} dia={dia} nombres={nombres} visitas={visitas} seleccionada={seleccionActual?.clave ?? null}
            onSeleccionar={clave => seleccionar(clave, 'lista')} />
        </div>
        <section id={`${id}-mapa`} className="itinerario-mapa-panel" aria-labelledby={`${id}-mapa-titulo`}>
          <h3 id={`${id}-mapa-titulo`}>Visitas del {fechaLocal(dia.fecha)}</h3>
          <p className="itinerario-mapa-ayuda">Los números corresponden al orden de las visitas de este día.</p>
          <MapaProtegido key={dia.fecha} onVerLista={() => setVistaMovil('lista')}>
            <Suspense fallback={<p role="status" className="mapa-cargando">Cargando mapa… La lista sigue disponible.</p>}>
              <MapaItinerario key={dia.fecha} visitas={visitas} seleccion={seleccionActual}
                onSeleccionar={clave => seleccionar(clave, 'mapa')} onVerTarjeta={verTarjeta} />
            </Suspense>
          </MapaProtegido>
        </section>
      </div>
    </> : <p>No hay días disponibles en el itinerario.</p>}
  </section>
}

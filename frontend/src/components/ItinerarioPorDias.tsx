import { useState } from 'react'
import type { ItinerarioGenerado, BloqueItinerario, DiaItinerario, CandidatoEvaluado, OpcionComida } from '../api'
import './ItinerarioPorDias.css'

interface Props {
  itinerario: ItinerarioGenerado
  destinoNombre: string
}

const nombresTipo: Record<BloqueItinerario['tipo'], string> = {
  VISITA: 'Visita', TRASLADO: 'Traslado', TIEMPO_LIBRE: 'Tiempo libre',
  COMIDA: 'Comida', DESCANSO: 'Descanso', MARGEN: 'Margen',
}

function hora(fechaHora: string): string {
  return fechaHora.includes('T') ? fechaHora.split('T')[1].slice(0, 5) : fechaHora.slice(0, 5)
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
    <summary>Detalles de la visita</summary>
    <div className="itinerario-detalles-contenido">
      <p>Afinidad: {candidato.afinidad} · Duración estimada: {candidato.duracionMinutos} min</p>
      <p>Coste: {coste[candidato.coste]} ({confianza[candidato.confianzaCoste]}){precio !== null && <> · Precio: {precio}{candidato.moneda ? ` ${candidato.moneda}` : ' (moneda no indicada)'}</>}</p>
      <p>Horario: {horario[candidato.estadoHorario]}{candidato.horario && ` · ${candidato.horario}`}{candidato.horarioFuente && <> · Fuente: <Fuente valor={candidato.horarioFuente} texto="Fuente del horario" /></>}</p>
      <p>Procedencia de la evaluación: {candidato.origenEvaluacion === 'GEMINI' ? 'Gemini' : 'estimación local'}</p>
      {candidato.motivo && <p>{candidato.motivo}</p>}
      {candidato.informacion && <p>{candidato.informacion}</p>}
      {candidato.lugar.direccion && <p>{candidato.lugar.direccion}</p>}
      {fuentes.length > 0 && <p>Fuentes: {fuentes.map((url, indice) => <span key={`${url}-${indice}`}>{indice > 0 && ' · '}<Fuente valor={url} texto={`Fuente ${indice + 1}`} /></span>)}</p>}
    </div>
  </details>
}

function Restaurante({ opcion }: { opcion: OpcionComida }) {
  return <div className="itinerario-restaurante"><strong>{opcion.nombre}</strong>
    {opcion.direccion && <p>{opcion.direccion}</p>}
    {opcion.horario && <p>Horario: {opcion.horario}</p>}
    {opcion.horarioFuente && <p>Fuente del horario: <Fuente valor={opcion.horarioFuente} texto="Consultar fuente" /></p>}
  </div>
}

function Bloque({ bloque, nombres }: { bloque: BloqueItinerario; nombres: ReadonlyMap<string, string> }) {
  const origen = bloque.origenId ? nombres.get(bloque.origenId) : null
  const destino = bloque.destinoId ? nombres.get(bloque.destinoId) : null
  return <li className="itinerario-bloque">
    <div className="itinerario-horas"><time dateTime={bloque.inicio}>{hora(bloque.inicio)}</time><span aria-hidden="true"> – </span><time dateTime={bloque.fin}>{hora(bloque.fin)}</time></div>
    <div className="itinerario-bloque-contenido">
      <p className="itinerario-tipo">{nombresTipo[bloque.tipo]}</p>
      {bloque.candidato && <h4>{bloque.candidato.lugar.nombre}</h4>}
      {bloque.descripcion && <p>{bloque.descripcion}</p>}
      {bloque.condicionada && <p className="itinerario-condicion">Propuesta condicionada: confirma el horario o la disponibilidad antes de ir.</p>}
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
        {bloque.restaurante && <><p>Restaurante propuesto</p><Restaurante opcion={bloque.restaurante} /></>}
        {!bloque.restaurante && <p>Comida sin restaurante seleccionado; el posible desvío no tiene ruta calculada.</p>}
        {bloque.alternativasComida.length > 0 && <details><summary>Alternativas ({Math.min(3, bloque.alternativasComida.length)})</summary><ul>{bloque.alternativasComida.slice(0, 3).map((opcion, indice) => <li key={`${opcion.id}-${indice}`}><Restaurante opcion={opcion} /></li>)}</ul></details>}
        <p>Propuestas sin reserva; consulta disponibilidad y horario.</p>
      </div>}
    </div>
  </li>
}

function ContenidoDia({ dia, nombres }: { dia: DiaItinerario; nombres: ReadonlyMap<string, string> }) {
  const tieneVentana = dia.inicioDisponible !== null && dia.finDisponible !== null
  return <div className="itinerario-dia" role="region" aria-label={`Plan del ${fechaLocal(dia.fecha)}`} aria-live="polite">
    <h3>Plan del {fechaLocal(dia.fecha)}</h3>
    {!tieneVentana ? <p>No hay tiempo disponible para actividades este día.</p>
      : dia.bloques.length > 0 ? <ol className="itinerario-bloques">{[...dia.bloques].sort((a, b) => a.inicio.localeCompare(b.inicio)).map((bloque, indice) => <Bloque key={`${bloque.inicio}-${indice}`} bloque={bloque} nombres={nombres} />)}</ol>
        : <p>No hay bloques programados para este día.</p>}
  </div>
}

export default function ItinerarioPorDias({ itinerario, destinoNombre }: Props) {
  const [fechaSeleccionada, setFechaSeleccionada] = useState<string | null>(null)
  const dia = itinerario.dias.find(elemento => elemento.fecha === fechaSeleccionada) ?? itinerario.dias[0]
  const nombres = new Map(itinerario.candidatos.map(candidato => [candidato.lugar.id, candidato.lugar.nombre]))
  itinerario.dias.forEach(d => d.bloques.forEach(bloque => {
    if (bloque.restaurante) nombres.set(bloque.restaurante.id, bloque.restaurante.nombre)
  }))
  return <section className="itinerario-por-dias" aria-labelledby="itinerario-titulo">
    <h2 id="itinerario-titulo">Tu itinerario en {destinoNombre}</h2>
    <p>Las horas corresponden a la hora local del destino.</p>
    {itinerario.parcial && <p className="itinerario-aviso" role="status">El itinerario es parcial y puede no cubrir toda la disponibilidad.</p>}
    {itinerario.advertencias.length > 0 && <div className="itinerario-aviso"><strong>Ten en cuenta</strong><ul>{itinerario.advertencias.map((aviso, indice) => <li key={`${aviso}-${indice}`}>{aviso}</li>)}</ul></div>}
    {dia ? <><nav className="itinerario-navegacion" aria-label="Seleccionar día del itinerario">{itinerario.dias.map((elemento, indice) => <button type="button" key={elemento.fecha} aria-pressed={elemento.fecha === dia.fecha} onClick={() => setFechaSeleccionada(elemento.fecha)}>Día {indice + 1}<span>{fechaLocal(elemento.fecha)}</span></button>)}</nav><ContenidoDia key={dia.fecha} dia={dia} nombres={nombres} /></> : <p>No hay días disponibles en el itinerario.</p>}
  </section>
}

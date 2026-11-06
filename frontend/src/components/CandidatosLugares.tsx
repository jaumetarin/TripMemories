import type { CandidatoEvaluado, CosteCandidato } from '../api'
import './CandidatosLugares.css'

const costes: Record<CosteCandidato, string> = {
  FREE: 'Gratis', LOW: 'Bajo', MEDIUM: 'Medio', HIGH: 'Alto', UNKNOWN: 'Desconocido',
}

function enlaceSeguro(valor: string): boolean {
  try { return ['http:', 'https:'].includes(new URL(valor).protocol) } catch { return false }
}

function mostrarPrecio(candidato: CandidatoEvaluado): string | null {
  const { precioMinimo, precioMaximo, moneda } = candidato
  if (precioMinimo === null && precioMaximo === null) return null
  const numero = (valor: number) => new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(valor)
  const rango = precioMinimo !== null && precioMaximo !== null && precioMinimo !== precioMaximo
    ? `${numero(precioMinimo)}–${numero(precioMaximo)}`
    : numero(precioMinimo ?? precioMaximo!)
  return `${rango}${moneda ? ` ${moneda}` : ' (moneda desconocida)'}`
}

export function CandidatosLugares({ candidatos, fallback, programados }: {
  candidatos: CandidatoEvaluado[]
  fallback: boolean
  programados: ReadonlySet<string>
}) {
  return <section className="candidatos-lugares" aria-labelledby="titulo-candidatos">
    <h2 id="titulo-candidatos">Lugares considerados</h2>
    <p>Estos lugares se evaluaron para generar el itinerario. Los que no aparecen en el plan pueden haber quedado fuera por tiempo, rutas u horarios.</p>
    <p role="status" aria-live="polite">{candidatos.length === 0
      ? 'No se evaluaron lugares para esta disponibilidad.'
      : `${candidatos.length} lugares evaluados.`}</p>
    {fallback && <p className="candidatos-aviso">La evaluación ha utilizado una alternativa local. Algunos datos pueden ser menos precisos.</p>}
    {candidatos.length > 0 && <p id="explicacion-duracion">Las duraciones son estimaciones y pueden variar durante la visita.</p>}
      <ul className="candidatos-lista">
        {candidatos.map(candidato => {
          const precio = mostrarPrecio(candidato)
          return <li className="candidato-tarjeta" key={candidato.lugar.id}
            data-programado={programados.has(candidato.lugar.id)}>
          <h3>{candidato.lugar.nombre}</h3>
          <p className="candidato-programacion">{programados.has(candidato.lugar.id) ? 'Incluido en el itinerario' : 'No programado'}</p>
          <p>{candidato.lugar.direccion || 'Dirección no disponible'}</p>
          <p className="candidato-etiqueta">{candidato.etiqueta}</p>
          <p><strong>Afinidad:</strong> {candidato.afinidad}/100</p>
          <p>{candidato.motivo}</p>
          <p><strong>Origen de la evaluación:</strong> {candidato.origenEvaluacion === 'LOCAL' ? 'Alternativa local' : 'Gemini'}</p>
          <p aria-describedby="explicacion-duracion"><strong>Duración estimada:</strong> ≈ {candidato.duracionMinutos} min</p>
          <p><strong>Coste:</strong> {costes[candidato.coste]} ({candidato.confianzaCoste === 'CONFIRMED' ? 'confirmado' : candidato.confianzaCoste === 'ESTIMATED' ? 'estimado' : 'sin estimación'})</p>
          {precio && <p><strong>Precio disponible:</strong> {precio}</p>}
          <p className="candidato-horario"><strong>Horario:</strong> {candidato.horario || 'Desconocido'}
            {candidato.horario && ` (${candidato.estadoHorario === 'VERIFIED' ? 'verificado' : candidato.estadoHorario === 'PARTIAL' ? 'información parcial' : 'sin verificar'})`}
            {candidato.horarioFuente && ` · Fuente: ${candidato.horarioFuente}`}</p>
          {candidato.informacion && <p>{candidato.informacion}</p>}
          {candidato.fuentes.filter(enlaceSeguro).length > 0 && <p className="candidato-fuentes"><strong>Fuentes:</strong>{' '}
            {candidato.fuentes.filter(enlaceSeguro).map((fuente, i) => <span key={fuente}>
              {i > 0 && ' · '}<a href={fuente} target="_blank" rel="noreferrer">Fuente {i + 1}</a>
            </span>)}
          </p>}
        </li>})}
      </ul>
      <p className="candidatos-atribucion">Lugares de <a href="https://www.geoapify.com/" target="_blank" rel="noreferrer">Geoapify</a>
        {' · © '}<a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a></p>
  </section>
}

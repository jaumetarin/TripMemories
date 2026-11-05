import { useEffect, useRef, useState } from 'react'
import { evaluarCandidatos } from '../api'
import type { CandidatoEvaluado, ConfiguracionItinerario, CosteCandidato, EvaluacionCandidatos } from '../api'
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

export function CandidatosLugares({ configuracion }: { configuracion: ConfiguracionItinerario }) {
  const [resultado, setResultado] = useState<EvaluacionCandidatos | null>(null)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')
  const peticion = useRef<AbortController | null>(null)
  useEffect(() => () => { peticion.current?.abort() }, [])

  async function buscar() {
    if (peticion.current || resultado !== null) return
    const controlador = new AbortController()
    peticion.current = controlador
    setCargando(true)
    setError('')
    try {
      const evaluacion = await evaluarCandidatos(configuracion, controlador.signal)
      if (!controlador.signal.aborted) setResultado(evaluacion)
    } catch (fallo) {
      if (controlador.signal.aborted) return
      setError(fallo instanceof Error && fallo.name === 'TimeoutError'
        ? 'La búsqueda está tardando demasiado. Vuelve a intentarlo.'
        : fallo instanceof Error && fallo.name === 'Error' ? fallo.message
        : 'No se puede conectar con el backend. Vuelve a intentarlo.')
    } finally {
      if (!controlador.signal.aborted) {
        peticion.current = null
        setCargando(false)
      }
    }
  }

  return <section className="candidatos-lugares" aria-labelledby="titulo-candidatos" aria-busy={cargando}>
    <h2 id="titulo-candidatos">Lugares para tu viaje</h2>
    <p>Lugares que coinciden con tus intereses y exclusiones según la información disponible.</p>
    {resultado === null && <button className="boton boton-primario" type="button" disabled={cargando} onClick={buscar}>
      {cargando ? 'Buscando y evaluando lugares…' : error ? 'Reintentar búsqueda de lugares' : 'Buscar lugares disponibles'}
    </button>}
    <p role="status" aria-live="polite">{cargando ? 'Buscando y evaluando lugares…' : resultado === null ? ''
      : resultado.candidatos.length === 0 ? 'No se han encontrado lugares con estas preferencias.'
        : `${resultado.candidatos.length} lugares encontrados.`}</p>
    {error && <p className="error-viaje" role="alert">{error}</p>}
    {resultado !== null && <>
      {resultado.fallback && <p className="candidatos-aviso" role="alert">La evaluación ha utilizado una alternativa local. Algunos datos pueden ser menos precisos.</p>}
      {resultado.advertencias.map((advertencia, i) => <p className="candidatos-aviso" key={i}>{advertencia}</p>)}
      {resultado.candidatos.length > 0 && <p id="explicacion-duracion">Las duraciones son estimaciones y pueden variar durante la visita.</p>}
      <ul className="candidatos-lista">
        {resultado.candidatos.map(candidato => {
          const precio = mostrarPrecio(candidato)
          return <li className="candidato-tarjeta" key={candidato.lugar.id}>
          <h3>{candidato.lugar.nombre}</h3>
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
    </>}
  </section>
}

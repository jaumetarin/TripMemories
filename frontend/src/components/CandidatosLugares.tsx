import { useEffect, useRef, useState } from 'react'
import { prepararCandidatos } from '../api'
import type { ConfiguracionItinerario, Lugar } from '../api'
import './CandidatosLugares.css'

export function CandidatosLugares({ configuracion }: { configuracion: ConfiguracionItinerario }) {
  const [lugares, setLugares] = useState<Lugar[] | null>(null)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState('')
  const peticion = useRef<AbortController | null>(null)
  useEffect(() => () => { peticion.current?.abort() }, [])

  async function buscar() {
    if (peticion.current || lugares !== null) return
    const controlador = new AbortController()
    peticion.current = controlador
    setCargando(true)
    setError('')
    try {
      const resultado = await prepararCandidatos(configuracion, controlador.signal)
      if (!controlador.signal.aborted) setLugares(resultado)
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
    {lugares === null && <button className="boton boton-primario" type="button" disabled={cargando} onClick={buscar}>
      {cargando ? 'Buscando lugares…' : error ? 'Reintentar búsqueda de lugares' : 'Buscar lugares disponibles'}
    </button>}
    <p role="status" aria-live="polite">{cargando ? 'Buscando lugares…' : lugares === null ? ''
      : lugares.length === 0 ? 'No se han encontrado lugares con estas preferencias.' : `${lugares.length} lugares encontrados.`}</p>
    {error && <p className="error-viaje" role="alert">{error}</p>}
    {lugares !== null && <>
      <ul className="candidatos-lista">
        {lugares.map(lugar => <li className="candidato-tarjeta" key={lugar.id}>
          <h3>{lugar.nombre}</h3><p>{lugar.direccion || 'Dirección no disponible'}</p>
          {lugar.horario && <p className="candidato-horario"><strong>Horario publicado:</strong> {lugar.horario}</p>}
        </li>)}
      </ul>
      <p className="candidatos-atribucion">Lugares de <a href="https://www.geoapify.com/" target="_blank" rel="noreferrer">Geoapify</a>
        {' · © '}<a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a></p>
    </>}
  </section>
}

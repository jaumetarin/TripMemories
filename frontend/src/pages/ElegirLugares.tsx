import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { buscarLugares, cambiarSeleccionLugar, completarCatalogoLugares } from '../api'
import type { ConfiguracionItinerario, EstadoLugar, Lugar, SeleccionLugar, RepartoCategoriaLugares } from '../api'
import MapaSeleccionLugares from '../components/MapaSeleccionLugares'
import { PantallaCarga } from '../components/PantallaCarga'
import { estadosLugar as estados, etiquetasEstado, simbolosEstado } from '../components/estadosLugar'
import '../components/PrioridadLugar.css'
import './ElegirLugares.css'

type Props = {
  configuracion: ConfiguracionItinerario
  onActualizar: (selecciones: SeleccionLugar[], avisos: string[]) => void
  onAtras: () => void
  onContinuar: () => void
}

function explicarError(error: unknown) {
  return error instanceof Error && error.name === 'TimeoutError'
    ? 'La búsqueda está tardando demasiado. Tus selecciones se conservan; vuelve a intentarlo.'
    : error instanceof Error && error.name === 'Error' ? error.message
    : 'No se puede conectar con el backend. Tus selecciones se conservan; vuelve a intentarlo.'
}

export function ElegirLugares({ configuracion, onActualizar, onAtras, onContinuar }: Props) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState<Lugar[]>([])
  const [activo, setActivo] = useState<string | null>(null)
  const [vista, setVista] = useState<'lista' | 'mapa'>('lista')
  const [cargando, setCargando] = useState(true)
  const [modificando, setModificando] = useState(false)
  const [error, setError] = useState('')
  const [mensaje, setMensaje] = useState('')
  const [limite, setLimite] = useState<number | null>(null)
  const [hayMas, setHayMas] = useState(false)
  const [catalogoCompleto, setCatalogoCompleto] = useState(false)
  const [limiteResultados, setLimiteResultados] = useState(30)
  const [reparto, setReparto] = useState<RepartoCategoriaLugares[]>([])
  const [categoriasMuestra, setCategoriasMuestra] = useState<Record<string, string>>({})
  const [reintento, setReintento] = useState(0)
  const peticion = useRef<AbortController | null>(null)
  const cambio = useRef<AbortController | null>(null)
  const tarjetas = useRef(new Map<string, HTMLElement>())
  const clave = JSON.stringify(configuracion)

  const recibir = useCallback((datos: Awaited<ReturnType<typeof buscarLugares>>) => {
    setResultados(datos.lugares)
    setLimite(datos.limiteSelecciones)
    setHayMas(datos.hayMas)
    setCatalogoCompleto(datos.catalogoCompleto)
    setReparto(datos.reparto)
    setCategoriasMuestra(datos.categoriasMuestra)
    onActualizar(datos.selecciones, datos.avisos)
  }, [onActualizar])

  // Al escribir se filtra el catálogo de la sesión. Buscar más requiere una acción explícita.
  useEffect(() => {
    const controlador = new AbortController()
    peticion.current = controlador
    const espera = window.setTimeout(() => {
      setCargando(true)
      setError('')
      buscarLugares(JSON.parse(clave) as ConfiguracionItinerario, texto, false, controlador.signal, limiteResultados)
        .then(datos => { if (!controlador.signal.aborted) recibir(datos) })
        .catch(error => { if (!controlador.signal.aborted) setError(explicarError(error)) })
        .finally(() => { if (!controlador.signal.aborted) setCargando(false) })
    }, 280)
    return () => { window.clearTimeout(espera); controlador.abort() }
  }, [clave, texto, reintento, limiteResultados, recibir])

  useEffect(() => () => { peticion.current?.abort(); cambio.current?.abort() }, [])

  async function ampliar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (cambio.current || cargando || texto.trim().length < 3) return
    peticion.current?.abort()
    const controlador = new AbortController()
    peticion.current = controlador
    setCargando(true)
    setError('')
    try {
      const datos = await buscarLugares(configuracion, texto, true, controlador.signal)
      if (!controlador.signal.aborted) {
        recibir(datos)
        setLimiteResultados(30)
        setMensaje(`Búsqueda ampliada completada: ${datos.lugares.length}${datos.hayMas ? ' o más' : ''} coincidencias compatibles.`)
      }
    } catch (error) {
      if (!controlador.signal.aborted) setError(explicarError(error))
    } finally { if (!controlador.signal.aborted) setCargando(false) }
  }

  async function cambiar(lugar: Lugar, estado: EstadoLugar | null) {
    if (cambio.current) return
    peticion.current?.abort()
    const controlador = new AbortController()
    cambio.current = controlador
    setModificando(true)
    setCargando(false)
    setError('')
    try {
      const datos = await cambiarSeleccionLugar(configuracion, lugar.id, estado, controlador.signal)
      if (controlador.signal.aborted) return
      onActualizar(datos.selecciones, datos.avisos)
      setMensaje(estado ? `${lugar.nombre}: ${etiquetasEstado[estado]}.` : `Se ha retirado la selección de ${lugar.nombre}.`)
      window.requestAnimationFrame(() => {
        const tarjeta = tarjetas.current.get(lugar.id)
        if (tarjeta) tarjeta.focus({ preventScroll: true })
        else document.getElementById('buscar-lugar')?.focus({ preventScroll: true })
      })
    } catch (error) {
      if (!controlador.signal.aborted) setError(explicarError(error))
    } finally { cambio.current = null; if (!controlador.signal.aborted) setModificando(false) }
  }

  async function cargarMas() {
    if (cargando || modificando) return
    peticion.current?.abort()
    const controlador = new AbortController()
    peticion.current = controlador
    setCargando(true)
    setError('')
    setMensaje('')
    try {
      const datos = await completarCatalogoLugares(configuracion, texto, controlador.signal)
      if (!controlador.signal.aborted) {
        recibir(datos)
        setLimiteResultados(60)
        setMensaje('Búsqueda ampliada. Se han incorporado los lugares compatibles encontrados.')
      }
    } catch (error) {
      if (!controlador.signal.aborted) setError(explicarError(error))
    } finally { if (!controlador.signal.aborted) setCargando(false) }
  }

  const lugaresMapa = useMemo(() => [...new Map([
    ...resultados.map(l => [l.id, l] as const), ...configuracion.selecciones.map(s => [s.lugar.id, s.lugar] as const),
  ]).values()], [resultados, configuracion.selecciones])
  const seleccionados = new Map(configuracion.selecciones.map(s => [s.lugar.id, s.estado]))
  const restantes = resultados.filter(l => !seleccionados.has(l.id))

  function verTarjeta(id: string) {
    setActivo(id)
    setVista('lista')
    window.requestAnimationFrame(() => {
      tarjetas.current.get(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      tarjetas.current.get(id)?.focus({ preventScroll: true })
    })
  }

  function tarjeta(lugar: Lugar) {
    const estado = seleccionados.get(lugar.id)
    const sinUbicacion = lugar.latitud === null || lugar.longitud === null
    return <article key={lugar.id} tabIndex={-1} ref={nodo => { if (nodo) tarjetas.current.set(lugar.id, nodo); else tarjetas.current.delete(lugar.id) }}
      className={`lugar-tarjeta${activo === lugar.id ? ' lugar-activo' : ''}`}>
      <div className="lugar-cabecera"><h3>{lugar.nombre}</h3>
        {estado && <span className={`prioridad prioridad-etiqueta ${estado}`}><span aria-hidden="true">{simbolosEstado[estado]}</span>{etiquetasEstado[estado]}</span>}
      </div>
      <p className="lugar-direccion">{lugar.direccion || 'Dirección no disponible'}</p>
      {categoriasMuestra[lugar.id] && <p className="lugar-categoria">{reparto.find(r => r.codigo === categoriasMuestra[lugar.id])?.nombre}</p>}
      <div className="lugar-estados" role="group" aria-label={`Prioridad de ${lugar.nombre}`}>
        {estados.map(opcion => <button key={opcion} type="button" className={`prioridad ${opcion}`} aria-pressed={estado === opcion}
          disabled={modificando || sinUbicacion} onClick={() => cambiar(lugar, opcion)}>
          <span aria-hidden="true">{simbolosEstado[opcion]}</span> {etiquetasEstado[opcion]}</button>)}
      </div>
      <div className="lugar-enlaces"><button type="button" disabled={sinUbicacion} onClick={() => { setActivo(lugar.id); setVista('mapa') }}>Ver en el mapa</button>
        {estado && <button type="button" disabled={modificando} onClick={() => cambiar(lugar, null)}>Retirar selección</button>}</div>
      {sinUbicacion && <p>No conocemos su ubicación exacta; no se puede seleccionar.</p>}
    </article>
  }

  if (cargando && limite === null && !error) return <div className="elegir-lugares">
    <PantallaCarga tipo="lugares" destino={configuracion.destino.nombre} />
    <div className="revision-acciones lugares-carga-acciones"><button type="button" className="boton" onClick={onAtras}>← Atrás</button></div>
  </div>

  return <div className="elegir-lugares" aria-busy={modificando}>
    <div className="lugares-explicacion"><p><strong>Tu viaje, tus imprescindibles.</strong> Este paso es opcional. Solo se muestran lugares compatibles con tus intereses y exclusiones.</p>
      <p><strong>Obligatorio:</strong> debe estar en tu itinerario. Si no encaja, te pediremos ajustar el viaje. <strong>Preferido:</strong> tendrá prioridad si hay tiempo. <strong>Evitado:</strong> no aparecerá en el itinerario.</p>
      <p>Empezamos con una muestra equilibrada de tus intereses, combinando distintos tipos de lugares. Puedes buscar un nombre o cargar más lugares. Al generar el plan completaremos la búsqueda necesaria.</p></div>
    <form className="lugares-buscador" onSubmit={ampliar}>
      <label htmlFor="buscar-lugar">Buscar lugares en {configuracion.destino.nombre}</label>
      <div><input id="buscar-lugar" value={texto} maxLength={100} type="search" autoComplete="off"
        placeholder="Por ejemplo, museo o nombre de un monumento" disabled={modificando}
        onChange={e => { peticion.current?.abort(); setTexto(e.target.value); setLimiteResultados(30); setCargando(true) }} />
        <button type="submit" disabled={cargando || modificando || texto.trim().length < 3}>Buscar más lugares</button></div>
      <p>Al escribir se filtran los lugares ya encontrados. Si no aparece el que buscas, escribe su nombre completo y busca más (mínimo tres caracteres).</p>
    </form>
    <p role="status" className="lugares-estado">{modificando ? 'Actualizando selección…' : mensaje}</p>
    {cargando && <PantallaCarga tipo="lugares" destino={configuracion.destino.nombre} compacta />}
    {error && <div role="alert" className="error-viaje"><p>{error}</p><button type="button" disabled={cargando || modificando} onClick={() => { setCargando(true); setReintento(n => n + 1) }}>Reintentar</button></div>}
    <div className="lugares-pestanas" aria-label="Vista de lugares">
      <button type="button" aria-pressed={vista === 'lista'} onClick={() => setVista('lista')}>Lista</button>
      <button type="button" aria-pressed={vista === 'mapa'} onClick={() => setVista('mapa')}>Mapa</button>
    </div>
    <div className="lugares-distribucion" aria-busy={cargando || modificando}>
      <div className={`lugares-lista${vista === 'mapa' ? ' lugares-oculta-movil' : ''}`}>
        <h2>Tu selección ({configuracion.selecciones.length}{limite !== null ? `/${limite}` : ''})</h2>
        {configuracion.selecciones.length ? [...configuracion.selecciones]
          .sort((a, b) => estados.indexOf(a.estado) - estados.indexOf(b.estado)).map(s => tarjeta(s.lugar))
          : <p className="lugares-vacio">Aún no has marcado ningún lugar. Puedes continuar y dejar que te propongamos las visitas.</p>}
        <h2>Descubre lugares</h2>
        {reparto.length > 0 && <div className="lugares-reparto" aria-label="Reparto por intereses">
          <p>Tu selección se mantiene aparte. En esta muestra:</p>
          <ul>{reparto.map(r => <li key={r.codigo}><span>{r.nombre}</span><strong>{r.mostrados} / {r.objetivo}</strong></li>)}</ul>
          {reparto.some(r => r.mostrados < r.objetivo) && <p role="status">No hemos encontrado suficientes lugares para completar la muestra de {reparto.filter(r => r.mostrados < r.objetivo).map(r => r.nombre).join(', ')}.
            {' '}{catalogoCompleto ? 'Puedes buscar un lugar concreto por nombre.' : 'Puedes cargar más lugares o buscar por nombre.'} No sustituimos sus huecos por otras categorías.</p>}
        </div>}
        {restantes.map(tarjeta)}
        {!cargando && !error && !restantes.length && <p className="lugares-vacio">{texto
          ? 'No hay más coincidencias. Prueba otro nombre o pulsa «Buscar más lugares».'
          : catalogoCompleto ? 'No hay más lugares disponibles con estas preferencias.'
          : 'La muestra inicial no contiene más lugares compatibles. Puedes buscar por nombre o cargar más lugares de tus intereses.'}</p>}
        {hayMas && <p>Mostramos {resultados.length} lugares de esta búsqueda. Puedes mostrar más o escribir un nombre para acotarla.</p>}
        {hayMas && limiteResultados < 150 && <button type="button" className="boton lugares-cargar-mas" disabled={cargando || modificando}
          onClick={() => { setCargando(true); setLimiteResultados(n => Math.min(150, n + 30)) }}>Mostrar más resultados encontrados</button>}
        {!catalogoCompleto && <button type="button" className="boton lugares-cargar-mas" disabled={cargando || modificando} onClick={cargarMas}>
          {cargando ? 'Buscando lugares…' : 'Cargar más lugares de mis intereses'}</button>}
      </div>
      <aside className={`lugares-mapa${vista === 'lista' ? ' lugares-oculta-movil' : ''}`} aria-label="Mapa de lugares y prioridades">
        <h2>Explora en el mapa</h2>
        <div className="lugares-leyenda" aria-label="Prioridades en el mapa">{estados.map(estado =>
          <span key={estado} className={`prioridad prioridad-etiqueta ${estado}`}><span aria-hidden="true">{simbolosEstado[estado]}</span>{etiquetasEstado[estado]}</span>)}<span>+ Sin seleccionar</span></div>
        {error && !lugaresMapa.length ? <div className="mapa-estado-vacio"><p>No se han podido cargar los lugares en el mapa.</p><p>Pulsa «Reintentar» para volver a buscarlos.</p></div>
          : <MapaSeleccionLugares lugares={lugaresMapa} selecciones={configuracion.selecciones} activo={activo}
            onActivar={setActivo} onVerTarjeta={verTarjeta} />}
        <p>Los marcadores muestran los resultados visibles y tu selección. Todavía no representan un recorrido.</p>
        {activo && <button type="button" onClick={() => verTarjeta(activo)}>Ver tarjeta del lugar destacado</button>}
      </aside>
    </div>
    <div className="revision-acciones"><button type="button" className="boton" disabled={modificando} onClick={onAtras}>← Atrás</button>
      <button type="button" className="boton boton-primario" disabled={modificando || cargando} onClick={onContinuar}>Revisar mi plan →</button></div>
  </div>
}

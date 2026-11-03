import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { obtenerCatalogoPreferencias, validarFechaHora } from '../api'
import type { CategoriaPreferencia, Destino, FechaHora } from '../api'
import { Icon } from '../assets/Icon'
import { BuscadorDestinos } from '../components/BuscadorDestinos'
import { CampoFecha } from '../components/CampoFecha'
import { PreferenciasGasto } from './PreferenciasGasto'
import type { DatosPreferencias } from './PreferenciasGasto'
import './CrearItinerario.css'

type CrearItinerarioProps = {
  onVolver: () => void
}

const etapas = ['Destino y disponibilidad', 'Preferencias y gasto', 'Elegir lugares', 'Revisar y generar']

// Unimos los campos sin convertirlos a UTC: son horas locales del destino.
function unirFechaHora(fecha: string, hora: string): string | null {
  return fecha && hora ? `${fecha}T${hora}` : null
}

function mostrarFechaHora(valor: string) {
  const [fecha, hora] = valor.split('T')
  const [anio, mes, dia] = fecha.split('-')
  return `${dia}/${mes}/${anio.slice(-2)}, ${hora.slice(0, 5)}`
}

export function CrearItinerario({ onVolver }: CrearItinerarioProps) {
  const [destinoSeleccionado, setDestinoSeleccionado] = useState<Destino | null>(null)
  const [fechaLlegada, setFechaLlegada] = useState('')
  const [horaLlegada, setHoraLlegada] = useState('10:00')
  const [fechaSalida, setFechaSalida] = useState('')
  const [horaSalida, setHoraSalida] = useState('18:00')
  const [alojamiento, setAlojamiento] = useState('')
  const [disponibilidad, setDisponibilidad] = useState<FechaHora | null>(null)
  const [paso, setPaso] = useState<'destino' | 'preferencias' | 'resumen'>('destino')
  const [preferencias, setPreferencias] = useState<DatosPreferencias>({
    intereses: [], categoriasExcluidas: [], subcategoriasExcluidas: [],
    restricciones: [], nivelGasto: 'Sin preferencia', distanciaKm: 3,
  })
  const [catalogo, setCatalogo] = useState<CategoriaPreferencia[]>([])
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true)
  const [errorCatalogo, setErrorCatalogo] = useState('')
  const [reintentoCatalogo, setReintentoCatalogo] = useState(0)
  const [errorDisponibilidad, setErrorDisponibilidad] = useState('')
  const [errorDestino, setErrorDestino] = useState('')
  const [validando, setValidando] = useState(false)
  const peticion = useRef<AbortController | null>(null)
  const resumen = useRef<HTMLElement>(null)
  const errores = useRef<HTMLDivElement>(null)
  const inicio = useRef<HTMLInputElement>(null)
  const titulo = useRef<HTMLHeadingElement>(null)

  useEffect(() => () => peticion.current?.abort(), [])

  useEffect(() => {
    const controlador = new AbortController()
    let vigente = true
    obtenerCatalogoPreferencias(controlador.signal)
      .then(categorias => { if (vigente) setCatalogo(categorias) })
      .catch(error => {
        if (!vigente) return
        setErrorCatalogo(error instanceof Error && error.name === 'Error'
          ? error.message : 'No se puede cargar el catálogo. Comprueba la conexión con el backend y vuelve a intentarlo.')
      })
      .finally(() => { if (vigente) setCargandoCatalogo(false) })
    return () => { vigente = false; controlador.abort() }
  }, [reintentoCatalogo])

  useEffect(() => {
    if (paso === 'resumen') resumen.current?.focus()
    if (paso === 'preferencias') titulo.current?.focus()
  }, [paso])

  useEffect(() => {
    if (!validando && (errorDisponibilidad || errorDestino)) errores.current?.focus()
  }, [validando, errorDisponibilidad, errorDestino])

  async function continuar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (peticion.current) return

    setErrorDisponibilidad('')
    setErrorDestino(destinoSeleccionado ? '' : 'Selecciona un destino de la lista de sugerencias.')
    const controlador = new AbortController()
    peticion.current = controlador
    setValidando(true)

    try {
      // El backend compara las fechas y horas y calcula la duración inclusiva.
      const resultado = await validarFechaHora(
        unirFechaHora(fechaLlegada, horaLlegada),
        unirFechaHora(fechaSalida, horaSalida),
        controlador.signal
      )
      if (!controlador.signal.aborted && destinoSeleccionado) {
        setDisponibilidad(resultado)
        setPaso('preferencias')
      }
    } catch (error) {
      if (controlador.signal.aborted) return
      let mensaje = 'No se puede conectar con el backend. Comprueba que esté arrancado e inténtalo de nuevo.'
      if (error instanceof Error && error.name === 'TimeoutError') {
        mensaje = 'La validación está tardando demasiado. Vuelve a intentarlo.'
      } else if (error instanceof Error && error.name === 'Error') {
        mensaje = error.message
      }
      setErrorDisponibilidad(mensaje)
    } finally {
      peticion.current = null
      if (!controlador.signal.aborted) setValidando(false)
    }
  }

  function editarDisponibilidad() {
    setPaso('destino')
    window.requestAnimationFrame(() => inicio.current?.focus())
  }

  function reintentarCatalogo() {
    setCargandoCatalogo(true)
    setErrorCatalogo('')
    setReintentoCatalogo(valor => valor + 1)
  }

  function nombreCategoria(codigo: string) {
    return catalogo.find(categoria => categoria.codigo === codigo)?.nombre ?? codigo
  }

  function nombreSubcategoria(codigo: string) {
    return catalogo.flatMap(categoria => categoria.subcategorias)
      .find(subcategoria => subcategoria.codigo === codigo)?.nombre ?? codigo
  }

  return (
    <section className={`crear-viaje${paso === 'preferencias' ? ' crear-viaje-preferencias' : ''}`} aria-labelledby="crear-titulo">
      <button className="boton-volver" type="button" onClick={onVolver}>← Volver al inicio</button>
      <nav className="acompanamiento" aria-label="Etapas del itinerario">
        <ol className="pasos">
          {etapas.map((etapa, indice) => {
            const completado = paso === 'resumen' ? indice < 2 : paso === 'preferencias' && indice === 0
            const actual = (paso === 'destino' && indice === 0) || (paso === 'preferencias' && indice === 1)
            return <li key={etapa} aria-current={actual ? 'step' : undefined} data-completado={completado || undefined}>
              <span aria-hidden="true">{completado ? '✓' : indice + 1}</span><strong>{etapa}{completado && <span className="solo-lectores">, completado</span>}</strong>
            </li>
          })}
        </ol>
      </nav>
      <div className="crear-intro">
        <p className="antetitulo">{paso === 'resumen' ? 'TU VIAJE, EN MARCHA' : `PASO ${paso === 'destino' ? 1 : 2} DE 4`}</p>
        <h1 ref={titulo} tabIndex={-1} id="crear-titulo">{paso === 'destino' ? '¿Dónde empieza tu próxima historia?' : paso === 'preferencias' ? 'Preferencias y gasto' : 'Tu viaje empieza a tomar forma.'}</h1>
        <p className="subtitulo">{paso === 'destino' ? 'Elige el destino y el tiempo que tendrás para descubrirlo.' : paso === 'preferencias' ? 'Cuéntanos cómo te gusta viajar para darle forma a tu itinerario.' : 'Revisa el destino, tu disponibilidad y tus preferencias.'}</p>
      </div>
      <div className="crear-distribucion" hidden={paso === 'preferencias'}>
        <form className="formulario-viaje" noValidate onSubmit={continuar} hidden={paso !== 'destino'} aria-busy={validando}>
          <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="ubicacion" /></span><div><h2>Tu viaje</h2><p>Destino, fechas y horas en un solo lugar.</p></div></div>
          <fieldset className="campos-viaje" disabled={validando}>
            <legend className="solo-lectores">Destino y disponibilidad del viaje</legend>
            <BuscadorDestinos seleccionado={destinoSeleccionado} onSeleccionar={destino => {
              setDestinoSeleccionado(destino)
              setErrorDestino('')
            }} />
            <div className="disponibilidad-campos">
              <fieldset className="momento-viaje">
                <legend>Llegada</legend>
                <CampoFecha ref={inicio} id="fecha-llegada" nombre="fechaLlegada" etiqueta="Fecha de llegada"
                  valor={fechaLlegada} descripcion="ayuda-disponibilidad errores-viaje"
                  onCambiar={fecha => { setFechaLlegada(fecha); setErrorDisponibilidad('') }} />
                <label htmlFor="hora-llegada">Hora de llegada aproximada</label>
                <input id="hora-llegada" name="horaLlegada" type="time" step={60} required
                  value={horaLlegada} aria-describedby="ayuda-llegada errores-viaje"
                  onChange={evento => { setHoraLlegada(evento.target.value); setErrorDisponibilidad('') }} />
                <p className="ayuda-fechas" id="ayuda-llegada">El primer día estará disponible desde esta hora.</p>
              </fieldset>
              <fieldset className="momento-viaje">
                <legend>Salida</legend>
                <CampoFecha id="fecha-salida" nombre="fechaSalida" etiqueta="Fecha de salida"
                  valor={fechaSalida} descripcion="ayuda-disponibilidad errores-viaje"
                  onCambiar={fecha => { setFechaSalida(fecha); setErrorDisponibilidad('') }} />
                <label htmlFor="hora-salida">Hora de salida aproximada</label>
                <input id="hora-salida" name="horaSalida" type="time" step={60} required
                  value={horaSalida} aria-describedby="ayuda-salida errores-viaje"
                  onChange={evento => { setHoraSalida(evento.target.value); setErrorDisponibilidad('') }} />
                <p className="ayuda-fechas" id="ayuda-salida">El último día estará disponible hasta esta hora.</p>
              </fieldset>
            </div>
            <p className="ayuda-fechas" id="ayuda-disponibilidad">De 1 a 10 días, contando el primero y el último. La salida debe ser posterior a la llegada. Todas las horas son locales del destino.</p>
            <div className="campo-alojamiento">
              <label htmlFor="alojamiento">Dirección del alojamiento <span>(opcional)</span></label>
              <div className="alojamiento-entrada">
                <Icon name="casa" />
                <input id="alojamiento" type="text" name="alojamiento" maxLength={250}
                  placeholder="Hotel, apartamento o zona de referencia" value={alojamiento}
                  onChange={evento => setAlojamiento(evento.target.value)} aria-describedby="ayuda-alojamiento" />
              </div>
              <p className="ayuda-fechas" id="ayuda-alojamiento">Puedes dejarlo vacío si todavía no sabes dónde te alojarás.</p>
            </div>
          </fieldset>
          <div ref={errores} id="errores-viaje" className="errores-viaje" tabIndex={-1} role="alert" aria-atomic="true">
            {errorDestino && <p className="error-viaje">{errorDestino}</p>}
            {errorDisponibilidad && <p className="error-viaje">{errorDisponibilidad}</p>}
          </div>
          <p className="aviso-formulario">Comprobaremos tu disponibilidad antes de continuar. El viaje todavía no se guarda.</p>
          <button className="boton boton-primario continuar" type="submit" disabled={validando}>
            {validando ? 'Comprobando disponibilidad…' : 'Continuar'} <Icon name="flecha" />
          </button>
        </form>
        {paso === 'resumen' && disponibilidad && destinoSeleccionado && (
          <section className="formulario-viaje resumen-viaje" ref={resumen} tabIndex={-1} aria-labelledby="resumen-titulo">
            <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="maleta" /></span><div><h2 id="resumen-titulo">Configuración del viaje</h2><p>Tu disponibilidad y tus preferencias, en un mismo lugar.</p></div></div>
            <p className="destino-confirmado">{destinoSeleccionado.nombre}</p>
            <p className="region-confirmada">{[destinoSeleccionado.region, destinoSeleccionado.pais].filter(Boolean).join(', ')}</p>
            <dl className="datos-viaje">
              <div><dt>Llegada · primer día</dt><dd><time dateTime={disponibilidad.llegada}>{mostrarFechaHora(disponibilidad.llegada)}</time></dd></div>
              <div><dt>Salida · último día</dt><dd><time dateTime={disponibilidad.salida}>{mostrarFechaHora(disponibilidad.salida)}</time></dd></div>
              <div className="duracion-viaje"><dt>Duración inclusiva</dt><dd>{disponibilidad.duracionDias} {disponibilidad.duracionDias === 1 ? 'día' : 'días'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Alojamiento</dt><dd>{alojamiento.trim() || 'Sin alojamiento indicado'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Intereses</dt><dd>{preferencias.intereses.map(nombreCategoria).join(', ') || 'Sin intereses seleccionados'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Categorías completas a evitar</dt><dd>{preferencias.categoriasExcluidas.map(nombreCategoria).join(', ') || 'Sin categorías completas excluidas'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Actividades a evitar</dt><dd>{preferencias.subcategoriasExcluidas.map(nombreSubcategoria).join(', ') || 'Sin subcategorías excluidas'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Otras preferencias</dt><dd>{preferencias.restricciones.join(', ') || 'Sin otras preferencias'}</dd></div>
              <div><dt>Nivel de gasto</dt><dd>{preferencias.nivelGasto}</dd></div>
              <div><dt>Distancia entre actividades</dt><dd>{preferencias.distanciaKm} km</dd></div>
            </dl>
            <p className="ayuda-fechas">Horas locales del destino. El tiempo disponible para actividades va desde la llegada hasta la salida.</p>
            <p className="aviso-formulario">Borrador sin guardar. La selección de lugares y la generación del itinerario estarán disponibles próximamente.</p>
            <button className="boton boton-primario continuar" type="button" onClick={() => setPaso('preferencias')}>Editar preferencias y gasto</button>
            <button className="boton-volver editar-destino" type="button" onClick={editarDisponibilidad}>Editar destino, fechas y horas</button>
          </section>
        )}
      </div>
      {paso === 'preferencias' && disponibilidad && destinoSeleccionado && (
        <PreferenciasGasto destino={destinoSeleccionado} disponibilidad={disponibilidad} alojamiento={alojamiento}
          catalogo={catalogo} cargandoCatalogo={cargandoCatalogo} errorCatalogo={errorCatalogo}
          onReintentarCatalogo={reintentarCatalogo}
          datos={preferencias} onCambiar={setPreferencias} onAtras={editarDisponibilidad}
          onContinuar={confirmadas => {
            setPreferencias(actuales => ({ ...actuales, ...confirmadas }))
            setPaso('resumen')
          }} />
      )}
    </section>
  )
}

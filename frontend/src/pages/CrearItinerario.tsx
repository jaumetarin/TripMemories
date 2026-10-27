import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { validarFechas } from '../api'
import type { Destino, FechasItinerario } from '../api'
import { Icon } from '../assets/Icon'
import { BuscadorDestinos } from '../components/BuscadorDestinos'
import { HorariosAlojamiento } from './HorariosAlojamiento'
import type { DatosHorarios } from './HorariosAlojamiento'
import './CrearItinerario.css'

type CrearItinerarioProps = {
  onVolver: () => void
}

const etapas = ['Destino y fechas', 'Horarios y alojamiento', 'Preferencias y gasto', 'Elegir lugares', 'Revisar y generar']

export function CrearItinerario({ onVolver }: CrearItinerarioProps) {
  const [destinoSeleccionado, setDestinoSeleccionado] = useState<Destino | null>(null)
  const [fechaInicio, setFechaInicio] = useState('')
  const [fechaFin, setFechaFin] = useState('')
  const [fechasValidadas, setFechasValidadas] = useState<FechasItinerario | null>(null)
  const [paso, setPaso] = useState<'destino' | 'horarios' | 'resumen'>('destino')
  const [datosHorarios, setDatosHorarios] = useState<DatosHorarios>({ horaLlegada: '10:00', horaSalida: '18:00', alojamiento: '' })
  const [horariosConfirmados, setHorariosConfirmados] = useState<DatosHorarios | null>(null)
  const [errorFechas, setErrorFechas] = useState('')
  const [errorDestino, setErrorDestino] = useState('')
  const [validando, setValidando] = useState(false)
  const peticion = useRef<AbortController | null>(null)
  const resumen = useRef<HTMLElement>(null)
  const inicio = useRef<HTMLInputElement>(null)

  useEffect(() => () => peticion.current?.abort(), [])

  useEffect(() => {
    if (paso === 'resumen') resumen.current?.focus()
  }, [paso])

  async function continuar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (peticion.current) return

    setErrorFechas('')
    setErrorDestino(destinoSeleccionado ? '' : 'Selecciona un destino de la lista de sugerencias.')
    const controlador = new AbortController()
    peticion.current = controlador
    setValidando(true)

    try {
      // La duración y las reglas del intervalo se comprueban siempre en el backend.
      const fechas = await validarFechas(fechaInicio, fechaFin, controlador.signal)
      if (!controlador.signal.aborted && destinoSeleccionado) {
        setFechasValidadas(fechas)
        setHorariosConfirmados(null)
        setPaso('horarios')
      }
    } catch (error) {
      if (controlador.signal.aborted) return
      let mensaje = 'No se puede conectar con el backend. Comprueba que esté arrancado e inténtalo de nuevo.'
      if (error instanceof Error && error.name === 'TimeoutError') {
        mensaje = 'La validación está tardando demasiado. Vuelve a intentarlo.'
      } else if (error instanceof Error && error.name === 'Error') {
        mensaje = error.message
      }
      setErrorFechas(mensaje)
    } finally {
      peticion.current = null
      if (!controlador.signal.aborted) setValidando(false)
    }
  }

  function volverAlCalendario() {
    setPaso('destino')
    window.requestAnimationFrame(() => inicio.current?.focus())
  }

  function mostrarFecha(fecha: string) {
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'long' })
      .format(new Date(`${fecha}T12:00:00`))
  }

  return (
    <section className={`crear-viaje${paso === 'horarios' ? ' crear-viaje-amplio' : ''}`} aria-labelledby="crear-titulo">
      <button className="boton-volver" type="button" onClick={onVolver}>← Volver al inicio</button>
      <nav className="acompanamiento" aria-label="Etapas del itinerario">
        <ol className="pasos">
          {etapas.map((etapa, indice) => {
            const numero = indice + 1
            const completado = paso === 'resumen' ? numero <= 2 : paso === 'horarios' && numero === 1
            const actual = paso !== 'resumen' && numero === (paso === 'destino' ? 1 : 2)
            return <li key={etapa} aria-current={actual ? 'step' : undefined} data-completado={completado || undefined}>
              <span aria-hidden="true">{completado ? '✓' : numero}</span><strong>{etapa}{completado && <span className="solo-lectores">, completado</span>}</strong>
            </li>
          })}
        </ol>
      </nav>
      <div className={`crear-intro${paso === 'horarios' ? ' crear-intro-horarios' : ''}`}>
        <p className="antetitulo">{paso === 'resumen' ? 'TU VIAJE, EN MARCHA' : `PASO ${paso === 'destino' ? 1 : 2} DE 5`}</p>
        <h1 id="crear-titulo">{paso === 'destino' ? 'Empecemos por el destino.' : paso === 'horarios' ? 'Horarios y alojamiento' : 'Tu viaje empieza a tomar forma.'}</h1>
        <p className="subtitulo">{paso === 'destino' ? 'Los grandes viajes empiezan con unos pequeños detalles.' : paso === 'horarios' ? 'Usaremos estos horarios para adaptar el primer y el último día.' : 'Ya tenemos el destino, las fechas y tus horarios.'}</p>
      </div>
      <div className="crear-distribucion" hidden={paso === 'horarios'}>
        <form className="formulario-viaje" noValidate onSubmit={continuar} hidden={paso !== 'destino'} aria-busy={validando}>
          <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="ubicacion" /></span><div><h2>Tu viaje</h2><p>Elige dónde y cuándo.</p></div></div>
          <fieldset className="campos-viaje" disabled={validando}>
            <legend className="solo-lectores">Destino y fechas del viaje</legend>
            <BuscadorDestinos seleccionado={destinoSeleccionado} onSeleccionar={destino => {
              setDestinoSeleccionado(destino)
              setErrorDestino('')
            }} />
            {errorDestino && <p className="error-viaje" role="alert">{errorDestino}</p>}
            <div className="fechas">
              <div>
                <label htmlFor="inicio">Fecha de inicio</label>
                <input ref={inicio} id="inicio" name="fechaInicio" type="date" required
                  value={fechaInicio} aria-describedby="ayuda-fechas error-fechas"
                  onChange={evento => { setFechaInicio(evento.target.value); setErrorFechas('') }} />
              </div>
              <div>
                <label htmlFor="fin">Fecha de fin</label>
                <input id="fin" name="fechaFin" type="date" required
                  value={fechaFin} aria-describedby="ayuda-fechas error-fechas"
                  onChange={evento => { setFechaFin(evento.target.value); setErrorFechas('') }} />
              </div>
            </div>
            <p className="ayuda-fechas" id="ayuda-fechas">De 1 a 10 días, contando tanto el día de inicio como el de fin. Puedes elegir el mismo día en ambos campos.</p>
          </fieldset>
          <p className="error-viaje" id="error-fechas" role="alert">{errorFechas}</p>
          <p className="aviso-formulario">Después elegiremos tus horarios y alojamiento. El viaje todavía no se guarda.</p>
          <button className="boton boton-primario continuar" type="submit" disabled={validando}>
            {validando ? 'Comprobando fechas…' : 'Continuar'} <Icon name="flecha" />
          </button>
        </form>
        {paso === 'resumen' && fechasValidadas && destinoSeleccionado && horariosConfirmados && (
          <section className="formulario-viaje resumen-viaje" ref={resumen} tabIndex={-1} aria-labelledby="resumen-titulo">
            <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="maleta" /></span><div><h2 id="resumen-titulo">Horarios y alojamiento confirmados</h2><p>Este es el comienzo de tu itinerario.</p></div></div>
            <p className="destino-confirmado">{destinoSeleccionado.nombre}</p>
            <p className="region-confirmada">{[destinoSeleccionado.region, destinoSeleccionado.pais].filter(Boolean).join(', ')}</p>
            <dl className="datos-viaje">
              <div><dt>Inicio</dt><dd><time dateTime={fechasValidadas.fechaInicio}>{mostrarFecha(fechasValidadas.fechaInicio)}</time></dd></div>
              <div><dt>Fin</dt><dd><time dateTime={fechasValidadas.fechaFin}>{mostrarFecha(fechasValidadas.fechaFin)}</time></dd></div>
              <div className="duracion-viaje"><dt>Duración inclusiva</dt><dd>{fechasValidadas.duracionDias} {fechasValidadas.duracionDias === 1 ? 'día' : 'días'}</dd></div>
              <div><dt>Llegada el primer día</dt><dd>{horariosConfirmados.horaLlegada || 'Sin indicar'}</dd></div>
              <div><dt>Salida el último día</dt><dd>{horariosConfirmados.horaSalida || 'Sin indicar'}</dd></div>
              <div className="alojamiento-confirmado"><dt>Alojamiento</dt><dd>{horariosConfirmados.alojamiento || 'Sin alojamiento indicado'}</dd></div>
            </dl>
            <p className="aviso-formulario">Borrador sin guardar. El siguiente paso será elegir tus preferencias y presupuesto.</p>
            <button className="boton boton-primario continuar" type="button" onClick={() => setPaso('horarios')}>Editar horarios y alojamiento</button>
            <button className="boton-volver volver-calendario" type="button" onClick={volverAlCalendario}>Volver al calendario</button>
          </section>
        )}
      </div>
      {paso === 'horarios' && fechasValidadas && destinoSeleccionado && (
        <HorariosAlojamiento destino={destinoSeleccionado} fechas={fechasValidadas} datos={datosHorarios}
          onCambiar={setDatosHorarios} onAtras={volverAlCalendario} onConfirmar={horarios => {
            setHorariosConfirmados(horarios)
            setDatosHorarios({ horaLlegada: horarios.horaLlegada, horaSalida: horarios.horaSalida, alojamiento: horarios.alojamiento })
            setPaso('resumen')
          }} />
      )}
    </section>
  )
}

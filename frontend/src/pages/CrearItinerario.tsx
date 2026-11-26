import { useCallback, useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { ErrorObligatoriosPendientes } from '../api'
import { confirmarConfiguracion, generarItinerario, obtenerConsumoGeneracion, obtenerCatalogoPreferencias, obtenerOpcionesGastoDistancia, validarFechaHora, revisarSeleccionLugares } from '../api'
import type { Alojamiento, CategoriaPreferencia, ConfiguracionItinerario, ConsumoGeneracion, Destino, FechaHora, ItinerarioGenerado, OpcionesGastoDistancia, Preferencias, RitmoDiario, SeleccionLugar } from '../api'
import { Icon } from '../assets/Icon'
import { BuscadorDestinos } from '../components/BuscadorDestinos'
import { BuscadorAlojamientos } from '../components/BuscadorAlojamientos'
import { CandidatosLugares } from '../components/CandidatosLugares'
import { CampoFecha } from '../components/CampoFecha'
import ItinerarioPorDias from '../components/ItinerarioPorDias'
import { PreferenciasGasto } from './PreferenciasGasto'
import { RevisarPlan } from './RevisarPlan'
import { ElegirLugares } from './ElegirLugares'
import type { CampoPlan } from './RevisarPlan'
import { GuardarViaje } from '../components/GuardarViaje'
import type { DestinoGuardado, ViajeGuardado } from '../viajes'
import './CrearItinerario.css'

type CrearItinerarioProps = {
  onVolver: () => void
  onVerViajes: () => void
  viajeInicial?: ViajeGuardado
}

const etapas = ['Destino y disponibilidad', 'Preferencias y gasto', 'Elegir lugares', 'Revisar y generar', 'Tu itinerario']

// Unimos los campos sin convertirlos a UTC: son horas locales del destino.
function unirFechaHora(fecha: string, hora: string): string | null {
  return fecha && hora ? `${fecha}T${hora}` : null
}
function obtenerFechaActual(): string {
  const ahora = new Date()
  const anio = ahora.getFullYear()
  const mes = String(ahora.getMonth() + 1).padStart(2, '0')
  const dia = String(ahora.getDate()).padStart(2, '0')

  return `${anio}-${mes}-${dia}`
}

const selectoresEdicion: Record<CampoPlan, string> = {
  destino: '#editar-destino input[role="combobox"]',
  fechas: '#fecha-llegada',
  horarios: '#hora-llegada',
  alojamiento: '#alojamiento',
  intereses: '#editar-intereses',
  exclusiones: '#editar-exclusiones',
  gasto: '#editar-gasto',
  distancia: '#distancia-actividades',
  ritmo: '#editar-ritmo',
  lugares: '#buscar-lugar',
}

export function CrearItinerario({ onVolver, onVerViajes, viajeInicial }: CrearItinerarioProps) {
  const inicial = viajeInicial?.configuracion
  const [destinoGuardado, setDestinoGuardado] = useState<DestinoGuardado | undefined>(viajeInicial
    ? { id: viajeInicial.resumen.id, revision: viajeInicial.resumen.revision, titulo: viajeInicial.resumen.titulo } : undefined)
  const [destinoSeleccionado, setDestinoSeleccionado] = useState<Destino | null>(inicial?.destino ?? null)
  const [fechaLlegada, setFechaLlegada] = useState(inicial?.disponibilidad.llegada.slice(0, 10) ?? '')
  const [horaLlegada, setHoraLlegada] = useState(inicial?.disponibilidad.llegada.slice(11, 16) ?? '10:00')
  const [fechaSalida, setFechaSalida] = useState(inicial?.disponibilidad.salida.slice(0, 10) ?? '')
  const [horaSalida, setHoraSalida] = useState(inicial?.disponibilidad.salida.slice(11, 16) ?? '18:00')
  const [alojamiento, setAlojamiento] = useState(inicial?.alojamiento ?? '')
  const [alojamientoLocalizado, setAlojamientoLocalizado] =
    useState<Alojamiento | null>(inicial?.alojamientoLocalizado ?? null)
  const [disponibilidad, setDisponibilidad] = useState<FechaHora | null>(inicial?.disponibilidad ?? null)
  const [paso, setPaso] = useState<'destino' | 'preferencias' | 'lugares' | 'resumen' | 'resultado'>('destino')
  const [selecciones, setSelecciones] = useState<SeleccionLugar[]>(inicial?.selecciones ?? [])
  const [avisosSeleccion, setAvisosSeleccion] = useState<string[]>([])
  const [errorSeleccion, setErrorSeleccion] = useState('')
  const [revisandoSeleccion, setRevisandoSeleccion] = useState(false)
  const revisionSeleccion = useRef<AbortController | null>(null)
  const [preferencias, setPreferencias] = useState<Preferencias>(inicial?.preferencias ?? {
    intereses: [], categoriasExcluidas: [], subcategoriasExcluidas: [],
    nivelGasto: null, distanciaKm: null,
  })
  const [ritmo, setRitmo] = useState<RitmoDiario>(inicial?.ritmo ?? {
    comidaInicio: '13:00', comidaFin: '15:00',
    descansoCadaMinutos: 120, descansoDuracionMinutos: 15, margenMinutos: 10,
  })
  const [catalogo, setCatalogo] = useState<CategoriaPreferencia[]>([])
  const [opcionesGastoDistancia, setOpcionesGastoDistancia] = useState<OpcionesGastoDistancia | null>(null)
  const [cargandoCatalogo, setCargandoCatalogo] = useState(true)
  const [errorCatalogo, setErrorCatalogo] = useState('')
  const [reintentoCatalogo, setReintentoCatalogo] = useState(0)
  const [errorDisponibilidad, setErrorDisponibilidad] = useState('')
  const [errorDestino, setErrorDestino] = useState('')
  const [errorAlojamiento, setErrorAlojamiento] = useState('')
  const [validando, setValidando] = useState(false)
  const [editandoResumen, setEditandoResumen] = useState(false)
  const [configuracionConfirmada, setConfiguracionConfirmada] = useState<ConfiguracionItinerario | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const [errorConfirmacion, setErrorConfirmacion] = useState('')
  const [itinerario, setItinerario] = useState<ItinerarioGenerado | null>(null)
  const [vistaResultado, setVistaResultado] = useState<'plan' | 'candidatos'>('plan')
  const [generando, setGenerando] = useState(false)
  const [errorGeneracion, setErrorGeneracion] = useState('')
  const [problemasObligatorios, setProblemasObligatorios] = useState<string[]>([])
  const [consumo, setConsumo] = useState<ConsumoGeneracion | null>(null)
  const [cargandoConsumo, setCargandoConsumo] = useState(true)
  const [errorConsumo, setErrorConsumo] = useState('')
  const [reintentoConsumo, setReintentoConsumo] = useState(0)
  const [resultadoReutilizado, setResultadoReutilizado] = useState(false)
  const [generacionId, setGeneracionId] = useState<string | null>(null)
  const [resultadoGuardado, setResultadoGuardado] = useState<DestinoGuardado | null>(null)
  const peticion = useRef<AbortController | null>(null)
  const peticionConfirmacion = useRef<AbortController | null>(null)
  const peticionGeneracion = useRef<AbortController | null>(null)
  const campoEdicion = useRef<CampoPlan | null>(null)
  const errores = useRef<HTMLDivElement>(null)
  const inicio = useRef<HTMLInputElement>(null)
  const titulo = useRef<HTMLHeadingElement>(null)

  useEffect(() => () => {
    peticion.current?.abort()
    peticionConfirmacion.current?.abort()
    peticionGeneracion.current?.abort()
    revisionSeleccion.current?.abort()
  }, [])

  useEffect(() => {
    if (paso !== 'resumen') return
    const controlador = new AbortController()
    obtenerConsumoGeneracion(controlador.signal).then(datos => {
      if (!controlador.signal.aborted) setConsumo(datos)
    }).catch(error => {
      if (controlador.signal.aborted) return
      setConsumo(null)
      setErrorConsumo(error instanceof Error ? error.message : 'No se puede consultar la disponibilidad.')
    }).finally(() => {
      if (!controlador.signal.aborted) setCargandoConsumo(false)
    })
    return () => controlador.abort()
  }, [paso, reintentoConsumo])

  function actualizarConsumo() {
    setCargandoConsumo(true)
    setErrorConsumo('')
    setReintentoConsumo(valor => valor + 1)
  }

  function mostrarResumen() {
    actualizarConsumo()
    setPaso('resumen')
  }

  useEffect(() => {
    const controlador = new AbortController()
    let vigente = true
    Promise.all([obtenerCatalogoPreferencias(controlador.signal), obtenerOpcionesGastoDistancia(controlador.signal)])
      .then(([categorias, opciones]) => {
        if (!vigente) return
        setCatalogo(categorias)
        setOpcionesGastoDistancia(opciones)
        // Los valores iniciales vienen del backend; un reintento nunca borra lo editado.
        setPreferencias(actuales => ({
          ...actuales,
          nivelGasto: actuales.nivelGasto ?? opciones.nivelGastoInicial,
          distanciaKm: actuales.distanciaKm ?? opciones.distanciaInicialKm,
        }))
      })
      .catch(error => {
        if (!vigente) return
        setErrorCatalogo(error instanceof Error && error.name === 'Error'
          ? error.message : 'No se pueden cargar las opciones. Comprueba la conexión con el backend y vuelve a intentarlo.')
      })
      .finally(() => { if (vigente) setCargandoCatalogo(false) })
    return () => { vigente = false; controlador.abort() }
  }, [reintentoCatalogo])

  useEffect(() => {
    if (campoEdicion.current && paso !== 'resumen') {
      document.querySelector<HTMLElement>(selectoresEdicion[campoEdicion.current])?.focus()
      campoEdicion.current = null
    } else if (paso === 'resumen' || paso === 'preferencias' || paso === 'lugares' || paso === 'resultado') {
      titulo.current?.focus()
    }
  }, [paso])

  useEffect(() => {
    if (!validando && (errorDisponibilidad || errorDestino || errorAlojamiento)) {
      errores.current?.focus()
    }
  }, [validando, errorDisponibilidad, errorDestino, errorAlojamiento])

  async function continuar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (peticion.current) return

    setErrorDisponibilidad('')
    setErrorDestino(destinoSeleccionado ? '' : 'Selecciona un destino de la lista de sugerencias.')
    setErrorAlojamiento('')
    if (fechaLlegada && fechaLlegada < obtenerFechaActual()) {
      setErrorDisponibilidad(
        'La fecha de llegada debe ser hoy o un día posterior.'
      )
      return
    }

    if (alojamiento.trim() && alojamientoLocalizado === null) {
      setErrorAlojamiento(
        'Selecciona un alojamiento de las sugerencias o deja el campo vacío.'
      )
      return
    }
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
        if (configuracionActual && selecciones.length) {
          const revisada = await revisarSeleccionLugares({ ...configuracionActual,
            destino: destinoSeleccionado, disponibilidad: resultado }, controlador.signal)
          actualizarSeleccion(revisada.selecciones, revisada.avisos)
        }
        if (controlador.signal.aborted) return
        if (editandoResumen) mostrarResumen()
        else setPaso('preferencias')
        setEditandoResumen(false)
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

  function editarApartado(campo: CampoPlan) {
    if (peticionConfirmacion.current || peticionGeneracion.current) return
    setConfiguracionConfirmada(null)
    setItinerario(null)
    setGeneracionId(null)
    setErrorConfirmacion('')
    setErrorGeneracion('')
    setEditandoResumen(true)
    setProblemasObligatorios([])
    campoEdicion.current = campo
    const esDisponibilidad = ['destino', 'fechas', 'horarios', 'alojamiento'].includes(campo)
    setPaso(campo === 'lugares' ? 'lugares' : esDisponibilidad ? 'destino' : 'preferencias')
  }

  const configuracionActual: ConfiguracionItinerario | null = destinoSeleccionado && disponibilidad ? {
    destino: destinoSeleccionado,
    disponibilidad,
    alojamiento,
    alojamientoLocalizado,
    preferencias: {
      intereses: preferencias.intereses,
      categoriasExcluidas: preferencias.categoriasExcluidas,
      subcategoriasExcluidas: preferencias.subcategoriasExcluidas,
      nivelGasto: preferencias.nivelGasto,
      distanciaKm: preferencias.distanciaKm,
    },
    ritmo,
    selecciones,
  } : null

  const actualizarSeleccion = useCallback((actuales: SeleccionLugar[], avisos: string[]) => {
    setSelecciones(anteriores => JSON.stringify(anteriores) === JSON.stringify(actuales) ? anteriores : actuales)
    if (avisos.length) setAvisosSeleccion(avisos)
    setConfiguracionConfirmada(null)
    setItinerario(null)
    setGeneracionId(null)
    setErrorConfirmacion('')
    setErrorGeneracion('')
    setProblemasObligatorios([])
  }, [])

  async function continuarPreferencias(confirmadas: Preferencias) {
    if (!configuracionActual || revisionSeleccion.current) return
    const controlador = new AbortController()
    revisionSeleccion.current = controlador
    setRevisandoSeleccion(true)
    setErrorSeleccion('')
    try {
      const nuevas = { ...preferencias, ...confirmadas }
      const revisada = await revisarSeleccionLugares({ ...configuracionActual, preferencias: nuevas }, controlador.signal)
      if (controlador.signal.aborted) return
      setPreferencias(nuevas)
      actualizarSeleccion(revisada.selecciones, revisada.avisos)
      setEditandoResumen(false)
      setPaso('lugares')
    } catch (error) {
      if (!controlador.signal.aborted) setErrorSeleccion(error instanceof Error ? error.message : 'No se pueden revisar las selecciones. Vuelve a intentarlo.')
    } finally {
      revisionSeleccion.current = null
      if (!controlador.signal.aborted) setRevisandoSeleccion(false)
    }
  }

  async function confirmarPlan() {
    if (!configuracionActual || peticionConfirmacion.current || configuracionConfirmada) return
    // La petición usa una copia de todos los datos visibles, nunca un borrador anterior.
    const enviada = structuredClone(configuracionActual)
    const controlador = new AbortController()
    peticionConfirmacion.current = controlador
    setConfirmando(true)
    setErrorConfirmacion('')
    try {
      const confirmada = await confirmarConfiguracion(enviada, controlador.signal)
      if (controlador.signal.aborted || peticionConfirmacion.current !== controlador) return
      setConfiguracionConfirmada(confirmada)
      setDestinoSeleccionado(confirmada.destino)
      setDisponibilidad(confirmada.disponibilidad)
      setFechaLlegada(confirmada.disponibilidad.llegada.split('T')[0])
      setHoraLlegada(confirmada.disponibilidad.llegada.split('T')[1].slice(0, 5))
      setFechaSalida(confirmada.disponibilidad.salida.split('T')[0])
      setHoraSalida(confirmada.disponibilidad.salida.split('T')[1].slice(0, 5))
      setAlojamiento(confirmada.alojamiento)
      setAlojamientoLocalizado(confirmada.alojamientoLocalizado)
      setPreferencias(confirmada.preferencias)
      setSelecciones(confirmada.selecciones)
      setRitmo({
        ...confirmada.ritmo,
        comidaInicio: confirmada.ritmo.comidaInicio.slice(0, 5),
        comidaFin: confirmada.ritmo.comidaFin.slice(0, 5),
      })
    } catch (error) {
      if (controlador.signal.aborted) return
      setErrorConfirmacion(error instanceof Error && error.name === 'TimeoutError'
        ? 'La confirmación está tardando demasiado. Revisa la conexión y vuelve a intentarlo.'
        : error instanceof Error && error.name === 'Error' ? error.message
        : 'No se puede conectar con el backend. Tus datos se conservan; vuelve a intentar la confirmación.')
    } finally {
      if (peticionConfirmacion.current === controlador) {
        peticionConfirmacion.current = null
        if (!controlador.signal.aborted) setConfirmando(false)
      }
    }
  }

  async function generarPlan() {
    if (!configuracionConfirmada || peticionGeneracion.current || problemasObligatorios.length) return
    if (itinerario) {
      setPaso('resultado')
      return
    }
    if (cargandoConsumo || !consumo || consumo.enCurso) return
    const controlador = new AbortController()
    peticionGeneracion.current = controlador
    setGenerando(true)
    setErrorGeneracion('')
    try {
      const generado = await generarItinerario(configuracionConfirmada, controlador.signal)
      if (controlador.signal.aborted || peticionGeneracion.current !== controlador) return
      setItinerario(generado.itinerario)
      setGeneracionId(generado.generacionId)
      setResultadoGuardado(null)
      setResultadoReutilizado(generado.reutilizado)
      setVistaResultado('plan')
      setPaso('resultado')
    } catch (error) {
      if (controlador.signal.aborted) return
      if (error instanceof ErrorObligatoriosPendientes) setProblemasObligatorios(error.problemas)
      setErrorGeneracion(error instanceof Error && error.name === 'TimeoutError'
        ? 'La generación ha tardado demasiado. Tus datos siguen confirmados; vuelve a intentarlo.'
        : error instanceof Error && error.name === 'Error' ? error.message
        : 'No se ha podido conectar con el backend. Tus datos siguen confirmados; vuelve a intentarlo.')
    } finally {
      if (peticionGeneracion.current === controlador) {
        peticionGeneracion.current = null
        if (!controlador.signal.aborted) setGenerando(false)
        if (!controlador.signal.aborted) actualizarConsumo()
      }
    }
  }

  const indiceActual = paso === 'destino' ? 0 : paso === 'preferencias' ? 1 : paso === 'lugares' ? 2 : paso === 'resumen' ? 3 : 4
  const idsProgramados = new Set(itinerario?.dias.flatMap(dia => dia.bloques
    .filter(bloque => bloque.tipo === 'VISITA' && bloque.candidato !== null)
    .map(bloque => bloque.candidato!.lugar.id)) ?? [])

  return (
    <section className={`crear-viaje${paso === 'preferencias' ? ' crear-viaje-preferencias' : paso === 'lugares' ? ' crear-viaje-lugares' : paso === 'resumen' ? ' crear-viaje-revision' : paso === 'resultado' ? ' crear-viaje-resultado' : ''}`} aria-labelledby="crear-titulo">
      <button className="boton-volver" type="button" onClick={onVolver} disabled={confirmando || generando}>← {viajeInicial ? 'Volver a Mis viajes' : 'Volver al inicio'}</button>
      {viajeInicial && <p className="resultado-nota">Estás preparando cambios para «{viajeInicial.resumen.titulo}». El viaje guardado no cambiará hasta que pulses Guardar cambios en el resultado.</p>}
      <nav className="acompanamiento" aria-label="Etapas del itinerario">
        <ol className="pasos">
          {etapas.map((etapa, indice) => {
            const completado = indice < indiceActual
            const actual = indice === indiceActual
            return <li key={etapa} aria-current={actual ? 'step' : undefined} data-completado={completado || undefined}>
              <span aria-hidden="true">{completado ? '✓' : indice + 1}</span><strong>{etapa}{completado && <span className="solo-lectores">, completado</span>}</strong>
            </li>
          })}
        </ol>
      </nav>
      <div className="crear-intro">
        <p className="antetitulo">{`PASO ${indiceActual + 1} DE ${etapas.length}`}</p>
        <h1 ref={titulo} tabIndex={-1} id="crear-titulo">{paso === 'destino' ? '¿Dónde empieza tu próxima historia?' : paso === 'preferencias' ? 'Preferencias y gasto' : paso === 'lugares' ? 'Elige tus imprescindibles' : paso === 'resumen' ? generando ? 'Tu viaje está tomando forma' : 'Revisa tu plan' : 'Tu itinerario'}</h1>
        <p className="subtitulo">{paso === 'destino' ? 'Elige el destino y el tiempo que tendrás para descubrirlo.' : paso === 'preferencias' ? 'Cuéntanos cómo te gusta viajar para darle forma a tu itinerario.' : paso === 'lugares' ? 'Busca lugares y decide cuáles quieres visitar, priorizar o evitar.' : paso === 'resumen' ? generando ? 'Estamos trabajando en tu propuesta. Tus datos siguen confirmados.' : 'Comprueba los detalles antes de generar tu itinerario. Puedes editar cualquier apartado.' : `Una propuesta para descubrir ${configuracionConfirmada?.destino.nombre ?? 'tu destino'} a tu ritmo.`}</p>
      </div>
      {avisosSeleccion.length > 0 && <div className="mapa-aviso" role="status">{avisosSeleccion.map(aviso => <p key={aviso}>{aviso}</p>)}
        <button type="button" onClick={() => setAvisosSeleccion([])}>Entendido</button></div>}
      {errorSeleccion && <p className="error-viaje" role="alert">{errorSeleccion}</p>}
      {revisandoSeleccion && <p role="status">Comprobando que tus lugares siguen siendo compatibles…</p>}
      <div className="crear-distribucion" hidden={paso !== 'destino'}>
        <form className="formulario-viaje" noValidate onSubmit={continuar} hidden={paso !== 'destino'} aria-busy={validando}>
          <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="ubicacion" /></span><div><h2>Tu viaje</h2><p>Destino, fechas y horas en un solo lugar.</p></div></div>
          <fieldset className="campos-viaje" disabled={validando}>
            <legend className="solo-lectores">Destino y disponibilidad del viaje</legend>
            <div id="editar-destino">
            <BuscadorDestinos
              seleccionado={destinoSeleccionado}
              onSeleccionar={destino => {
                if (destino?.id !== destinoSeleccionado?.id) {
                  setAlojamiento('')
                  setAlojamientoLocalizado(null)
                  setErrorAlojamiento('')
                }

                setDestinoSeleccionado(destino)
                setErrorDestino('')
              }}
            />
          </div>
            <div className="disponibilidad-campos">
              <fieldset className="momento-viaje">
                <legend>Llegada</legend>
                <CampoFecha
                    ref={inicio}
                    id="fecha-llegada"
                    nombre="fechaLlegada"
                    etiqueta="Fecha de llegada"
                    valor={fechaLlegada}
                    fechaMinima={obtenerFechaActual()}
                    descripcion="ayuda-disponibilidad errores-viaje"
                    onCambiar={fecha => {
                      setFechaLlegada(fecha)
                      setErrorDisponibilidad('')
                    }}
                  />
                <label htmlFor="hora-llegada">Hora de llegada aproximada</label>
                <input id="hora-llegada" name="horaLlegada" type="time" step={60} required
                  value={horaLlegada} aria-describedby="ayuda-llegada errores-viaje"
                  onChange={evento => { setHoraLlegada(evento.target.value); setErrorDisponibilidad('') }} />
                <p className="ayuda-fechas" id="ayuda-llegada">El primer día estará disponible desde esta hora.</p>
              </fieldset>
              <fieldset className="momento-viaje">
                <legend>Salida</legend>
                <CampoFecha
                  id="fecha-salida"
                  nombre="fechaSalida"
                  etiqueta="Fecha de salida"
                  valor={fechaSalida}
                  fechaMinima={fechaLlegada || undefined}
                  descripcion="ayuda-disponibilidad errores-viaje"
                  onCambiar={fecha => {
                    setFechaSalida(fecha)
                    setErrorDisponibilidad('')
                  }}
                />
                <label htmlFor="hora-salida">Hora de salida aproximada</label>
                <input id="hora-salida" name="horaSalida" type="time" step={60} required
                  value={horaSalida} aria-describedby="ayuda-salida errores-viaje"
                  onChange={evento => { setHoraSalida(evento.target.value); setErrorDisponibilidad('') }} />
                <p className="ayuda-fechas" id="ayuda-salida">El último día estará disponible hasta esta hora.</p>
              </fieldset>
            </div>
            <p className="ayuda-fechas" id="ayuda-disponibilidad">De 1 a 10 días, contando el primero y el último. La salida debe ser posterior a la llegada. Todas las horas son locales del destino.</p>
            <BuscadorAlojamientos
              key={destinoSeleccionado?.id ?? 'sin-destino'}
              destinoId={destinoSeleccionado?.id ?? null}
              texto={alojamiento}
              seleccionado={alojamientoLocalizado}
              activo={paso === 'destino' && !validando}
              onCambiar={(texto, seleccionado) => {
                setAlojamiento(texto)
                setAlojamientoLocalizado(seleccionado)
                setErrorAlojamiento('')
              }}
            />
          </fieldset>
          <div ref={errores} id="errores-viaje" className="errores-viaje" tabIndex={-1} role="alert" aria-atomic="true">
            {errorDestino && <p className="error-viaje">{errorDestino}</p>}
            {errorAlojamiento && <p className="error-viaje">{errorAlojamiento}</p>}
            {errorDisponibilidad && <p className="error-viaje">{errorDisponibilidad}</p>}
          </div>
          <p className="aviso-formulario">Comprobaremos tu disponibilidad antes de continuar. El viaje todavía no se guarda.</p>
          <button className="boton boton-primario continuar" type="submit" disabled={validando}>
            {validando ? 'Comprobando disponibilidad…' : editandoResumen ? 'Guardar cambios y volver al resumen' : 'Continuar'} <Icon name="flecha" />
          </button>
        </form>
      </div>
      {paso === 'resumen' && configuracionActual && (
        <RevisarPlan configuracion={configuracionConfirmada ?? configuracionActual}
          confirmada={configuracionConfirmada !== null} confirmando={confirmando} errorConfirmacion={errorConfirmacion}
          generando={generando} errorGeneracion={errorGeneracion} itinerarioDisponible={itinerario !== null}
          problemasObligatorios={problemasObligatorios}
          consumo={consumo} cargandoConsumo={cargandoConsumo} errorConsumo={errorConsumo}
          onActualizarConsumo={actualizarConsumo}
          catalogo={catalogo} opcionesGastoDistancia={opcionesGastoDistancia}
          onEditar={editarApartado} onAtras={() => editarApartado('lugares')} onConfirmar={confirmarPlan}
          onGenerar={generarPlan} />
      )}
      {itinerario && configuracionConfirmada && (
        <div className="resultado-itinerario" hidden={paso !== 'resultado'}>
          <button className="boton-volver" type="button" onClick={mostrarResumen}>← Volver al resumen</button>
          <p className="resultado-nota" role="status">{resultadoReutilizado
            ? 'Hemos recuperado tu último itinerario vigente. Esta consulta no consume otra generación.'
            : 'Tu itinerario está listo. Puedes volver a consultarlo sin gastar otra generación mientras siga vigente.'}
            {' '}Guárdala en tu cuenta para conservarla.</p>
          <GuardarViaje key={generacionId ?? 'sin-identificador'} generacionId={generacionId}
            tituloInicial={destinoGuardado?.titulo ?? `Viaje a ${configuracionConfirmada.destino.nombre}`}
            destino={destinoGuardado} onGuardado={guardado => { setDestinoGuardado(guardado); setResultadoGuardado(guardado) }}
            onVerViajes={onVerViajes} />
          <nav className="resultado-vistas" aria-label="Vistas del resultado">
            <button type="button" aria-current={vistaResultado === 'plan' ? 'page' : undefined}
              onClick={() => setVistaResultado('plan')}>Itinerario por días</button>
            <button type="button" aria-current={vistaResultado === 'candidatos' ? 'page' : undefined}
              onClick={() => setVistaResultado('candidatos')}>Lugares considerados ({itinerario.candidatos.length})</button>
          </nav>
          <div hidden={vistaResultado !== 'plan'}>
          <ItinerarioPorDias
            itinerario={itinerario}
            configuracion={configuracionConfirmada}
            origenTrazados={resultadoGuardado ? { tipo: 'viaje', id: resultadoGuardado.id, revision: resultadoGuardado.revision }
              : generacionId ? { tipo: 'generacion', id: generacionId } : undefined}
          />
          </div>
          {vistaResultado === 'candidatos' && <CandidatosLugares candidatos={itinerario.candidatos}
            fallback={itinerario.evaluacionFallback} programados={idsProgramados} />}
        </div>
      )}
      {paso === 'preferencias' && disponibilidad && destinoSeleccionado && (
        <PreferenciasGasto destino={destinoSeleccionado} disponibilidad={disponibilidad} alojamiento={alojamiento}
          catalogo={catalogo} cargandoCatalogo={cargandoCatalogo} errorCatalogo={errorCatalogo}
          opcionesGastoDistancia={opcionesGastoDistancia}
          onReintentarCatalogo={reintentarCatalogo}
          datos={preferencias} onCambiar={setPreferencias} ritmo={ritmo} onCambiarRitmo={setRitmo}
          onAtras={editarDisponibilidad}
          editandoResumen={editandoResumen}
          continuando={revisandoSeleccion}
          onContinuar={continuarPreferencias} />
      )}
      {paso === 'lugares' && configuracionActual && <ElegirLugares configuracion={configuracionActual}
        onActualizar={actualizarSeleccion} onAtras={() => { setEditandoResumen(false); setPaso('preferencias') }}
        onContinuar={() => { setEditandoResumen(false); mostrarResumen() }} />}
    </section>
  )
}

export interface Destino {
  id: string
  nombre: string
  region: string
  pais: string
  latitud: number
  longitud: number
}

export interface FechaHora {
  llegada: string
  salida: string
  duracionDias: number
}

export interface ConfiguracionItinerario {
  destino: Destino
  disponibilidad: FechaHora
  alojamiento: string
  preferencias: Preferencias
  ritmo: RitmoDiario
}

export interface RitmoDiario {
  comidaInicio: string
  comidaFin: string
  descansoCadaMinutos: number
  descansoDuracionMinutos: number
  margenMinutos: number
}

export interface Lugar {
  id: string
  nombre: string
  direccion: string | null
  latitud: number | null
  longitud: number | null
  categorias: string[]
  subcategorias: string[]
  horario?: string
}

export interface Preferencias {
  intereses: string[]
  categoriasExcluidas: string[]
  subcategoriasExcluidas: string[]
  nivelGasto: NivelGasto | null
  distanciaKm: number | null
}

export type NivelGasto = 'ECONOMICO' | 'MODERADO' | 'FLEXIBLE' | 'SIN_PREFERENCIA'

export interface OpcionNivelGasto {
  codigo: NivelGasto
  nombre: string
  descripcion: string
}

export interface OpcionesGastoDistancia {
  nivelesGasto: OpcionNivelGasto[]
  distanciaMinimaKm: number
  distanciaMaximaKm: number
  pasoDistanciaKm: number
  nivelGastoInicial: NivelGasto
  distanciaInicialKm: number
  significadoGasto: string
  significadoDistancia: string
}

export interface SubcategoriaPreferencia {
  codigo: string
  nombre: string
}

export interface CategoriaPreferencia {
  codigo: string
  nombre: string
  subcategorias: SubcategoriaPreferencia[]
}

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null
}

function esListaDeTextos(valor: unknown): valor is string[] {
  return Array.isArray(valor) && valor.every(elemento => typeof elemento === 'string')
}

function esSubcategoria(valor: unknown): valor is SubcategoriaPreferencia {
  return esObjeto(valor) && typeof valor.codigo === 'string' && typeof valor.nombre === 'string'
}

function esCategoria(valor: unknown): valor is CategoriaPreferencia {
  return esObjeto(valor) && typeof valor.codigo === 'string' && typeof valor.nombre === 'string'
    && Array.isArray(valor.subcategorias) && valor.subcategorias.every(esSubcategoria)
}

function esPreferencias(valor: unknown): valor is Preferencias {
  return esObjeto(valor) && esListaDeTextos(valor.intereses)
    && esListaDeTextos(valor.categoriasExcluidas) && esListaDeTextos(valor.subcategoriasExcluidas)
    && esNivelGasto(valor.nivelGasto)
    && typeof valor.distanciaKm === 'number' && Number.isFinite(valor.distanciaKm)
}

function esNivelGasto(valor: unknown): valor is NivelGasto {
  return typeof valor === 'string'
    && ['ECONOMICO', 'MODERADO', 'FLEXIBLE', 'SIN_PREFERENCIA'].includes(valor)
}

function esOpcionNivelGasto(valor: unknown): valor is OpcionNivelGasto {
  return esObjeto(valor) && esNivelGasto(valor.codigo)
    && typeof valor.nombre === 'string' && typeof valor.descripcion === 'string'
}

function esOpcionesGastoDistancia(valor: unknown): valor is OpcionesGastoDistancia {
  return esObjeto(valor) && Array.isArray(valor.nivelesGasto)
    && valor.nivelesGasto.length > 0 && valor.nivelesGasto.every(esOpcionNivelGasto)
    && esNivelGasto(valor.nivelGastoInicial)
    && valor.nivelesGasto.some(nivel => nivel.codigo === valor.nivelGastoInicial)
    && typeof valor.distanciaMinimaKm === 'number' && Number.isFinite(valor.distanciaMinimaKm)
    && typeof valor.distanciaMaximaKm === 'number' && Number.isFinite(valor.distanciaMaximaKm)
    && typeof valor.pasoDistanciaKm === 'number' && Number.isFinite(valor.pasoDistanciaKm)
    && typeof valor.distanciaInicialKm === 'number' && Number.isFinite(valor.distanciaInicialKm)
    && valor.pasoDistanciaKm > 0 && valor.distanciaMinimaKm > 0
    && valor.distanciaMinimaKm <= valor.distanciaInicialKm
    && valor.distanciaInicialKm <= valor.distanciaMaximaKm
    && typeof valor.significadoGasto === 'string' && typeof valor.significadoDistancia === 'string'
}

async function leerRespuestaPreferencias(respuesta: Response): Promise<unknown> {
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(esObjeto(datos) && typeof datos.mensaje === 'string'
      ? datos.mensaje : 'No se han podido comprobar las preferencias. Comprueba que el backend esté actualizado y arrancado.')
  }
  return datos
}

export async function obtenerCatalogoPreferencias(signal: AbortSignal): Promise<CategoriaPreferencia[]> {
  const respuesta = await fetch('/api/itinerarios/catalogo-preferencias', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { Accept: 'application/json' },
  })
  const datos = await leerRespuestaPreferencias(respuesta)
  if (!Array.isArray(datos) || datos.length === 0 || !datos.every(esCategoria)) {
    throw new Error('El servidor ha devuelto un catálogo de preferencias no válido.')
  }
  return datos
}

export async function validarPreferencias(preferencias: Preferencias, signal: AbortSignal): Promise<Preferencias> {
  const respuesta = await fetch('/api/itinerarios/validar-preferencias', {
    method: 'POST',
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(preferencias),
  })
  const datos = await leerRespuestaPreferencias(respuesta)
  if (!esPreferencias(datos)) throw new Error('El servidor ha devuelto unas preferencias no válidas.')
  return datos
}

export async function obtenerOpcionesGastoDistancia(signal: AbortSignal): Promise<OpcionesGastoDistancia> {
  const respuesta = await fetch('/api/itinerarios/opciones-gasto-distancia', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { Accept: 'application/json' },
  })
  const datos = await leerRespuestaPreferencias(respuesta)
  if (!esOpcionesGastoDistancia(datos)) {
    throw new Error('El servidor ha devuelto unas opciones de gasto y distancia no válidas.')
  }
  return datos
}

function esDestino(valor: unknown): valor is Destino {
  if (typeof valor !== 'object' || valor === null) return false
  const destino = valor as Record<string, unknown>
  return typeof destino.id === 'string' && typeof destino.nombre === 'string'
    && typeof destino.region === 'string' && typeof destino.pais === 'string'
    && typeof destino.latitud === 'number' && Number.isFinite(destino.latitud)
    && typeof destino.longitud === 'number' && Number.isFinite(destino.longitud)
}

function esConfiguracionItinerario(valor: unknown): valor is ConfiguracionItinerario {
  const formatoFechaHora = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/
  return esObjeto(valor) && esDestino(valor.destino)
    && esObjeto(valor.disponibilidad)
    && typeof valor.disponibilidad.llegada === 'string'
    && typeof valor.disponibilidad.salida === 'string'
    && formatoFechaHora.test(valor.disponibilidad.llegada)
    && formatoFechaHora.test(valor.disponibilidad.salida)
    && typeof valor.disponibilidad.duracionDias === 'number'
    && Number.isInteger(valor.disponibilidad.duracionDias)
    && typeof valor.alojamiento === 'string' && esPreferencias(valor.preferencias)
    && esRitmoDiario(valor.ritmo)
}

function esRitmoDiario(valor: unknown): valor is RitmoDiario {
  const hora = /^\d{2}:\d{2}(?::\d{2})?$/
  return esObjeto(valor)
    && typeof valor.comidaInicio === 'string' && hora.test(valor.comidaInicio)
    && typeof valor.comidaFin === 'string' && hora.test(valor.comidaFin)
    && typeof valor.descansoCadaMinutos === 'number' && Number.isInteger(valor.descansoCadaMinutos)
    && typeof valor.descansoDuracionMinutos === 'number' && Number.isInteger(valor.descansoDuracionMinutos)
    && typeof valor.margenMinutos === 'number' && Number.isInteger(valor.margenMinutos)
}

export async function confirmarConfiguracion(
  configuracion: ConfiguracionItinerario, signal: AbortSignal
): Promise<ConfiguracionItinerario> {
  const respuesta = await fetch('/api/itinerarios/confirmar-configuracion', {
    method: 'POST',
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(configuracion),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(esObjeto(datos) && typeof datos.mensaje === 'string'
      ? datos.mensaje : 'No se ha podido confirmar la configuración. Comprueba la conexión con el backend e inténtalo de nuevo.')
  }
  if (!esConfiguracionItinerario(datos)) {
    throw new Error('El servidor ha devuelto una configuración no válida. No se ha confirmado el viaje.')
  }
  return datos
}

function esLugar(valor: unknown): valor is Lugar {
  return esObjeto(valor) && typeof valor.id === 'string' && valor.id.trim().length > 0
    && typeof valor.nombre === 'string'
    && (valor.direccion === null || typeof valor.direccion === 'string')
    && (valor.latitud === null || typeof valor.latitud === 'number' && Number.isFinite(valor.latitud))
    && (valor.longitud === null || typeof valor.longitud === 'number' && Number.isFinite(valor.longitud))
    && esListaDeTextos(valor.categorias) && esListaDeTextos(valor.subcategorias)
    && (valor.horario === undefined || typeof valor.horario === 'string')
}

export async function prepararCandidatos(
  configuracion: ConfiguracionItinerario, signal: AbortSignal
): Promise<Lugar[]> {
  const respuesta = await fetch('/api/candidatos/preparar', {
    method: 'POST',
    // Hasta seis consultas independientes, cada una con timeout en el backend.
    signal: AbortSignal.any([signal, AbortSignal.timeout(60_000)]),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(configuracion),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(esObjeto(datos) && typeof datos.mensaje === 'string'
      ? datos.mensaje : 'No se han podido buscar los lugares. Comprueba la conexión con el backend y vuelve a intentarlo.')
  }
  if (!Array.isArray(datos) || !datos.every(esLugar)) {
    throw new Error('El servidor ha devuelto una lista de lugares no válida.')
  }
  return datos
}

export async function buscarDestinos(texto: string, signal: AbortSignal): Promise<Destino[]> {
  const parametros = new URLSearchParams({ texto })
  const respuesta = await fetch(`/api/destinos?${parametros}`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    headers: { Accept: 'application/json' },
  })
  const datos: unknown = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    const mensaje = typeof datos === 'object' && datos !== null && 'mensaje' in datos
      && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se ha podido buscar el destino.'
    throw new Error(mensaje)
  }
  if (!Array.isArray(datos) || !datos.every(esDestino)) {
    throw new Error('El servidor ha devuelto una respuesta no válida.')
  }
  return datos
}

export async function validarFechaHora(
  llegada: string | null,
  salida: string | null,
  signal?: AbortSignal
): Promise<FechaHora> {

  const respuesta = await fetch('/api/itinerarios/validar-fecha-hora', {
    method: 'POST',
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(10_000)])
      : AbortSignal.timeout(10_000),
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      llegada,
      salida,
    }),
  })

  const datos: unknown = await respuesta.json().catch(() => null)

  if (!respuesta.ok) {
    const mensaje = typeof datos === 'object' && datos !== null && 'mensaje' in datos
      && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se han podido validar las fechas y horas. Comprueba que el backend esté arrancado.'
    throw new Error(mensaje)
  }

  if (typeof datos !== 'object' || datos === null
    || !('llegada' in datos) || typeof datos.llegada !== 'string'
    || !('salida' in datos) || typeof datos.salida !== 'string'
    || !('duracionDias' in datos) || typeof datos.duracionDias !== 'number') {
    throw new Error('El servidor ha devuelto una respuesta no válida.')
  }

  return {
    llegada: datos.llegada,
    salida: datos.salida,
    duracionDias: datos.duracionDias,
  }
}

export type CosteCandidato = 'FREE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN'

export interface CandidatoEvaluado {
  lugar: Lugar
  afinidad: number
  duracionMinutos: number
  etiqueta: string
  motivo: string
  coste: CosteCandidato
  origenEvaluacion: 'GEMINI' | 'LOCAL'
  confianzaCoste: 'CONFIRMED' | 'ESTIMATED' | 'UNKNOWN'
  estadoHorario: 'VERIFIED' | 'PARTIAL' | 'UNVERIFIED'
  precioMinimo: number | null
  precioMaximo: number | null
  moneda: string | null
  informacion: string | null
  fuentes: string[]
  horario: string | null
  horarioFuente: string | null
}

export interface EvaluacionCandidatos {
  candidatos: CandidatoEvaluado[]
  fallback: boolean
  advertencias: string[]
  minutosDisponibles: number
  minutosInvestigados: number
}

export type TipoBloqueItinerario = 'VISITA' | 'TRASLADO' | 'TIEMPO_LIBRE' | 'COMIDA' | 'DESCANSO' | 'MARGEN'

export interface OpcionComida {
  id: string
  nombre: string
  latitud: number | null
  longitud: number | null
  direccion: string | null
  horario: string | null
  horarioFuente: string | null
}

export interface BloqueItinerario {
  tipo: TipoBloqueItinerario
  inicio: string
  fin: string
  candidato: CandidatoEvaluado | null
  origenId: string | null
  destinoId: string | null
  distanciaMetros: number | null
  duracionSegundos: number | null
  fuente: string | null
  condicionada: boolean
  descripcion: string | null
  restaurante: OpcionComida | null
  alternativasComida: OpcionComida[]
}

export interface DiaItinerario {
  fecha: string
  inicioDisponible: string | null
  finDisponible: string | null
  bloques: BloqueItinerario[]
}

export interface ItinerarioGenerado {
  dias: DiaItinerario[]
  advertencias: string[]
  parcial: boolean
  candidatosEvaluados: number
  candidatosEnMatriz: number
  candidatos: CandidatoEvaluado[]
  evaluacionFallback: boolean
}

function esTextoONulo(valor: unknown): valor is string | null {
  return valor === null || typeof valor === 'string'
}

function esNumeroONulo(valor: unknown): valor is number | null {
  return valor === null || typeof valor === 'number' && Number.isFinite(valor) && valor >= 0
}

function esCandidatoEvaluado(valor: unknown): valor is CandidatoEvaluado {
  return esObjeto(valor) && esLugar(valor.lugar)
    && typeof valor.afinidad === 'number' && Number.isFinite(valor.afinidad)
    && typeof valor.duracionMinutos === 'number' && Number.isFinite(valor.duracionMinutos) && valor.duracionMinutos > 0
    && typeof valor.etiqueta === 'string' && typeof valor.motivo === 'string'
    && typeof valor.coste === 'string' && ['FREE', 'LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'].includes(valor.coste)
    && (valor.origenEvaluacion === 'GEMINI' || valor.origenEvaluacion === 'LOCAL')
    && (valor.confianzaCoste === 'CONFIRMED' || valor.confianzaCoste === 'ESTIMATED' || valor.confianzaCoste === 'UNKNOWN')
    && (valor.estadoHorario === 'VERIFIED' || valor.estadoHorario === 'PARTIAL' || valor.estadoHorario === 'UNVERIFIED')
    && esNumeroONulo(valor.precioMinimo) && esNumeroONulo(valor.precioMaximo) && esTextoONulo(valor.moneda)
    && esTextoONulo(valor.informacion) && esListaDeTextos(valor.fuentes)
    && esTextoONulo(valor.horario) && esTextoONulo(valor.horarioFuente)
}

export async function evaluarCandidatos(
  configuracion: ConfiguracionItinerario, signal: AbortSignal
): Promise<EvaluacionCandidatos> {
  const respuesta = await fetch('/api/candidatos/evaluar', {
    method: 'POST',
    signal: AbortSignal.any([signal, AbortSignal.timeout(120_000)]),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(configuracion),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(esObjeto(datos) && typeof datos.mensaje === 'string'
      ? datos.mensaje : 'No se han podido evaluar los lugares. Comprueba la conexión y vuelve a intentarlo.')
  }
  if (!esObjeto(datos) || !Array.isArray(datos.candidatos) || !datos.candidatos.every(esCandidatoEvaluado)
    || typeof datos.fallback !== 'boolean' || !esListaDeTextos(datos.advertencias)
    || typeof datos.minutosDisponibles !== 'number' || !Number.isFinite(datos.minutosDisponibles)
    || typeof datos.minutosInvestigados !== 'number' || !Number.isFinite(datos.minutosInvestigados)) {
    throw new Error('El servidor ha devuelto una evaluación de lugares no válida.')
  }
  return datos as unknown as EvaluacionCandidatos
}

function esOpcionComida(valor: unknown): valor is OpcionComida {
  return esObjeto(valor) && typeof valor.id === 'string' && typeof valor.nombre === 'string'
    && (valor.latitud === null || typeof valor.latitud === 'number' && Number.isFinite(valor.latitud))
    && (valor.longitud === null || typeof valor.longitud === 'number' && Number.isFinite(valor.longitud))
    && esTextoONulo(valor.direccion) && esTextoONulo(valor.horario) && esTextoONulo(valor.horarioFuente)
}

function esBloqueItinerario(valor: unknown): valor is BloqueItinerario {
  const fechaHora = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/
  return esObjeto(valor)
    && typeof valor.tipo === 'string'
    && ['VISITA', 'TRASLADO', 'TIEMPO_LIBRE', 'COMIDA', 'DESCANSO', 'MARGEN'].includes(valor.tipo)
    && typeof valor.inicio === 'string' && fechaHora.test(valor.inicio)
    && typeof valor.fin === 'string' && fechaHora.test(valor.fin)
    && (valor.candidato === null || esCandidatoEvaluado(valor.candidato))
    && esTextoONulo(valor.origenId) && esTextoONulo(valor.destinoId)
    && esNumeroONulo(valor.distanciaMetros) && esNumeroONulo(valor.duracionSegundos)
    && esTextoONulo(valor.fuente) && typeof valor.condicionada === 'boolean'
    && esTextoONulo(valor.descripcion)
    && (valor.restaurante === null || esOpcionComida(valor.restaurante))
    && Array.isArray(valor.alternativasComida) && valor.alternativasComida.every(esOpcionComida)
}

function esDiaItinerario(valor: unknown): valor is DiaItinerario {
  const fechaHora = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/
  return esObjeto(valor) && typeof valor.fecha === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor.fecha)
    && (valor.inicioDisponible === null || typeof valor.inicioDisponible === 'string' && fechaHora.test(valor.inicioDisponible))
    && (valor.finDisponible === null || typeof valor.finDisponible === 'string' && fechaHora.test(valor.finDisponible))
    && Array.isArray(valor.bloques) && valor.bloques.every(esBloqueItinerario)
}

function esItinerarioGenerado(valor: unknown): valor is ItinerarioGenerado {
  return esObjeto(valor) && Array.isArray(valor.dias) && valor.dias.every(esDiaItinerario)
    && esListaDeTextos(valor.advertencias) && typeof valor.parcial === 'boolean'
    && typeof valor.candidatosEvaluados === 'number' && Number.isInteger(valor.candidatosEvaluados) && valor.candidatosEvaluados >= 0
    && typeof valor.candidatosEnMatriz === 'number' && Number.isInteger(valor.candidatosEnMatriz) && valor.candidatosEnMatriz >= 0
    && Array.isArray(valor.candidatos) && valor.candidatos.every(esCandidatoEvaluado)
    && typeof valor.evaluacionFallback === 'boolean'
}

export async function generarItinerario(
  configuracion: ConfiguracionItinerario, signal: AbortSignal
): Promise<ItinerarioGenerado> {
  const respuesta = await fetch('/api/itinerarios/generar', {
    method: 'POST',
    signal: AbortSignal.any([signal, AbortSignal.timeout(300_000)]),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(configuracion),
  })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(esObjeto(datos) && typeof datos.mensaje === 'string'
      ? datos.mensaje : 'No se ha podido generar el itinerario. Comprueba la conexión y vuelve a intentarlo.')
  }
  if (!esItinerarioGenerado(datos)) {
    throw new Error('El servidor ha devuelto un itinerario no válido. Vuelve a intentarlo.')
  }
  return datos
}

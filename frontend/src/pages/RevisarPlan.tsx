import type { CategoriaPreferencia, ConfiguracionItinerario, OpcionesGastoDistancia } from '../api'
import { Icon } from '../assets/Icon'
import { CandidatosLugares } from '../components/CandidatosLugares'
import './RevisarPlan.css'

export type CampoPlan = 'destino' | 'fechas' | 'horarios' | 'alojamiento' | 'intereses' | 'exclusiones' | 'gasto' | 'distancia'

type Props = {
  configuracion: ConfiguracionItinerario
  confirmada: boolean
  confirmando: boolean
  errorConfirmacion: string
  catalogo: CategoriaPreferencia[]
  opcionesGastoDistancia: OpcionesGastoDistancia | null
  onEditar: (campo: CampoPlan) => void
  onAtras: () => void
  onConfirmar: () => void
}

function mostrarFecha(valor: string) {
  // Solo formateamos para la pantalla; no calculamos ni validamos la duración.
  return new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(valor.split('T')[0] + 'T12:00:00'))
}

export function RevisarPlan({ configuracion, confirmada, confirmando, errorConfirmacion, catalogo,
  opcionesGastoDistancia, onEditar, onAtras, onConfirmar }: Props) {
  const { destino, disponibilidad, alojamiento, preferencias } = configuracion
  const nombreCategoria = (codigo: string) => catalogo.find(categoria => categoria.codigo === codigo)?.nombre ?? codigo
  const nombreSubcategoria = (codigo: string) => catalogo.flatMap(categoria => categoria.subcategorias)
    .find(subcategoria => subcategoria.codigo === codigo)?.nombre ?? codigo
  const nivelGasto = opcionesGastoDistancia?.nivelesGasto.find(nivel => nivel.codigo === preferencias.nivelGasto)
  const exclusiones = [
    preferencias.categoriasExcluidas.length > 0
      ? 'Categorías completas: ' + preferencias.categoriasExcluidas.map(nombreCategoria).join(', ') : '',
    preferencias.subcategoriasExcluidas.length > 0
      ? 'Actividades: ' + preferencias.subcategoriasExcluidas.map(nombreSubcategoria).join(', ') : '',
  ].filter(Boolean)

  const apartados: { campo: CampoPlan; titulo: string; valores: string[]; ayuda?: string }[] = [
    { campo: 'destino', titulo: 'Destino', valores: [[destino.nombre, destino.region, destino.pais].filter(Boolean).join(', ')] },
    { campo: 'fechas', titulo: 'Fechas', valores: [
      'Del ' + mostrarFecha(disponibilidad.llegada) + ' al ' + mostrarFecha(disponibilidad.salida)
      + ' · ' + disponibilidad.duracionDias + (disponibilidad.duracionDias === 1 ? ' día' : ' días'),
    ] },
    { campo: 'horarios', titulo: 'Llegada / salida', valores: [
      disponibilidad.llegada.split('T')[1].slice(0, 5) + ' / ' + disponibilidad.salida.split('T')[1].slice(0, 5),
    ], ayuda: 'Llegada el primer día y salida el último. Horas locales del destino.' },
    { campo: 'alojamiento', titulo: 'Alojamiento', valores: [alojamiento.trim() || 'Sin alojamiento indicado'] },
    { campo: 'intereses', titulo: 'Intereses', valores: [
      preferencias.intereses.map(nombreCategoria).join(', ') || 'Sin intereses seleccionados',
    ] },
    { campo: 'exclusiones', titulo: 'A evitar', valores: exclusiones.length > 0 ? exclusiones : ['Sin preferencias para evitar'] },
    { campo: 'gasto', titulo: 'Nivel de gasto', valores: [nivelGasto?.nombre ?? 'Sin seleccionar'],
      ayuda: opcionesGastoDistancia?.significadoGasto },
    { campo: 'distancia', titulo: 'Distancia deseada entre actividades',
      valores: [preferencias.distanciaKm === null ? 'Sin seleccionar' : preferencias.distanciaKm + ' km'],
      ayuda: opcionesGastoDistancia?.significadoDistancia },
  ]

  return (
    <div className="revision-plan" aria-busy={confirmando}>
      <div className="revision-tarjetas" aria-label="Detalles del plan">
        {apartados.map(apartado => (
          <section className="revision-tarjeta" key={apartado.campo} aria-labelledby={'revision-' + apartado.campo}>
            <div className="revision-tarjeta-contenido">
              <h2 id={'revision-' + apartado.campo}>{apartado.titulo}</h2>
              {apartado.valores.map(valor => <p className="revision-valor" key={valor}>{valor}</p>)}
              {apartado.ayuda && <p className="revision-ayuda">{apartado.ayuda}</p>}
            </div>
            <button className="revision-editar" type="button" aria-label={'Editar ' + apartado.titulo.toLowerCase()}
              disabled={confirmando} onClick={() => onEditar(apartado.campo)}>Editar</button>
          </section>
        ))}
      </div>

      <p className="revision-nota"><Icon name="informacion" />
        <span>Confirma los datos de este resumen. Si editas algún apartado, tendrás que confirmar de nuevo. El viaje todavía no se guarda.</span>
      </p>
      <div className="revision-estado" role="status" aria-live="polite">
        {confirmando ? 'Comprobando toda la configuración…' : confirmada
          ? 'Configuración confirmada. Estos son los últimos valores validados para tu itinerario.'
          : 'Configuración pendiente de confirmar.'}
      </div>
      {errorConfirmacion && <p className="error-viaje" role="alert">{errorConfirmacion}</p>}
      <div className="revision-acciones">
        <button className="boton revision-atras" type="button" disabled={confirmando} onClick={onAtras}>← Atrás</button>
        <button className="boton boton-primario" type="button" disabled={confirmando || confirmada}
          onClick={onConfirmar}>
          {confirmando ? 'Confirmando…' : confirmada ? 'Configuración confirmada' : errorConfirmacion ? 'Reintentar confirmación' : 'Confirmar configuración'} <Icon name="flecha" />
        </button>
      </div>
      <p className="revision-generacion">La generación del itinerario estará disponible próximamente. Confirmar la configuración no genera actividades.</p>
      {confirmada && <CandidatosLugares configuracion={configuracion} />}
    </div>
  )
}

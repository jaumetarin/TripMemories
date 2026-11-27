import { useEffect, useMemo, useRef, useState } from 'react'
import { divIcon, latLngBounds } from 'leaflet'
import type { Marker as MarcadorLeaflet } from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer, Tooltip, useMap } from 'react-leaflet'
import type { DiaItinerario } from '../api'
import { useTrazados } from '../trazados'
import type { OrigenTrazados } from '../trazados'
import { agruparUbicaciones, horaLocal } from './visitasItinerario'
import type { PuntoInicioMapa, UbicacionVisitas, VisitaMapa } from './visitasItinerario'
import 'leaflet/dist/leaflet.css'
import './MapaItinerario.css'

interface Props {
  dia: DiaItinerario
  origenTrazados?: OrigenTrazados
  nombres: Map<string, string>
  visitas: VisitaMapa[]
  puntoInicio: PuntoInicioMapa
  seleccion: { clave: string } | null
  onSeleccionar: (clave: string) => void
  onVerTarjeta: (clave: string) => void
}

function encuadrar(
  map: ReturnType<typeof useMap>,
  grupos: UbicacionVisitas[],
  puntoInicio: PuntoInicioMapa,
  caminos: [number, number][] = [],
) {
  map.stop()

  const posiciones = [
    puntoInicio.posicion,
    ...grupos.map(grupo => grupo.posicion),
    ...caminos,
  ]

  const mismaUbicacion = grupos.every(grupo =>
    grupo.posicion[0] === puntoInicio.posicion[0]
    && grupo.posicion[1] === puntoInicio.posicion[1]
  )

  if (mismaUbicacion && caminos.length === 0) {
    map.setView(puntoInicio.posicion, 15, { animate: false })
  } else {
    map.fitBounds(latLngBounds(posiciones), {
      padding: [55, 55],
      maxZoom: 16,
      animate: false,
    })
  }
}

function VistaMapa({
  grupos,
  seleccion,
  puntoInicio,
  caminos,
}: {
  grupos: UbicacionVisitas[]
  seleccion: Props['seleccion']
  puntoInicio: PuntoInicioMapa
  caminos: [number, number][]
}) {
  const map = useMap()

  useEffect(() => {
    let encuadreAplicado = false

    const ajustar = () => {
      const contenedor = map.getContainer()

      if (!contenedor.clientWidth || !contenedor.clientHeight) return

      map.invalidateSize({ pan: false })

      if (encuadreAplicado) return
      encuadreAplicado = true

      const grupo = grupos.find(elemento =>
        elemento.visitas.some(visita => visita.clave === seleccion?.clave)
      )

      if (grupo) {
        map.flyTo(grupo.posicion, Math.max(15, map.getZoom()), {
          animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
          duration: 0.45,
        })
      } else {
        encuadrar(map, grupos, puntoInicio, caminos)
      }
    }

    const observador = new ResizeObserver(ajustar)
    observador.observe(map.getContainer())
    ajustar()

    return () => observador.disconnect()
  }, [map, grupos, seleccion, puntoInicio, caminos])

  return (
    <button type="button" className="mapa-ver-dia" onClick={evento => {
      evento.stopPropagation()
      map.closePopup()
      encuadrar(map, grupos, puntoInicio, caminos)
    }}>
      Ver recorrido del día
    </button>
  )
}
function MarcadorInicio({
  puntoInicio,
}: {
  puntoInicio: PuntoInicioMapa
}) {
  const marcador = useRef<MarcadorLeaflet>(null)

  const icono = useMemo(() => divIcon({
    html: '<span aria-hidden="true">Inicio</span>',
    className: 'inicio-marcador',
    iconSize: [70, 30],
    iconAnchor: [35, 46],
    popupAnchor: [0, -46],
  }), [])

  useEffect(() => {
    marcador.current?.getElement()?.setAttribute(
      'aria-label',
      `Mostrar punto de inicio: ${puntoInicio.nombre}`
    )
  }, [puntoInicio.nombre])

  return (
    <Marker
      ref={marcador}
      position={puntoInicio.posicion}
      icon={icono}
      title={`Punto de inicio: ${puntoInicio.nombre}`}
      zIndexOffset={500}
    >
      <Popup>
        <div className="mapa-visitas-popup">
          <p><strong>Inicio de cada día</strong></p>
          <p>{puntoInicio.nombre}</p>
          {puntoInicio.direccion
            && puntoInicio.direccion !== puntoInicio.nombre
            && <p>{puntoInicio.direccion}</p>}
        </div>
      </Popup>
    </Marker>
  )
}

function MarcadorVisitas({ grupo, seleccion, onSeleccionar, onVerTarjeta }: {
  grupo: UbicacionVisitas
  seleccion: Props['seleccion']
  onSeleccionar: Props['onSeleccionar']
  onVerTarjeta: Props['onVerTarjeta']
}) {
  const marcador = useRef<MarcadorLeaflet>(null)
  const seleccionada = grupo.visitas.find(visita => visita.clave === seleccion?.clave)
  const numeros = grupo.visitas.map(visita => visita.numero).join(' · ')
  const etiqueta = grupo.visitas.map(visita => `${visita.numero}. ${visita.nombre}`).join('; ')
  const icono = useMemo(() => divIcon({
    // Solo números generados por la aplicación; los nombres se renderizan con React.
    html: `<span aria-hidden="true">${numeros}</span>`,
    className: `visita-marcador${seleccionada ? ' visita-marcador-activo' : ''}`,
    iconSize: [grupo.visitas.length > 1 ? 68 : 40, 40],
    iconAnchor: [grupo.visitas.length > 1 ? 34 : 20, 20], popupAnchor: [0, -24],
  }), [numeros, seleccionada, grupo.visitas.length])

  useEffect(() => {
    const elemento = marcador.current?.getElement()
    elemento?.setAttribute('aria-label', `Mostrar visita ${etiqueta}`)
    elemento?.setAttribute('aria-pressed', String(!!seleccionada))
    if (seleccionada) marcador.current?.openPopup()
    else marcador.current?.closePopup()
  }, [seleccion, seleccionada, etiqueta, icono])

  return <Marker ref={marcador} position={grupo.posicion} icon={icono} title={etiqueta}
    zIndexOffset={seleccionada ? 1000 : 0} eventHandlers={{
      click: () => onSeleccionar((seleccionada ?? grupo.visitas[0]).clave),
      keydown: evento => {
        if (evento.originalEvent.key === ' ') {
          evento.originalEvent.preventDefault()
          onSeleccionar((seleccionada ?? grupo.visitas[0]).clave)
        }
      },
    }}>
    <Popup autoPan={false}>
      <div className="mapa-visitas-popup">
        {grupo.visitas.length > 1 && <p>Estas visitas comparten ubicación:</p>}
        {grupo.visitas.map(visita => <div key={visita.clave} className="mapa-visita-popup">
          <button type="button" className="mapa-elegir-visita" aria-pressed={visita.clave === seleccion?.clave}
            onClick={() => onSeleccionar(visita.clave)}>{visita.numero}. {visita.nombre}</button>
          <p>{horaLocal(visita.inicio)}–{horaLocal(visita.fin)}</p>
          {visita.direccion && <p>{visita.direccion}</p>}
          <button type="button" className="mapa-ver-tarjeta" onClick={() => onVerTarjeta(visita.clave)}>Ver tarjeta</button>
        </div>)}
      </div>
    </Popup>
  </Marker>
}

export default function MapaItinerario({
  dia,
  origenTrazados,
  nombres,
  visitas,
  puntoInicio,
  seleccion,
  onSeleccionar,
  onVerTarjeta,
}: Props) {
  const grupos = useMemo(() => agruparUbicaciones(visitas), [visitas])
  const [errorFondo, setErrorFondo] = useState(false)
  const marco = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(false)
  useEffect(() => {
    if (!marco.current) return
    const observador = new IntersectionObserver(([entrada]) => setVisible(entrada.isIntersecting))
    observador.observe(marco.current)
    return () => observador.disconnect()
  }, [])
  const { tramos, cargando, error, reintentar, reintentarDesde, esperandoCuota } = useTrazados(origenTrazados, dia, visible)
  const caminos = useMemo<[number, number][]>(() => tramos.flatMap(t =>
    t.geometria?.coordenadas.map(([lon, lat]): [number, number] => [lat, lon]) ?? []), [tramos])
  const incompletos = tramos.filter(t => t.estado === 'CUOTA_AGOTADA' || t.estado === 'NO_DISPONIBLE' || t.estado === 'COORDENADAS_AUSENTES')

  return <div ref={marco} className="mapa-recorrido">
    {cargando && <p className="mapa-aviso" role="status">Cargando recorrido… Puedes seguir consultando la lista.</p>}
    {(error || incompletos.length > 0) && <div className="mapa-aviso" role="status">
      {reintentarDesde && <p>Has agotado la cuota temporal de consultas de rutas. Puedes reintentar a partir del {new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(reintentarDesde))}. Los trazados disponibles y la lista siguen accesibles.</p>}
      <p>{error || `No podemos mostrar ${incompletos.length === 1 ? 'un tramo' : `${incompletos.length} tramos`} del recorrido. La lista sigue disponible.`}</p>
      {incompletos.length > 0 && <details><summary>Ver tramos sin trazado</summary><ul>{incompletos.map((t, indice) =>
        <li key={indice}>{nombres.get(t.origenId) ?? 'Origen'} → {nombres.get(t.destinoId) ?? 'Destino'}</li>)}</ul></details>}
      <button type="button" className="boton" disabled={cargando || esperandoCuota} onClick={reintentar}>Reintentar trazados</button>
    </div>}
    {grupos.length === 0 && (
      <p className="mapa-aviso" role="status">
        {visitas.length === 0
          ? 'No hay visitas programadas para este día. Se muestra el punto de inicio.'
          : 'Las visitas no tienen una ubicación disponible. Se muestra el punto de inicio.'}
      </p>
    )}

    {errorFondo && (
      <p className="mapa-aviso" role="status">
        Parte del mapa no se ha podido cargar. La lista de visitas sigue disponible.
      </p>
    )}

    <MapContainer
      className="mapa-itinerario-lienzo"
      center={puntoInicio.posicion}
      zoom={13}
      scrollWheelZoom={false}
    >
      <TileLayer
        url={import.meta.env.VITE_MAP_TILES_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'}
        attribution={'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}
        maxZoom={19}
        eventHandlers={{ tileerror: () => setErrorFondo(true) }}
      />

      <VistaMapa
        grupos={grupos}
        seleccion={seleccion}
        puntoInicio={puntoInicio}
        caminos={caminos}
      />

      {tramos.map((tramo, indice) => tramo.geometria && <Polyline key={`${tramo.clave}:${indice}`}
        positions={tramo.geometria.coordenadas.map(([lon, lat]): [number, number] => [lat, lon])}
        pathOptions={{ color: '#087f70', weight: 4, opacity: .85, lineCap: 'round' }}>
        <Tooltip sticky>{nombres.get(tramo.origenId) ?? 'Origen'} → {nombres.get(tramo.destinoId) ?? 'Destino'} · A pie</Tooltip>
      </Polyline>)}

      <MarcadorInicio puntoInicio={puntoInicio} />

      {grupos.map(grupo => (
        <MarcadorVisitas
          key={grupo.posicion.join(',')}
          grupo={grupo}
          seleccion={seleccion}
          onSeleccionar={onSeleccionar}
          onVerTarjeta={onVerTarjeta}
        />
      ))}
    </MapContainer>
    {tramos.some(t => t.estado === 'DISPONIBLE') && <p className="mapa-recorrido-fuente">Recorrido a pie · <a href="https://openrouteservice.org/" target="_blank" rel="noopener noreferrer">openrouteservice</a></p>}
  </div>
}

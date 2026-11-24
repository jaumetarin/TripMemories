import { useEffect, useMemo, useRef, useState } from 'react'
import { divIcon, latLngBounds } from 'leaflet'
import type { Marker as Marcador } from 'leaflet'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import type { EstadoLugar, Lugar, SeleccionLugar } from '../api'
import 'leaflet/dist/leaflet.css'
import './MapaItinerario.css'
import './PrioridadLugar.css'
import { etiquetasEstado, simbolosEstado } from './estadosLugar'

type Props = {
  lugares: Lugar[]
  selecciones: SeleccionLugar[]
  activo: string | null
  onActivar: (id: string) => void
  onVerTarjeta: (id: string) => void
}
type Localizable = Lugar & { latitud: number; longitud: number }

function Encuadre({ lugares, activo }: { lugares: Localizable[]; activo: string | null }) {
  const mapa = useMap()
  useEffect(() => {
    const ajustar = () => {
      if (!mapa.getContainer().clientWidth) return
      mapa.invalidateSize({ pan: false })
      const lugar = lugares.find(l => l.id === activo)
      if (lugar) mapa.setView([lugar.latitud, lugar.longitud], 16, { animate: false })
      else if (lugares.length === 1) mapa.setView([lugares[0].latitud, lugares[0].longitud], 14)
      else mapa.fitBounds(latLngBounds(lugares.map(l => [l.latitud, l.longitud])), { padding: [35, 35], maxZoom: 15 })
    }
    const observador = new ResizeObserver(ajustar)
    observador.observe(mapa.getContainer())
    ajustar()
    return () => observador.disconnect()
  }, [mapa, lugares, activo])
  return null
}

function Punto({ lugar, estado, activo, onActivar, onVerTarjeta }: {
  lugar: Localizable; estado?: EstadoLugar; activo: boolean
  onActivar: Props['onActivar']; onVerTarjeta: Props['onVerTarjeta']
}) {
  const ref = useRef<Marcador>(null)
  const icono = useMemo(() => divIcon({
    html: `<span aria-hidden="true">${estado ? simbolosEstado[estado] : '+'}</span>`,
    className: `visita-marcador seleccion-marcador ${estado ? `prioridad ${estado}` : ''}${activo ? ' visita-marcador-activo' : ''}`,
    iconSize: [38, 38], iconAnchor: [19, 19], popupAnchor: [0, -20],
  }), [estado, activo])
  useEffect(() => {
    const elemento = ref.current?.getElement()
    elemento?.setAttribute('aria-label', `${lugar.nombre}: ${estado ? etiquetasEstado[estado] : 'Sin seleccionar'}`)
    elemento?.setAttribute('title', `${lugar.nombre} · ${estado ? etiquetasEstado[estado] : 'Sin seleccionar'}`)
    if (activo) ref.current?.openPopup()
    else ref.current?.closePopup()
  }, [activo, estado, lugar.nombre, icono])
  return <Marker ref={ref} position={[lugar.latitud, lugar.longitud]} icon={icono}
    title={`${lugar.nombre} · ${estado ? etiquetasEstado[estado] : 'Sin seleccionar'}`}
    zIndexOffset={activo ? 1000 : 0} eventHandlers={{ click: () => onActivar(lugar.id) }}>
    <Popup autoPan={false} maxWidth={230} minWidth={180}><div className="seleccion-popup"><strong>{lugar.nombre}</strong><p>{lugar.direccion}</p>
      <p>{estado ? <span className={`prioridad prioridad-etiqueta ${estado}`}><span aria-hidden="true">{simbolosEstado[estado]}</span>{etiquetasEstado[estado]}</span> : 'Sin seleccionar'}</p>
      <button type="button" onClick={() => onVerTarjeta(lugar.id)}>Ver tarjeta y prioridad</button>
    </div></Popup>
  </Marker>
}

export default function MapaSeleccionLugares({ lugares, selecciones, activo, onActivar, onVerTarjeta }: Props) {
  const [error, setError] = useState(false)
  const puntos = useMemo(() => lugares.filter((l): l is Localizable =>
    typeof l.latitud === 'number' && typeof l.longitud === 'number'
    && Number.isFinite(l.latitud) && Number.isFinite(l.longitud)), [lugares])
  const estados = new Map(selecciones.map(s => [s.lugar.id, s.estado]))
  if (!puntos.length) return <div className="mapa-estado-vacio"><p>No hay lugares localizables para mostrar.</p><p>La lista sigue disponible.</p></div>
  return <>
    {error && <p role="status" className="mapa-aviso">No se ha podido cargar parte del mapa. Puedes seguir eligiendo lugares en la lista.</p>}
    <MapContainer className="mapa-itinerario-lienzo" center={[puntos[0].latitud, puntos[0].longitud]} zoom={13} scrollWheelZoom={false}>
      <TileLayer url={import.meta.env.VITE_MAP_TILES_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'}
        attribution={'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}
        maxZoom={19} eventHandlers={{ tileerror: () => setError(true) }} />
      <Encuadre lugares={puntos} activo={activo} />
      {puntos.map(lugar => <Punto key={lugar.id} lugar={lugar} estado={estados.get(lugar.id)} activo={activo === lugar.id}
        onActivar={onActivar} onVerTarjeta={onVerTarjeta} />)}
    </MapContainer>
  </>
}

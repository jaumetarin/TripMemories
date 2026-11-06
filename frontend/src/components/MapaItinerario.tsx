import { useEffect, useMemo, useRef, useState } from 'react'
import { divIcon, latLngBounds } from 'leaflet'
import type { Marker as MarcadorLeaflet } from 'leaflet'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { agruparUbicaciones, horaLocal } from './visitasItinerario'
import type { UbicacionVisitas, VisitaMapa } from './visitasItinerario'
import 'leaflet/dist/leaflet.css'
import './MapaItinerario.css'

interface Props {
  visitas: VisitaMapa[]
  seleccion: { clave: string } | null
  onSeleccionar: (clave: string) => void
  onVerTarjeta: (clave: string) => void
}

function encuadrar(map: ReturnType<typeof useMap>, grupos: UbicacionVisitas[]) {
  map.stop()
  if (grupos.length === 1) map.setView(grupos[0].posicion, 15, { animate: false })
  else map.fitBounds(latLngBounds(grupos.map(grupo => grupo.posicion)), { padding: [45, 45], maxZoom: 16, animate: false })
}

function VistaMapa({ grupos, seleccion }: { grupos: UbicacionVisitas[]; seleccion: Props['seleccion'] }) {
  const map = useMap()
  useEffect(() => {
    let encuadreAplicado = false
    const ajustar = () => {
      const contenedor = map.getContainer()
      if (!contenedor.clientWidth || !contenedor.clientHeight) return
      map.invalidateSize({ pan: false })
      if (encuadreAplicado) return
      encuadreAplicado = true
      const grupo = grupos.find(elemento => elemento.visitas.some(visita => visita.clave === seleccion?.clave))
      if (grupo) {
        map.flyTo(grupo.posicion, Math.max(15, map.getZoom()), {
          animate: !window.matchMedia('(prefers-reduced-motion: reduce)').matches, duration: 0.45,
        })
      } else encuadrar(map, grupos)
    }
    // También se activa al volver desde una pestaña oculta o cambiar de tamaño.
    const observador = new ResizeObserver(ajustar)
    observador.observe(map.getContainer())
    ajustar()
    // Leaflet detiene sus animaciones al desmontar el mapa; aquí solo limpiamos el observador.
    return () => observador.disconnect()
  }, [map, grupos, seleccion])
  return <button type="button" className="mapa-ver-dia" onClick={evento => {
    evento.stopPropagation()
    map.closePopup()
    encuadrar(map, grupos)
  }}>Ver todas las visitas del día</button>
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

export default function MapaItinerario({ visitas, seleccion, onSeleccionar, onVerTarjeta }: Props) {
  const grupos = useMemo(() => agruparUbicaciones(visitas), [visitas])
  const [errorFondo, setErrorFondo] = useState(false)
  if (grupos.length === 0) return <div className="mapa-estado-vacio" role="status">
    <span aria-hidden="true">◎</span>
    <p>{visitas.length === 0 ? 'No hay visitas programadas para mostrar en este día.' : 'Las visitas de este día no tienen una ubicación disponible.'}</p>
    <p>Puedes consultar el itinerario completo en la lista.</p>
  </div>

  return <>
    {errorFondo && <p className="mapa-aviso" role="status">Parte del mapa no se ha podido cargar. La lista de visitas sigue disponible.</p>}
    <MapContainer className="mapa-itinerario-lienzo" center={grupos[0].posicion} zoom={13} scrollWheelZoom={false}>
      <TileLayer url={import.meta.env.VITE_MAP_TILES_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'}
        attribution={'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}
        maxZoom={19} eventHandlers={{ tileerror: () => setErrorFondo(true) }} />
      <VistaMapa grupos={grupos} seleccion={seleccion} />
      {grupos.map(grupo => <MarcadorVisitas key={grupo.posicion.join(',')} grupo={grupo} seleccion={seleccion}
        onSeleccionar={onSeleccionar} onVerTarjeta={onVerTarjeta} />)}
    </MapContainer>
  </>
}

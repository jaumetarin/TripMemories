import { useEffect, useRef } from 'react'
import { Icon } from '../assets/Icon'
import './PantallaCarga.css'

type Props = {
  tipo: 'lugares' | 'itinerario'
  destino: string
  compacta?: boolean
  enfocar?: boolean
}

export function PantallaCarga({ tipo, destino, compacta = false, enfocar = false }: Props) {
  const titulo = useRef<HTMLHeadingElement>(null)
  const generando = tipo === 'itinerario'

  useEffect(() => {
    if (enfocar) titulo.current?.focus()
  }, [enfocar])

  return <section className={`pantalla-carga${compacta ? ' pantalla-carga-compacta' : ''}`}
    role="status" aria-live="polite" aria-atomic="true">
    <div className="carga-emblema" aria-hidden="true">
      <span className="carga-giro" />
      <Icon name={generando ? 'brujula' : 'ubicacion'} />
    </div>
    <div className="carga-contenido">
      <p className="carga-antetitulo">{generando ? 'DANDO FORMA A TU VIAJE' : 'EXPLORANDO TU DESTINO'}</p>
      <h2 ref={titulo} tabIndex={-1}>{generando ? `Preparando tu itinerario de ${destino}` : `Buscando lugares en ${destino}`}</h2>
      <p className="carga-descripcion">{generando
        ? 'Estamos reuniendo las visitas y organizando los días según tus preferencias, horarios y desplazamientos.'
        : 'Estamos reuniendo lugares compatibles con tus intereses y buscando una selección variada para ti.'}</p>
      {!compacta && <div className="carga-detalles" aria-label="Qué estamos preparando">
        <span><Icon name="ubicacion" />{generando ? 'Visitas a tu medida' : 'Lugares por descubrir'}</span>
        <span><Icon name={generando ? 'calendario' : 'brujula'} />{generando ? 'Días y horarios' : 'Tus intereses'}</span>
        <span><Icon name="mapa" />{generando ? 'Trayectos entre visitas' : 'Ubicaciones en el mapa'}</span>
      </div>}
      <p className="carga-espera">{generando
        ? 'Puede tardar unos minutos. Mantén esta página abierta; no necesitas volver a pulsar Generar.'
        : 'La búsqueda puede llevar un poco de tiempo. Tus datos y selecciones se conservan.'}</p>
    </div>
  </section>
}

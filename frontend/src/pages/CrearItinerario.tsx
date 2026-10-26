import { useState } from 'react'
import type { Destino } from '../api'
import { Icon } from '../assets/Icon'
import { BuscadorDestinos } from '../components/BuscadorDestinos'
import './CrearItinerario.css'

type CrearItinerarioProps = {
  onVolver: () => void
}

export function CrearItinerario({ onVolver }: CrearItinerarioProps) {
  const [destinoSeleccionado, setDestinoSeleccionado] = useState<Destino | null>(null)

  return (
    <section className="crear-viaje" aria-labelledby="crear-titulo">
      <button className="boton-volver" type="button" onClick={onVolver}>← Volver al inicio</button>
      <div className="crear-intro">
        <p className="antetitulo">TU PRÓXIMA HISTORIA</p>
        <h1 id="crear-titulo">Empecemos por el destino.</h1>
        <p className="subtitulo">Los grandes viajes empiezan con unos pequeños detalles.</p>
      </div>
      <div className="crear-distribucion">
        <nav className="acompanamiento" aria-label="Etapas del itinerario">
          <ol className="pasos">
            <li aria-current="step"><span>1</span><div><strong>El destino</strong><p>Dónde y cuándo.</p></div></li>
            <li><span>2</span><div><strong>Tus preferencias</strong><p>Un viaje a tu medida.</p></div></li>
            <li><span>3</span><div><strong>Tu itinerario</strong><p>Todo listo para explorar.</p></div></li>
          </ol>
        </nav>
        <div className="formulario-viaje">
          <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="ubicacion" /></span><div><h2>Tu viaje</h2><p>Elige dónde y cuándo.</p></div></div>
          <BuscadorDestinos seleccionado={destinoSeleccionado} onSeleccionar={setDestinoSeleccionado} />
          <div className="fechas">
            <div><label htmlFor="inicio">Fecha de inicio</label><input id="inicio" name="fechaInicio" type="date" /></div>
            <div><label htmlFor="fin">Fecha de fin</label><input id="fin" name="fechaFin" type="date" /></div>
          </div>
          <p className="aviso-formulario" id="aviso-guardar">Próximamente podrás continuar y guardar tu viaje. Por ahora, los datos de esta pantalla no se guardan.</p>
          <button className="boton boton-primario continuar" type="button" disabled aria-describedby="aviso-guardar">Continuar <Icon name="flecha" /></button>
        </div>
      </div>
    </section>
  )
}

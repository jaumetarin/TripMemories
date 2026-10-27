import type { SubmitEvent } from 'react'
import type { Destino, FechaHora } from '../api'
import { Icon } from '../assets/Icon'
import './PreferenciasGasto.css'

export type DatosPreferencias = {
  intereses: string[]
  evitar: string[]
  nivelGasto: string
  distanciaKm: number
}

type Props = {
  destino: Destino
  disponibilidad: FechaHora
  alojamiento: string
  datos: DatosPreferencias
  onCambiar: (datos: DatosPreferencias) => void
  onAtras: () => void
  onContinuar: () => void
}

const intereses = [
  { nombre: 'Arte y museos', icono: '🎨' },
  { nombre: 'Historia y arquitectura', icono: '🏛️' },
  { nombre: 'Gastronomía', icono: '🍽️' },
  { nombre: 'Naturaleza y parques', icono: '🌿' },
  { nombre: 'Cultura local', icono: '🎭' },
  { nombre: 'Ocio y entretenimiento', icono: '🎡' },
]

const opcionesEvitar = ['Ocio nocturno', 'Lugares muy concurridos', 'Actividades de coste elevado']

const nivelesGasto = [
  { nombre: 'Económico', descripcion: 'Priorizar opciones gratuitas o de bajo coste.' },
  { nombre: 'Moderado', descripcion: 'Combinar opciones gratuitas y de pago.' },
  { nombre: 'Flexible', descripcion: 'Dar más prioridad al interés de la experiencia.' },
  { nombre: 'Sin preferencia', descripcion: 'No priorizar por gasto.' },
]

function mostrarFecha(fechaHora: string) {
  const [anio, mes, dia] = fechaHora.split('T')[0].split('-')
  return `${dia}/${mes}/${anio.slice(-2)}`
}

export function PreferenciasGasto({ destino, disponibilidad, alojamiento, datos, onCambiar, onAtras, onContinuar }: Props) {
  function alternarSeleccion(campo: 'intereses' | 'evitar', opcion: string) {
    const seleccion = datos[campo].includes(opcion)
      ? datos[campo].filter(valor => valor !== opcion)
      : [...datos[campo], opcion]
    onCambiar({ ...datos, [campo]: seleccion })
  }

  function continuar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    // Este paso solo recoge selecciones de la interfaz; no consulta ni guarda en el backend.
    onContinuar()
  }

  return (
    <div className="preferencias-distribucion">
      <form className="preferencias-formulario" onSubmit={continuar} aria-label="Preferencias y gasto">
        <fieldset className="grupo-preferencias">
          <legend>Tus intereses</legend>
          <p className="ayuda-preferencias" id="ayuda-intereses">Selecciona todo lo que te apetezca descubrir.</p>
          <div className="intereses-opciones">
            {intereses.map(interes => (
              <label className="opcion-interes" key={interes.nombre} data-seleccionada={datos.intereses.includes(interes.nombre) || undefined}>
                <span className="interes-icono" aria-hidden="true">{interes.icono}</span>
                <span>{interes.nombre}</span>
                <input type="checkbox" name="intereses" value={interes.nombre}
                  checked={datos.intereses.includes(interes.nombre)} aria-describedby="ayuda-intereses"
                  onChange={() => alternarSeleccion('intereses', interes.nombre)} />
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="grupo-preferencias">
          <legend>Qué prefieres evitar</legend>
          <div className="evitar-opciones">
            {opcionesEvitar.map(opcion => (
              <label className="opcion-evitar" key={opcion} data-seleccionada={datos.evitar.includes(opcion) || undefined}>
                <input type="checkbox" name="evitar" value={opcion} checked={datos.evitar.includes(opcion)}
                  aria-describedby="ayuda-evitar" onChange={() => alternarSeleccion('evitar', opcion)} />
                <span>{opcion}</span>
              </label>
            ))}
          </div>
          <p className="ayuda-preferencias" id="ayuda-evitar">Las preferencias orientan las recomendaciones; no son garantías absolutas.</p>
        </fieldset>

        <fieldset className="grupo-preferencias">
          <legend>Nivel de gasto</legend>
          <p className="ayuda-preferencias" id="ayuda-gasto">Este nivel orientará las recomendaciones. No es un presupuesto exacto.</p>
          <div className="gasto-opciones">
            {nivelesGasto.map(nivel => (
              <label className="opcion-gasto" key={nivel.nombre} data-seleccionada={datos.nivelGasto === nivel.nombre || undefined}>
                <input type="radio" name="nivelGasto" value={nivel.nombre} checked={datos.nivelGasto === nivel.nombre}
                  aria-describedby="ayuda-gasto" onChange={() => onCambiar({ ...datos, nivelGasto: nivel.nombre })} />
                <span><strong>{nivel.nombre}</strong><span className="gasto-descripcion">{nivel.descripcion}</span></span>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="grupo-preferencias distancia-preferencias">
          <label htmlFor="distancia-actividades">Distancia entre actividades</label>
          <p className="ayuda-preferencias" id="ayuda-distancia"><Icon name="informacion" />Se aplica entre una actividad y la siguiente, no a la distancia total del día.</p>
          <div className="distancia-control">
            <input id="distancia-actividades" name="distanciaKm" type="range" min={1} max={10} step={1}
              value={datos.distanciaKm} aria-describedby="ayuda-distancia"
              aria-valuetext={`${datos.distanciaKm} kilómetros entre actividades`}
              onChange={evento => onCambiar({ ...datos, distanciaKm: Number(evento.target.value) })} />
            <output htmlFor="distancia-actividades">{datos.distanciaKm} km</output>
          </div>
          <div className="distancia-limites" aria-hidden="true"><span>1 km</span><span>10 km</span></div>
        </div>

        <p className="nota-preferencias">Puedes cambiar estas opciones al volver a este paso. De momento forman parte de tu borrador.</p>
        <div className="acciones-preferencias">
          <button className="boton boton-secundario" type="button" onClick={onAtras}>← Atrás</button>
          <button className="boton boton-primario" type="submit">Continuar <Icon name="derecha" /></button>
        </div>
      </form>

      <aside className="resumen-preferencias" aria-labelledby="resumen-preferencias-titulo">
        <div className="resumen-preferencias-icono"><Icon name="maleta" /></div>
        <h2 id="resumen-preferencias-titulo">Resumen del viaje</h2>
        <dl>
          <div><dt>Destino</dt><dd>{destino.nombre}{destino.pais ? `, ${destino.pais}` : ''}</dd></div>
          <div><dt>Fechas</dt><dd>{disponibilidad.duracionDias} {disponibilidad.duracionDias === 1 ? 'día' : 'días'}<span>{mostrarFecha(disponibilidad.llegada)} – {mostrarFecha(disponibilidad.salida)}</span></dd></div>
          <div><dt>Horario</dt><dd>{disponibilidad.llegada.split('T')[1].slice(0, 5)} → {disponibilidad.salida.split('T')[1].slice(0, 5)}<span>Llegada el primer día · salida el último día</span></dd></div>
          {alojamiento.trim() && <div><dt>Alojamiento</dt><dd>{alojamiento}</dd></div>}
        </dl>
        <p className="resumen-preferencias-nota">Un viaje a tu ritmo, desde el primer momento.</p>
      </aside>
    </div>
  )
}

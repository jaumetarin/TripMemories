import { useEffect, useRef } from 'react'
import type { SubmitEvent } from 'react'
import type { Destino, FechasItinerario } from '../api'
import { Icon } from '../assets/Icon'
import './HorariosAlojamiento.css'

export interface DatosHorarios {
  horaLlegada: string
  horaSalida: string
  alojamiento: string
}

type Props = {
  destino: Destino
  fechas: FechasItinerario
  datos: DatosHorarios
  onCambiar: (datos: DatosHorarios) => void
  onAtras: () => void
  onConfirmar: (horarios: DatosHorarios) => void
}

export function HorariosAlojamiento({ destino, fechas, datos, onCambiar, onAtras, onConfirmar }: Props) {
  const llegada = useRef<HTMLInputElement>(null)

  useEffect(() => {
    llegada.current?.focus()
  }, [])

  function cambiar(campo: keyof DatosHorarios, valor: string) {
    onCambiar({ ...datos, [campo]: valor })
  }

  function confirmar(sinAlojamiento = false) {
    const horarios = { ...datos, alojamiento: sinAlojamiento ? '' : datos.alojamiento }
    if (sinAlojamiento) onCambiar(horarios)
    onConfirmar(horarios)
  }

  function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    confirmar()
  }

  function mostrarFecha(fecha: string) {
    return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium' }).format(new Date(`${fecha}T12:00:00`))
  }

  return (
    <div className="horarios-distribucion">
      <form className="formulario-viaje horarios-formulario" noValidate onSubmit={enviar}>
        <fieldset className="campos-viaje">
          <legend className="solo-lectores">Horarios y alojamiento del viaje</legend>
          <div className="horas-viaje">
            <div>
              <label htmlFor="hora-llegada">Hora de llegada aproximada</label>
              <input ref={llegada} id="hora-llegada" type="time" name="horaLlegada" step={60}
                value={datos.horaLlegada} onChange={evento => cambiar('horaLlegada', evento.target.value)}
                aria-describedby="ayuda-llegada" />
              <p className="ayuda-fechas" id="ayuda-llegada">El primer día empezará después de tu llegada.</p>
            </div>
            <div>
              <label htmlFor="hora-salida">Hora de salida aproximada</label>
              <input id="hora-salida" type="time" name="horaSalida" step={60}
                value={datos.horaSalida} onChange={evento => cambiar('horaSalida', evento.target.value)}
                aria-describedby="ayuda-salida" />
              <p className="ayuda-fechas" id="ayuda-salida">El último día terminará antes de tu salida.</p>
            </div>
          </div>
          <div className="campo-alojamiento">
            <label htmlFor="alojamiento">Dirección del alojamiento <span>(opcional)</span></label>
            <div className="alojamiento-entrada">
              <Icon name="casa" />
              <input id="alojamiento" type="text" name="alojamiento" maxLength={250}
                placeholder="Hotel, apartamento o zona de referencia" value={datos.alojamiento}
                onChange={evento => cambiar('alojamiento', evento.target.value)} aria-describedby="ayuda-alojamiento" />
            </div>
            <p className="nota-alojamiento" id="ayuda-alojamiento"><Icon name="informacion" />Puedes dejarlo vacío si todavía no sabes dónde te alojarás.</p>
            <button className="omitir-alojamiento" type="button" onClick={() => confirmar(true)}>Continuar sin alojamiento</button>
          </div>
        </fieldset>
        <div className="acciones-horarios">
          <button className="boton boton-secundario" type="button" onClick={onAtras}>← Atrás</button>
          <button className="boton boton-primario" type="submit">
            Continuar <Icon name="derecha" />
          </button>
        </div>
      </form>
      <aside className="resumen-lateral" aria-labelledby="resumen-lateral-titulo">
        <h2 id="resumen-lateral-titulo">Resumen del viaje</h2>
        <dl>
          <div><dt>Destino</dt><dd>{destino.nombre}{destino.pais ? `, ${destino.pais}` : ''}</dd></div>
          <div><dt>Fechas</dt><dd>{fechas.duracionDias} {fechas.duracionDias === 1 ? 'día' : 'días'}<span>{mostrarFecha(fechas.fechaInicio)} – {mostrarFecha(fechas.fechaFin)}</span></dd></div>
          <div><dt>Horario</dt><dd>{datos.horaLlegada || 'Sin indicar'} → {datos.horaSalida || 'Sin indicar'}<span>Llegada el primer día · salida el último día</span></dd></div>
          {datos.alojamiento.trim() && <div><dt>Alojamiento</dt><dd>{datos.alojamiento}</dd></div>}
        </dl>
      </aside>
    </div>
  )
}

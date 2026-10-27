import { useRef, useState } from 'react'
import type { Ref } from 'react'
import { Icon } from '../assets/Icon'
import './CampoFecha.css'

type Props = {
  id: string
  nombre: string
  etiqueta: string
  valor: string
  onCambiar: (fecha: string) => void
  descripcion: string
  ref?: Ref<HTMLInputElement>
}

// Solo cambia la presentación. Las fechas imposibles las rechaza el backend.
function mostrarFecha(fecha: string) {
  if (!fecha) return ''
  const [anio, mes, dia] = fecha.split('-')
  return `${dia}/${mes}/${anio.slice(-2)}`
}

function convertirAISO(texto: string) {
  // También admite solo números para los teclados numéricos del móvil.
  const partes = /^(\d{2})\/?(\d{2})\/?(\d{2}|\d{4})$/.exec(texto)
  if (!partes) return ''
  const [, dia, mes, anio] = partes
  return `${anio.length === 2 ? `20${anio}` : anio}-${mes}-${dia}`
}

export function CampoFecha({ id, nombre, etiqueta, valor, onCambiar, descripcion, ref }: Props) {
  const calendario = useRef<HTMLInputElement>(null)
  const [texto, setTexto] = useState<string | null>(null)

  return (
    <div className="campo-fecha">
      <label htmlFor={id}>{etiqueta}</label>
      <div className="fecha-entrada">
        <input ref={ref} id={id} name={nombre} type="text" inputMode="numeric" required
          placeholder="dd/mm/yy" maxLength={10} autoComplete="off"
          value={texto ?? mostrarFecha(valor)} aria-describedby={`${id}-formato ${descripcion}`}
          onChange={evento => {
            setTexto(evento.target.value)
            onCambiar(convertirAISO(evento.target.value))
          }}
          onBlur={() => { if (valor) setTexto(null) }} />
        <button className="fecha-calendario" type="button" aria-label={`Abrir calendario: ${etiqueta.toLowerCase()}`}
          onClick={() => calendario.current?.showPicker()}>
          <Icon name="calendario" />
        </button>
        <input className="fecha-selector" ref={calendario} type="date" value={valor}
          tabIndex={-1} aria-hidden="true"
          onChange={evento => {
            setTexto(null)
            onCambiar(evento.target.value)
          }} />
      </div>
      <p className="fecha-formato" id={`${id}-formato`}>dd/mm/yy · Ejemplo: 27/10/26</p>
    </div>
  )
}

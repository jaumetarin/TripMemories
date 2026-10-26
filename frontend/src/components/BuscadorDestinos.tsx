import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import type { Destino } from '../api'
import { buscarDestinos } from '../api'
import { Icon } from '../assets/Icon'
import './BuscadorDestinos.css'

interface Props {
  seleccionado: Destino | null
  onSeleccionar: (destino: Destino | null) => void
}

interface Busqueda {
  texto: string
  destinos: Destino[]
  error: string
  terminada: boolean
}

export function BuscadorDestinos({ seleccionado, onSeleccionar }: Props) {
  const [texto, setTexto] = useState(seleccionado?.nombre ?? '')
  const [abierto, setAbierto] = useState(false)
  const [indiceActivo, setIndiceActivo] = useState(-1)
  const [reintento, setReintento] = useState(0)
  const [busqueda, setBusqueda] = useState<Busqueda | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const id = useId()
  const consulta = texto.trim()
  const puedeBuscar = consulta.length >= 3 && seleccionado === null
  const actual = busqueda?.texto === consulta ? busqueda : null
  const destinos = puedeBuscar && actual?.terminada ? actual.destinos : []
  const mostrarLista = abierto && destinos.length > 0

  useEffect(() => {
    if (!puedeBuscar) return
    const controlador = new AbortController()
    let vigente = true

    // Espera una pausa al escribir y descarta respuestas de consultas anteriores.
    const temporizador = window.setTimeout(async () => {
      setBusqueda({ texto: consulta, destinos: [], error: '', terminada: false })
      try {
        const resultados = await buscarDestinos(consulta, controlador.signal)
        if (vigente) {
          setBusqueda({ texto: consulta, destinos: resultados, error: '', terminada: true })
        }
      } catch (error) {
        if (!vigente) return
        let mensaje = 'No se puede conectar con el backend. Comprueba que esté arrancado.'
        if (error instanceof Error && error.name === 'TimeoutError') {
          mensaje = 'La búsqueda está tardando demasiado. Inténtalo de nuevo.'
        } else if (error instanceof Error && error.name === 'Error') {
          mensaje = error.message
        }
        setBusqueda({ texto: consulta, destinos: [], error: mensaje, terminada: true })
      }
    }, 350)

    return () => {
      vigente = false
      window.clearTimeout(temporizador)
      controlador.abort()
    }
  }, [texto, consulta, puedeBuscar, reintento])

  function editar(valor: string) {
    setTexto(valor)
    setBusqueda(null)
    setIndiceActivo(-1)
    setAbierto(true)
    onSeleccionar(null)
  }

  function seleccionar(destino: Destino) {
    setTexto(destino.nombre)
    onSeleccionar(destino)
    setAbierto(false)
    setIndiceActivo(-1)
    input.current?.focus()
  }

  function manejarTeclado(evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.nativeEvent.isComposing) return
    if (evento.key === 'Escape') {
      setAbierto(false)
      setIndiceActivo(-1)
    } else if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && destinos.length > 0) {
      evento.preventDefault()
      setAbierto(true) 
      setIndiceActivo(indice => evento.key === 'ArrowDown'
        ? (indice + 1) % destinos.length
        : (indice <= 0 ? destinos.length - 1 : indice - 1))
    } else if (evento.key === 'Enter' && mostrarLista && indiceActivo >= 0) {
      evento.preventDefault()
      seleccionar(destinos[indiceActivo])
    }
  }

  let mensaje = 'Escribe al menos 3 caracteres para buscar una ciudad.'
  if (seleccionado) mensaje = `Destino seleccionado: ${[seleccionado.nombre, seleccionado.region, seleccionado.pais].filter(Boolean).join(', ')}.`
  else if (puedeBuscar && !actual?.terminada) mensaje = 'Buscando destinos…'
  else if (puedeBuscar && actual?.error) mensaje = actual.error
  else if (puedeBuscar && destinos.length === 0) mensaje = 'No encontramos ciudades. Prueba con otro nombre.'
  else if (destinos.length > 0) mensaje = `${destinos.length} sugerencias. Selecciona una ciudad de la lista.`

  return (
    <div className="buscador-destinos" onBlur={evento => {
      if (!evento.currentTarget.contains(evento.relatedTarget)) setAbierto(false)
    }}>
      <label htmlFor={`${id}-campo`}>Destino</label>
      <div className="destino-campo">
        <input
          ref={input}
          id={`${id}-campo`}
          name="destino"
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          maxLength={120}
          placeholder="¿Qué ciudad te gustaría descubrir?"
          value={texto}
          onChange={evento => editar(evento.target.value)}
          onFocus={() => setAbierto(true)}
          onKeyDown={manejarTeclado}
          aria-autocomplete="list"
          aria-expanded={mostrarLista}
          aria-controls={mostrarLista ? `${id}-lista` : undefined}
          aria-activedescendant={mostrarLista && indiceActivo >= 0 ? `${id}-opcion-${indiceActivo}` : undefined}
          aria-describedby={`${id}-estado`}
        />
        {texto && <button className="destino-limpiar" type="button" aria-label="Borrar destino" onClick={() => {
          editar('')
          input.current?.focus()
        }}>×</button>}
        {mostrarLista && (
          <ul className="destino-sugerencias" id={`${id}-lista`} role="listbox" aria-label="Destinos sugeridos">
            {destinos.map((destino, indice) => (
              <li key={destino.id} role="option" id={`${id}-opcion-${indice}`}
                aria-selected={indice === indiceActivo}
                onPointerDown={evento => evento.preventDefault()}
                onMouseEnter={() => setIndiceActivo(indice)}
                onClick={() => seleccionar(destino)}>
                <Icon name="ubicacion" />
                <div><strong>{destino.nombre}</strong><span>{[destino.region, destino.pais].filter(Boolean).join(', ')}</span></div>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p id={`${id}-estado`} className={`destino-estado${actual?.error && puedeBuscar ? ' destino-error' : ''}`} role="status" aria-live="polite">
        {mensaje}
      </p>
      {puedeBuscar && actual?.error && <button type="button" className="destino-reintentar" onClick={() => {
        setBusqueda(null)
        setReintento(valor => valor + 1)
        input.current?.focus()
      }}>Reintentar búsqueda</button>}
      <p className="destino-atribucion">Lugares de <a href="https://www.geoapify.com/" target="_blank" rel="noreferrer">Geoapify</a> · © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a></p>
    </div>
  )
}

import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { buscarAlojamientos } from '../api'
import type { Alojamiento } from '../api'
import { Icon } from '../assets/Icon'
import './BuscadorDestinos.css'

interface Props {
  destinoId: string | null
  texto: string
  seleccionado: Alojamiento | null
  activo: boolean
  onCambiar: (texto: string, seleccionado: Alojamiento | null) => void
}

interface Busqueda {
  texto: string
  destinoId: string
  resultados: Alojamiento[]
  error: string
  terminada: boolean
}

export function BuscadorAlojamientos({
  destinoId,
  texto,
  seleccionado,
  activo,
  onCambiar,
}: Props) {
  const [busqueda, setBusqueda] = useState<Busqueda | null>(null)
  const [abierto, setAbierto] = useState(false)
  const [indiceActivo, setIndiceActivo] = useState(-1)
  const [reintento, setReintento] = useState(0)
  const input = useRef<HTMLInputElement>(null)
  const id = useId()

  const consulta = texto.trim()
  const puedeBuscar = activo
    && !!destinoId
    && consulta.length >= 3
    && seleccionado === null

  const actual = busqueda?.texto === consulta
    && busqueda.destinoId === destinoId
    ? busqueda
    : null

  const alojamientos = puedeBuscar && actual?.terminada
    ? actual.resultados
    : []

  const mostrarLista = abierto && alojamientos.length > 0

  useEffect(() => {
    if (!puedeBuscar || !destinoId) return

    const controlador = new AbortController()
    let vigente = true

    const temporizador = window.setTimeout(async () => {
      setBusqueda({
        texto: consulta,
        destinoId,
        resultados: [],
        error: '',
        terminada: false,
      })

      try {
        const resultados = await buscarAlojamientos(
          consulta,
          destinoId,
          controlador.signal
        )

        if (!vigente) return

        setBusqueda({
          texto: consulta,
          destinoId,
          resultados,
          error: '',
          terminada: true,
        })
      } catch (error) {
        if (!vigente) return

        let mensaje = 'No se puede conectar con el backend.'

        if (error instanceof Error && error.name === 'TimeoutError') {
          mensaje = 'La búsqueda está tardando demasiado. Inténtalo de nuevo.'
        } else if (error instanceof Error && error.name === 'Error') {
          mensaje = error.message
        }

        setBusqueda({
          texto: consulta,
          destinoId,
          resultados: [],
          error: mensaje,
          terminada: true,
        })
      }
    }, 350)

    return () => {
      vigente = false
      window.clearTimeout(temporizador)
      controlador.abort()
    }
  }, [consulta, destinoId, puedeBuscar, reintento])

    function editar(valor: string) {
    if (valor.trim() !== consulta) {
        setBusqueda(null)
    }

    setIndiceActivo(-1)
    setAbierto(true)
    onCambiar(valor, null)
    }

  function seleccionar(alojamiento: Alojamiento) {
    onCambiar(alojamiento.nombre, alojamiento)
    setAbierto(false)
    setIndiceActivo(-1)
    input.current?.focus()
  }

  function manejarTeclado(evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.nativeEvent.isComposing) return

    if (evento.key === 'Escape') {
      setAbierto(false)
      setIndiceActivo(-1)
    } else if (
      (evento.key === 'ArrowDown' || evento.key === 'ArrowUp')
      && alojamientos.length > 0
    ) {
      evento.preventDefault()
      setAbierto(true)
      setIndiceActivo(indice => evento.key === 'ArrowDown'
        ? (indice + 1) % alojamientos.length
        : (indice <= 0 ? alojamientos.length - 1 : indice - 1))
    } else if (
      evento.key === 'Enter'
      && mostrarLista
      && indiceActivo >= 0
    ) {
      evento.preventDefault()
      seleccionar(alojamientos[indiceActivo])
    }
  }

  let mensaje = 'Escribe al menos 3 caracteres y selecciona una sugerencia.'

  if (!destinoId) {
    mensaje = 'Selecciona primero el destino.'
  } else if (seleccionado) {
    mensaje = `Ubicación seleccionada: ${seleccionado.direccion}.`
  } else if (puedeBuscar && !actual?.terminada) {
    mensaje = 'Buscando alojamientos…'
  } else if (puedeBuscar && actual?.error) {
    mensaje = actual.error
  } else if (puedeBuscar && alojamientos.length === 0) {
    mensaje = 'No encontramos coincidencias. Prueba con otro nombre o dirección.'
  } else if (alojamientos.length > 0) {
    mensaje = `${alojamientos.length} sugerencias. Selecciona una ubicación.`
  }

  return (
    <div className="campo-alojamiento buscador-destinos"
      onBlur={evento => {
        if (!evento.currentTarget.contains(evento.relatedTarget)) {
          setAbierto(false)
        }
      }}>
      <label htmlFor="alojamiento">
        Dirección del alojamiento <span>(opcional)</span>
      </label>

      <div className="destino-campo alojamiento-entrada">
        <Icon name="casa" />
        <input
          ref={input}
          id="alojamiento"
          name="alojamiento"
          type="text"
          role="combobox"
          autoComplete="off"
          maxLength={250}
          placeholder="Hotel, apartamento o dirección"
          value={texto}
          disabled={!destinoId || !activo}
          onChange={evento => editar(evento.target.value)}
          onFocus={() => setAbierto(true)}
          onKeyDown={manejarTeclado}
          aria-autocomplete="list"
          aria-expanded={mostrarLista}
          aria-controls={mostrarLista ? `${id}-lista` : undefined}
          aria-activedescendant={mostrarLista && indiceActivo >= 0
            ? `${id}-opcion-${indiceActivo}`
            : undefined}
          aria-describedby={`${id}-estado ${id}-ayuda`}
        />

        {texto && (
          <button className="destino-limpiar" type="button"
            disabled={!destinoId || !activo}
            aria-label="Borrar alojamiento"
            onClick={() => {
              editar('')
              input.current?.focus()
            }}>
            ×
          </button>
        )}

        {mostrarLista && (
          <ul className="destino-sugerencias" id={`${id}-lista`}
            role="listbox" aria-label="Ubicaciones de alojamiento sugeridas">
            {alojamientos.map((alojamiento, indice) => (
              <li key={alojamiento.id}
                id={`${id}-opcion-${indice}`}
                role="option"
                aria-selected={indice === indiceActivo}
                onPointerDown={evento => evento.preventDefault()}
                onMouseEnter={() => setIndiceActivo(indice)}
                onClick={() => seleccionar(alojamiento)}>
                <Icon name="ubicacion" />
                <div>
                  <strong>{alojamiento.nombre}</strong>
                  <span>{alojamiento.direccion}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <p id={`${id}-estado`}
        className={`destino-estado${puedeBuscar && actual?.error ? ' destino-error' : ''}`}
        role="status" aria-live="polite">
        {mensaje}
      </p>

      {puedeBuscar && actual?.error && (
        <button className="destino-reintentar" type="button"
          onClick={() => {
            setBusqueda(null)
            setIndiceActivo(-1)
            setReintento(valor => valor + 1)
            input.current?.focus()
          }}>
          Reintentar búsqueda
        </button>
      )}

      <p className="ayuda-fechas" id={`${id}-ayuda`}>
        Puedes dejarlo vacío si todavía no sabes dónde te alojarás.
      </p>
    </div>
  )
}
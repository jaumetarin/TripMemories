import { useEffect, useMemo, useState } from 'react'
import type { SubmitEvent } from 'react'
import { validarPreferencias } from '../api'
import type { CategoriaPreferencia, Destino, FechaHora, Preferencias } from '../api'
import { Icon } from '../assets/Icon'
import './PreferenciasGasto.css'

export interface DatosPreferencias extends Preferencias {
  restricciones: string[]
  nivelGasto: string
  distanciaKm: number
}

type Props = {
  destino: Destino
  disponibilidad: FechaHora
  alojamiento: string
  catalogo: CategoriaPreferencia[]
  cargandoCatalogo: boolean
  errorCatalogo: string
  onReintentarCatalogo: () => void
  datos: DatosPreferencias
  onCambiar: (datos: DatosPreferencias) => void
  onAtras: () => void
  onContinuar: (preferencias: Preferencias) => void
}

type Validacion = {
  seleccion: Preferencias
  intento: number
  resultado: Preferencias | null
  error: string
}

// Los iconos son presentación; los códigos, nombres y relaciones vienen del backend.
const iconos: Record<string, string> = {
  GASTRONOMIA: '🍽️', ARTE_MUSEOS: '🎨', HISTORIA_ARQUITECTURA: '🏛️',
  NATURALEZA_PARQUES: '🌿', CULTURA_LOCAL: '🎭', OCIO_ENTRETENIMIENTO: '🎡',
}
const restricciones = ['Lugares muy concurridos', 'Actividades de coste elevado']
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

export function PreferenciasGasto({ destino, disponibilidad, alojamiento, catalogo, cargandoCatalogo,
  errorCatalogo, onReintentarCatalogo, datos, onCambiar, onAtras, onContinuar }: Props) {
  const [validacion, setValidacion] = useState<Validacion | null>(null)
  const [reintento, setReintento] = useState(0)
  const [gruposAbiertos, setGruposAbiertos] = useState<string[]>(() => catalogo
    .filter(categoria => datos.intereses.includes(categoria.codigo)
      || datos.categoriasExcluidas.includes(categoria.codigo)
      || categoria.subcategorias.some(subcategoria => datos.subcategoriasExcluidas.includes(subcategoria.codigo)))
    .map(categoria => categoria.codigo))
  const tieneCatalogo = catalogo.length > 0 && !cargandoCatalogo && !errorCatalogo

  // Cambiar gasto o distancia no vuelve a validar las categorías.
  const seleccion = useMemo<Preferencias>(() => ({
    intereses: datos.intereses,
    categoriasExcluidas: datos.categoriasExcluidas,
    subcategoriasExcluidas: datos.subcategoriasExcluidas,
  }), [datos.intereses, datos.categoriasExcluidas, datos.subcategoriasExcluidas])
  const actual = validacion?.seleccion === seleccion && validacion.intento === reintento ? validacion : null
  const comprobando = tieneCatalogo && (!actual || (!actual.resultado && !actual.error))
  const puedeContinuar = tieneCatalogo && Boolean(actual?.resultado)

  useEffect(() => {
    if (!tieneCatalogo) return
    const controlador = new AbortController()
    let vigente = true
    validarPreferencias(seleccion, controlador.signal)
      .then(resultado => { if (vigente) setValidacion({ seleccion, intento: reintento, resultado, error: '' }) })
      .catch(error => {
        if (!vigente) return
        let mensaje = 'No se puede comprobar tu selección. Revisa la conexión con el backend y vuelve a intentarlo.'
        if (error instanceof Error && error.name === 'TimeoutError') {
          mensaje = 'La comprobación está tardando demasiado. Vuelve a intentarlo.'
        } else if (error instanceof Error && error.name === 'Error') {
          mensaje = error.message
        }
        setValidacion({ seleccion, intento: reintento, resultado: null, error: mensaje })
      })
    // Las respuestas de selecciones anteriores nunca pueden sustituir a la actual.
    return () => { vigente = false; controlador.abort() }
  }, [seleccion, tieneCatalogo, reintento])

  function cambiarSeleccion(campo: keyof Preferencias | 'restricciones', codigo: string, marcada: boolean) {
    const valores = marcada
      ? [...new Set([...datos[campo], codigo])]
      : datos[campo].filter(valor => valor !== codigo)
    onCambiar({ ...datos, [campo]: valores })
    if (campo === 'intereses' && marcada) {
      setGruposAbiertos(abiertos => abiertos.includes(codigo) ? abiertos : [...abiertos, codigo])
    }
  }

  function cambiarGrupo(codigo: string, abierto: boolean) {
    setGruposAbiertos(actuales => {
      if (actuales.includes(codigo) === abierto) return actuales
      return abierto ? [...actuales, codigo] : actuales.filter(valor => valor !== codigo)
    })
  }

  function continuar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    // Solo se avanza con la respuesta válida correspondiente a esta selección exacta.
    if (puedeContinuar && actual?.resultado) onContinuar(actual.resultado)
  }

  const categoriasElegidas = catalogo.filter(categoria => datos.categoriasExcluidas.includes(categoria.codigo))
  const subcategoriasElegidas = catalogo.flatMap(categoria => categoria.subcategorias)
    .filter(subcategoria => datos.subcategoriasExcluidas.includes(subcategoria.codigo))

  return (
    <div className="preferencias-distribucion">
      <form className="preferencias-formulario" onSubmit={continuar} aria-label="Preferencias y gasto">
        {cargandoCatalogo && <p className="estado-catalogo" role="status">Cargando categorías y subcategorías…</p>}
        {errorCatalogo && <div className="error-viaje" role="alert"><p>{errorCatalogo}</p>
          <button type="button" className="enlace-preferencias" onClick={onReintentarCatalogo}>Reintentar carga del catálogo</button>
        </div>}

        <fieldset className="grupo-preferencias" disabled={!tieneCatalogo}>
          <legend>Tus intereses</legend>
          <p className="ayuda-preferencias" id="ayuda-intereses">Selecciona todo lo que te apetezca descubrir.</p>
          <div className="intereses-opciones">
            {catalogo.map(categoria => (
              <label className="opcion-interes" key={categoria.codigo} data-seleccionada={datos.intereses.includes(categoria.codigo) || undefined}>
                <span className="interes-icono" aria-hidden="true">{iconos[categoria.codigo] ?? '📍'}</span>
                <span>{categoria.nombre}</span>
                <input type="checkbox" name="intereses" value={categoria.codigo}
                  checked={datos.intereses.includes(categoria.codigo)} aria-describedby="ayuda-intereses"
                  onChange={evento => cambiarSeleccion('intereses', categoria.codigo, evento.target.checked)} />
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="grupo-preferencias" disabled={!tieneCatalogo}>
          <legend>Qué prefieres evitar</legend>
          <p className="ayuda-preferencias" id="ayuda-exclusiones">Puedes excluir actividades concretas de un interés. Una categoría completa no puede estar seleccionada a la vez como interés y como exclusión.</p>
          <div className="exclusiones-grupos">
            {catalogo.map(categoria => {
              const completa = datos.categoriasExcluidas.includes(categoria.codigo)
              const parciales = categoria.subcategorias.filter(subcategoria => datos.subcategoriasExcluidas.includes(subcategoria.codigo))
              return (
                <details className="exclusion-grupo" key={categoria.codigo}
                  open={gruposAbiertos.includes(categoria.codigo)}
                  onToggle={evento => cambiarGrupo(categoria.codigo, evento.currentTarget.open)}>
                  <summary><span>{categoria.nombre}</span><span className="exclusion-cantidad">{completa ? 'Categoría completa' : `${parciales.length} ${parciales.length === 1 ? 'seleccionada' : 'seleccionadas'}`}</span></summary>
                  <div className="exclusion-opciones">
                    <label className="exclusion-completa">
                      <input type="checkbox" name="categoriasExcluidas" value={categoria.codigo} checked={completa}
                        aria-describedby="ayuda-exclusiones error-preferencias"
                        onChange={evento => cambiarSeleccion('categoriasExcluidas', categoria.codigo, evento.target.checked)} />
                      <span>Evitar toda la categoría: {categoria.nombre}</span>
                    </label>
                    {categoria.subcategorias.map(subcategoria => (
                      <label className="exclusion-parcial" key={subcategoria.codigo}>
                        <input type="checkbox" name="subcategoriasExcluidas" value={subcategoria.codigo}
                          checked={datos.subcategoriasExcluidas.includes(subcategoria.codigo)} aria-describedby="ayuda-exclusiones"
                          onChange={evento => cambiarSeleccion('subcategoriasExcluidas', subcategoria.codigo, evento.target.checked)} />
                        <span>{subcategoria.nombre}</span>
                      </label>
                    ))}
                  </div>
                </details>
              )
            })}
          </div>
          {(categoriasElegidas.length > 0 || subcategoriasElegidas.length > 0) && (
            <div className="exclusiones-elegidas" aria-label="Exclusiones seleccionadas">
              <p className="ayuda-preferencias">Tus exclusiones siguen aquí aunque cambies los intereses:</p>
              <div className="evitar-opciones">
                {categoriasElegidas.map(categoria => <button type="button" className="exclusion-elegida" key={categoria.codigo}
                  aria-label={`Quitar exclusión de toda la categoría: ${categoria.nombre}`}
                  onClick={() => cambiarSeleccion('categoriasExcluidas', categoria.codigo, false)}>Categoría completa: {categoria.nombre} <span aria-hidden="true">×</span></button>)}
                {subcategoriasElegidas.map(subcategoria => <button type="button" className="exclusion-elegida" key={subcategoria.codigo}
                  aria-label={`Quitar exclusión: ${subcategoria.nombre}`}
                  onClick={() => cambiarSeleccion('subcategoriasExcluidas', subcategoria.codigo, false)}>{subcategoria.nombre} <span aria-hidden="true">×</span></button>)}
              </div>
            </div>
          )}
        </fieldset>

        <div className="estado-validacion" aria-live="polite" role="status">
          {comprobando ? 'Comprobando tu selección…' : puedeContinuar ? 'Selección comprobada. Puedes continuar.' : ''}
        </div>
        <div className="error-viaje" role="alert" id="error-preferencias" hidden={!actual?.error}>
          <p>{actual?.error}</p>
          <button type="button" className="enlace-preferencias" onClick={() => setReintento(valor => valor + 1)}>Volver a comprobar</button>
        </div>

        <fieldset className="grupo-preferencias">
          <legend>Otras preferencias</legend>
          <div className="evitar-opciones">
            {restricciones.map(opcion => <label className="opcion-evitar" key={opcion} data-seleccionada={datos.restricciones.includes(opcion) || undefined}>
              <input type="checkbox" name="restricciones" value={opcion} checked={datos.restricciones.includes(opcion)}
                onChange={evento => cambiarSeleccion('restricciones', opcion, evento.target.checked)} /><span>{opcion}</span>
            </label>)}
          </div>
          <p className="ayuda-preferencias">Estas opciones orientarán las recomendaciones; no son garantías absolutas.</p>
        </fieldset>

        <fieldset className="grupo-preferencias">
          <legend>Nivel de gasto</legend>
          <p className="ayuda-preferencias" id="ayuda-gasto">Este nivel orientará las recomendaciones. No es un presupuesto exacto.</p>
          <div className="gasto-opciones">
            {nivelesGasto.map(nivel => <label className="opcion-gasto" key={nivel.nombre} data-seleccionada={datos.nivelGasto === nivel.nombre || undefined}>
              <input type="radio" name="nivelGasto" value={nivel.nombre} checked={datos.nivelGasto === nivel.nombre}
                aria-describedby="ayuda-gasto" onChange={() => onCambiar({ ...datos, nivelGasto: nivel.nombre })} />
              <span><strong>{nivel.nombre}</strong><span className="gasto-descripcion">{nivel.descripcion}</span></span>
            </label>)}
          </div>
        </fieldset>

        <div className="grupo-preferencias distancia-preferencias">
          <label htmlFor="distancia-actividades">Distancia entre actividades</label>
          <p className="ayuda-preferencias" id="ayuda-distancia"><Icon name="informacion" />Se aplica entre una actividad y la siguiente, no a la distancia total del día.</p>
          <div className="distancia-control">
            <input id="distancia-actividades" name="distanciaKm" type="range" min={1} max={10} step={1}
              value={datos.distanciaKm} aria-describedby="ayuda-distancia" aria-valuetext={`${datos.distanciaKm} kilómetros entre actividades`}
              onChange={evento => onCambiar({ ...datos, distanciaKm: Number(evento.target.value) })} />
            <output htmlFor="distancia-actividades">{datos.distanciaKm} km</output>
          </div>
          <div className="distancia-limites" aria-hidden="true"><span>1 km</span><span>10 km</span></div>
        </div>

        <p className="nota-preferencias">Puedes cambiar estas opciones al volver a este paso. De momento forman parte de tu borrador.</p>
        <div className="acciones-preferencias">
          <button className="boton boton-secundario" type="button" onClick={onAtras}>← Atrás</button>
          <button className="boton boton-primario" type="submit" disabled={!puedeContinuar}>
            {comprobando ? 'Comprobando…' : 'Continuar'} <Icon name="derecha" />
          </button>
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

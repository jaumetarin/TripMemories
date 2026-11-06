import { useEffect, useMemo, useState } from 'react'
import type { SubmitEvent } from 'react'
import { validarPreferencias } from '../api'
import type { CategoriaPreferencia, Destino, FechaHora, OpcionesGastoDistancia, Preferencias, RitmoDiario } from '../api'
import { Icon } from '../assets/Icon'
import './PreferenciasGasto.css'

type Props = {
  destino: Destino
  disponibilidad: FechaHora
  alojamiento: string
  catalogo: CategoriaPreferencia[]
  opcionesGastoDistancia: OpcionesGastoDistancia | null
  cargandoCatalogo: boolean
  errorCatalogo: string
  onReintentarCatalogo: () => void
  datos: Preferencias
  onCambiar: (datos: Preferencias) => void
  ritmo: RitmoDiario
  onCambiarRitmo: (ritmo: RitmoDiario) => void
  onAtras: () => void
  onContinuar: (preferencias: Preferencias) => void
  editandoResumen?: boolean
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

function mostrarFecha(fechaHora: string) {
  const [anio, mes, dia] = fechaHora.split('T')[0].split('-')
  return `${dia}/${mes}/${anio.slice(-2)}`
}

export function PreferenciasGasto({ destino, disponibilidad, alojamiento, catalogo, cargandoCatalogo,
  opcionesGastoDistancia, errorCatalogo, onReintentarCatalogo, datos, onCambiar,
  ritmo, onCambiarRitmo, onAtras, onContinuar,
  editandoResumen = false }: Props) {
  const [validacion, setValidacion] = useState<Validacion | null>(null)
  const [reintento, setReintento] = useState(0)
  const [gruposAbiertos, setGruposAbiertos] = useState<string[]>(() => catalogo
    .filter(categoria => datos.intereses.includes(categoria.codigo)
      || datos.categoriasExcluidas.includes(categoria.codigo)
      || categoria.subcategorias.some(subcategoria => datos.subcategoriasExcluidas.includes(subcategoria.codigo)))
    .map(categoria => categoria.codigo))
  const tieneCatalogo = catalogo.length > 0 && opcionesGastoDistancia !== null && !cargandoCatalogo && !errorCatalogo

  // Las preferencias se validan por separado; el ritmo se comprueba al confirmar el viaje.
  const seleccion = useMemo<Preferencias>(() => ({
    intereses: datos.intereses,
    categoriasExcluidas: datos.categoriasExcluidas,
    subcategoriasExcluidas: datos.subcategoriasExcluidas,
    nivelGasto: datos.nivelGasto,
    distanciaKm: datos.distanciaKm,
  }), [datos.intereses, datos.categoriasExcluidas, datos.subcategoriasExcluidas, datos.nivelGasto, datos.distanciaKm])
  const actual = validacion?.seleccion === seleccion && validacion.intento === reintento ? validacion : null
  const comprobando = tieneCatalogo && (!actual || (!actual.resultado && !actual.error))
  const ritmoValido = ritmo.comidaInicio >= '10:00' && ritmo.comidaFin <= '18:00'
    && ritmo.comidaInicio < ritmo.comidaFin
    && Number.isInteger(ritmo.descansoCadaMinutos) && ritmo.descansoCadaMinutos >= 30 && ritmo.descansoCadaMinutos <= 480
    && Number.isInteger(ritmo.descansoDuracionMinutos) && ritmo.descansoDuracionMinutos >= 5 && ritmo.descansoDuracionMinutos <= 120
    && Number.isInteger(ritmo.margenMinutos) && ritmo.margenMinutos >= 0 && ritmo.margenMinutos <= 120
  const puedeContinuar = tieneCatalogo && Boolean(actual?.resultado) && ritmoValido

  useEffect(() => {
    if (!tieneCatalogo) return
    const controlador = new AbortController()
    let vigente = true
    // Evita una petición por cada posición al arrastrar el selector de distancia.
    const espera = window.setTimeout(() => {
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
    }, 200)
    // Las respuestas de selecciones anteriores nunca pueden sustituir a la actual.
    return () => { vigente = false; window.clearTimeout(espera); controlador.abort() }
  }, [seleccion, tieneCatalogo, reintento])

  function cambiarSeleccion(campo: 'intereses' | 'categoriasExcluidas' | 'subcategoriasExcluidas',
    codigo: string, marcada: boolean) {
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
        {cargandoCatalogo && <p className="estado-catalogo" role="status">Cargando opciones del viaje…</p>}
        {errorCatalogo && <div className="error-viaje" role="alert"><p>{errorCatalogo}</p>
          <button type="button" className="enlace-preferencias" onClick={onReintentarCatalogo}>Reintentar carga del catálogo</button>
        </div>}

        <fieldset className="grupo-preferencias" id="editar-intereses" tabIndex={-1} disabled={!tieneCatalogo}>
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

        <fieldset className="grupo-preferencias" id="editar-exclusiones" tabIndex={-1} disabled={!tieneCatalogo}>
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

        <fieldset className="grupo-preferencias" id="editar-gasto" tabIndex={-1} disabled={!tieneCatalogo}>
          <legend>Nivel de gasto</legend>
          <p className="ayuda-preferencias" id="ayuda-gasto">{opcionesGastoDistancia?.significadoGasto}</p>
          <div className="gasto-opciones">
            {opcionesGastoDistancia?.nivelesGasto.map(nivel => <label className="opcion-gasto" key={nivel.codigo} data-seleccionada={datos.nivelGasto === nivel.codigo || undefined}>
              <input type="radio" name="nivelGasto" value={nivel.codigo} checked={datos.nivelGasto === nivel.codigo}
                aria-describedby="ayuda-gasto error-preferencias" onChange={() => onCambiar({ ...datos, nivelGasto: nivel.codigo })} />
              <span><strong>{nivel.nombre}</strong><span className="gasto-descripcion">{nivel.descripcion}</span></span>
            </label>)}
          </div>
        </fieldset>

        <div className="grupo-preferencias distancia-preferencias">
          <label htmlFor="distancia-actividades">Distancia deseada entre actividades consecutivas</label>
          <p className="ayuda-preferencias" id="ayuda-distancia"><Icon name="informacion" />{opcionesGastoDistancia?.significadoDistancia}</p>
          <div className="distancia-control">
            <input id="distancia-actividades" name="distanciaKm" type="range"
              min={opcionesGastoDistancia?.distanciaMinimaKm} max={opcionesGastoDistancia?.distanciaMaximaKm}
              step={opcionesGastoDistancia?.pasoDistanciaKm} disabled={!tieneCatalogo}
              value={datos.distanciaKm ?? ''} aria-describedby="ayuda-distancia error-preferencias"
              aria-valuetext={`${datos.distanciaKm ?? 'Sin seleccionar'} kilómetros deseados entre actividades consecutivas`}
              onChange={evento => onCambiar({ ...datos, distanciaKm: Number(evento.target.value) })} />
            <output htmlFor="distancia-actividades">{datos.distanciaKm === null ? '—' : datos.distanciaKm + ' km'}</output>
          </div>
          <div className="distancia-limites" aria-hidden="true">
            <span>{opcionesGastoDistancia?.distanciaMinimaKm} km</span><span>{opcionesGastoDistancia?.distanciaMaximaKm} km</span>
          </div>
        </div>

        <fieldset className="grupo-preferencias" id="editar-ritmo" tabIndex={-1}>
          <legend>Comida, descansos y margen</legend>
          <p className="ayuda-preferencias" id="ayuda-ritmo">Puedes dejar estos valores como están. La franja de comida se reserva completa; las visitas y los trayectos a pie se ajustan al tiempo restante.</p>
          <div className="ritmo-campos">
            <label htmlFor="comida-inicio">Comida desde
              <input id="comida-inicio" type="time" min="10:00" max="18:00" required
                value={ritmo.comidaInicio} aria-describedby="ayuda-ritmo error-ritmo"
                onChange={evento => onCambiarRitmo({ ...ritmo, comidaInicio: evento.target.value })} />
            </label>
            <label htmlFor="comida-fin">Comida hasta
              <input id="comida-fin" type="time" min="10:00" max="18:00" required
                value={ritmo.comidaFin} aria-describedby="ayuda-ritmo error-ritmo"
                onChange={evento => onCambiarRitmo({ ...ritmo, comidaFin: evento.target.value })} />
            </label>
            <label htmlFor="descanso-cada">Descanso cada (minutos de actividad)
              <input id="descanso-cada" type="number" min="30" max="480" step="1" required
                value={ritmo.descansoCadaMinutos} aria-describedby="ayuda-ritmo error-ritmo"
                onChange={evento => onCambiarRitmo({ ...ritmo, descansoCadaMinutos: Number(evento.target.value) })} />
            </label>
            <label htmlFor="descanso-duracion">Duración del descanso (minutos)
              <input id="descanso-duracion" type="number" min="5" max="120" step="1" required
                value={ritmo.descansoDuracionMinutos} aria-describedby="ayuda-ritmo error-ritmo"
                onChange={evento => onCambiarRitmo({ ...ritmo, descansoDuracionMinutos: Number(evento.target.value) })} />
            </label>
            <label htmlFor="margen-minutos">Margen entre actividades (minutos)
              <input id="margen-minutos" type="number" min="0" max="120" step="1" required
                value={ritmo.margenMinutos} aria-describedby="ayuda-ritmo error-ritmo"
                onChange={evento => onCambiarRitmo({ ...ritmo, margenMinutos: Number(evento.target.value) })} />
            </label>
          </div>
          {!ritmoValido && <p className="error-viaje" id="error-ritmo" role="alert">Revisa el ritmo: la comida debe quedar entre las 10:00 y las 18:00, con el fin posterior al inicio. El descanso y el margen deben estar dentro de los límites indicados.</p>}
          <p className="ayuda-preferencias">El margen se añade al traslado calculado a pie. La comida también cuenta como descanso.</p>
        </fieldset>

        <div className="estado-validacion" aria-live="polite" role="status">
          {comprobando ? 'Comprobando tus preferencias…' : puedeContinuar ? 'Preferencias comprobadas. Puedes continuar.' : ''}
        </div>
        <div className="error-viaje" role="alert" id="error-preferencias" hidden={!actual?.error}>
          <p>{actual?.error}</p>
          <button type="button" className="enlace-preferencias" onClick={() => setReintento(valor => valor + 1)}>Volver a comprobar</button>
        </div>
        <p className="nota-preferencias">Puedes cambiar estas opciones al volver a este paso. De momento forman parte de tu borrador.</p>
        <div className="acciones-preferencias">
          <button className="boton boton-secundario" type="button" onClick={onAtras}>← Atrás</button>
          <button className="boton boton-primario" type="submit" disabled={!puedeContinuar}>
            {comprobando ? 'Comprobando…' : editandoResumen ? 'Guardar cambios y volver al resumen' : 'Continuar'} <Icon name="derecha" />
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
          <div><dt>Gasto orientativo</dt>
            <dd>{opcionesGastoDistancia?.nivelesGasto.find(nivel => nivel.codigo === datos.nivelGasto)?.nombre ?? 'Sin seleccionar'}
              <span>No es un presupuesto.</span>
            </dd>
          </div>
          <div><dt>Distancia deseada</dt>
            <dd>{datos.distanciaKm === null ? 'Sin seleccionar' : datos.distanciaKm + ' km'}
              <span>Entre actividades consecutivas, no por día.</span>
            </dd>
          </div>
          <div><dt>Comida</dt><dd>{ritmo.comidaInicio}–{ritmo.comidaFin}<span>Franja reservada completa.</span></dd></div>
          <div><dt>Descanso y margen</dt><dd>{ritmo.descansoDuracionMinutos} min cada {ritmo.descansoCadaMinutos} min de actividad<span>{ritmo.margenMinutos} min adicionales entre actividades.</span></dd></div>
        </dl>
        <p className="resumen-preferencias-nota">Un viaje a tu ritmo, desde el primer momento.</p>
      </aside>
    </div>
  )
}

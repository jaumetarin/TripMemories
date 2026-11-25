import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { actualizarViaje, guardarViaje } from '../viajes'
import type { DestinoGuardado } from '../viajes'
import '../pages/MisViajes.css'

type Props = { generacionId: string | null; tituloInicial: string; destino?: DestinoGuardado; onVerViajes: () => void; onGuardado: (viaje: DestinoGuardado) => void }
export function GuardarViaje({ generacionId, tituloInicial, destino, onVerViajes, onGuardado }: Props) {
  const [titulo, setTitulo] = useState(tituloInicial)
  const [guardado, setGuardado] = useState<DestinoGuardado | null>(destino ?? null)
  const [confirmado, setConfirmado] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')
  const peticion = useRef<AbortController | null>(null)
  const aviso = useRef<HTMLParagraphElement>(null)
  useEffect(() => () => peticion.current?.abort(), [])
  useEffect(() => { if (error) aviso.current?.focus() }, [error])
  const sinCambios = confirmado && titulo.trim() === guardado?.titulo
  async function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!generacionId || peticion.current || sinCambios) return
    const controlador = new AbortController()
    peticion.current = controlador
    setGuardando(true); setError('')
    try {
      const resultado = guardado
        ? await actualizarViaje(generacionId, guardado, titulo.trim(), controlador.signal)
        : { ...await guardarViaje(generacionId, titulo.trim(), controlador.signal), revision: 1 }
      if (controlador.signal.aborted) return
      const confirmado = { ...resultado, titulo: titulo.trim() }
      setGuardado(confirmado); onGuardado(confirmado); setTitulo(titulo.trim()); setConfirmado(true)
    } catch (fallo) {
      if (!controlador.signal.aborted) setError(fallo instanceof Error && fallo.name === 'Error' ? fallo.message
        : 'No hemos podido confirmar el guardado. Tus cambios siguen aquí. Comprueba Mis viajes antes de reintentar.')
    } finally { peticion.current = null; if (!controlador.signal.aborted) setGuardando(false) }
  }
  return <section className="guardar-viaje" aria-labelledby="guardar-viaje-titulo">
    <h2 id="guardar-viaje-titulo">{guardado ? 'Guardar cambios del viaje' : 'Guarda este viaje en tu cuenta'}</h2>
    <p>{sinCambios ? 'Viaje guardado. Puedes recuperarlo desde Mis viajes, incluso después de cerrar sesión.'
      : guardado ? 'Los cambios están pendientes. El viaje anterior se conserva hasta que confirmemos la actualización.'
      : 'Ponle un título para recuperarlo después sin volver a generarlo.'}</p>
    <form onSubmit={enviar}>
      <label htmlFor="titulo-viaje">Título del viaje</label>
      <input id="titulo-viaje" value={titulo} onChange={e => setTitulo(e.target.value)} required maxLength={160} disabled={guardando} />
      <div className="viajes-acciones">
        <button className="boton boton-primario" type="submit" disabled={guardando || sinCambios || !generacionId || !titulo.trim()}>
          {guardando ? 'Guardando…' : sinCambios ? 'Guardado' : guardado ? 'Guardar cambios' : 'Guardar viaje'}
        </button>
        <button className="boton" type="button" onClick={onVerViajes} disabled={guardando}>Ir a Mis viajes</button>
      </div>
    </form>
    {!generacionId && <p role="alert">Este resultado no tiene un identificador de guardado. Vuelve a generar una propuesta para guardarla.</p>}
    {error && <p className="viajes-error" role="alert" tabIndex={-1} ref={aviso}>{error}</p>}
    <p role="status" aria-live="polite">{guardando ? 'Estamos guardando tu viaje. No vuelvas a enviarlo.' : sinCambios ? 'Guardado confirmado.' : 'Cambios pendientes de guardar.'}</p>
  </section>
}

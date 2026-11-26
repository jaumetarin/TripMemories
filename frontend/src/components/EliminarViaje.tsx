import { useEffect, useRef, useState } from 'react'
import { eliminarViaje } from '../viajes'
import type { ResumenViaje } from '../viajes'
import './EliminarViaje.css'

export function EliminarViaje({ viaje, onCancelar, onEliminado }: {
  viaje: ResumenViaje; onCancelar: () => void; onEliminado: (recuerdos: boolean) => void
}) {
  const dialogo = useRef<HTMLDialogElement>(null)
  const peticion = useRef<AbortController | null>(null)
  const [recuerdos, setRecuerdos] = useState(!!viaje.eliminandoRecuerdos)
  const [ocupado, setOcupado] = useState(false), [error, setError] = useState('')
  useEffect(() => {
    const elemento = dialogo.current
    elemento?.showModal()
    return () => { peticion.current?.abort(); elemento?.close() }
  }, [])

  async function confirmar() {
    if (peticion.current) return
    const control = new AbortController(); peticion.current = control
    setOcupado(true); setError('')
    try {
      await eliminarViaje(viaje,recuerdos,control.signal)
      if (!control.signal.aborted) onEliminado(recuerdos)
    } catch (fallo) {
      if (!control.signal.aborted) setError(fallo instanceof Error && fallo.name === 'Error' ? fallo.message
        : 'No se ha podido confirmar la eliminación. Actualiza Mis viajes antes de reintentar.')
    } finally {
      if (!control.signal.aborted) { peticion.current = null; setOcupado(false) }
    }
  }

  return <dialog ref={dialogo} className="eliminar-viaje-dialogo" aria-labelledby="eliminar-viaje-titulo"
    onCancel={e => { if (ocupado) e.preventDefault(); else onCancelar() }}>
    <h2 id="eliminar-viaje-titulo">¿Eliminar este viaje?</h2>
    <p><strong>{viaje.titulo}</strong><br />{viaje.destino}</p>
    <p>Dejará de aparecer en «Mis viajes». Por defecto, conservaremos su álbum y las fotos en «Mis recuerdos». Si el álbum está vacío, se retirará también.</p>
    <label className="eliminar-viaje-opcion"><input type="checkbox" checked={recuerdos} disabled={ocupado || viaje.eliminandoRecuerdos}
      onChange={e => setRecuerdos(e.target.checked)} />Eliminar también el álbum y todas sus fotos</label>
    {recuerdos && <p className="eliminar-viaje-aviso">Las fotos se borrarán permanentemente de Rute. No podrás recuperarlas desde la aplicación.</p>}
    {error && <p className="viajes-error" role="alert">{error}</p>}
    {ocupado && <p role="status">{recuerdos ? 'Eliminando el viaje y sus recuerdos…' : 'Eliminando el viaje…'}</p>}
    <div className="viajes-acciones"><button className="boton" type="button" autoFocus disabled={ocupado} onClick={onCancelar}>Cancelar</button>
      <button className="boton eliminar-viaje-confirmar" type="button" disabled={ocupado} onClick={() => void confirmar()}>
        {ocupado ? 'Un momento…' : recuerdos ? 'Eliminar viaje y fotos' : 'Eliminar viaje y conservar recuerdos'}
      </button></div>
  </dialog>
}

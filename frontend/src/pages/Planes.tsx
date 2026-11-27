import { useEffect, useRef, useState } from 'react'
import { peticionApi } from '../sesion'
import './Planes.css'

type NombrePlan = 'FREE' | 'PREMIUM'
interface Plan { plan: NombrePlan; generacionesMes: number; generacionesHora: number; cuotaBytes: number }
interface PlanActual extends Plan { demoHabilitada: boolean }

function esPlan(dato: unknown): dato is Plan {
  if (typeof dato !== 'object' || dato === null) return false
  const p = dato as Record<string, unknown>
  return (p.plan === 'FREE' || p.plan === 'PREMIUM') && [p.generacionesMes, p.generacionesHora, p.cuotaBytes]
    .every(n => typeof n === 'number' && Number.isSafeInteger(n) && n > 0)
}
function esPlanActual(dato: unknown): dato is PlanActual {
  return esPlan(dato) && typeof (dato as unknown as Record<string, unknown>).demoHabilitada === 'boolean'
}
async function leer(ruta: string, plan?: NombrePlan): Promise<unknown> {
  const respuesta = await peticionApi(ruta, { method: plan ? 'POST' : 'GET', headers: { Accept: 'application/json', ...(plan ? { 'Content-Type': 'application/json' } : {}) }, body: plan ? JSON.stringify({ plan }) : undefined, signal: AbortSignal.timeout(10000) })
  const datos: unknown = await respuesta.json().catch(() => null)
  if (!respuesta.ok) throw new Error(typeof datos === 'object' && datos !== null && 'mensaje' in datos && typeof datos.mensaje === 'string' ? datos.mensaje : 'No se ha podido consultar el plan. Vuelve a intentarlo.')
  return datos
}

export function Planes({ usuarioId, onEntrar }: { usuarioId?: string; onEntrar: () => void }) {
  const [oferta, setOferta] = useState<Plan[]>([])
  const [actual, setActual] = useState<PlanActual | null>(null)
  const [error, setError] = useState('')
  const [cambiando, setCambiando] = useState(false)
  const [intento, setIntento] = useState(0)
  const [cargando, setCargando] = useState(true)
  const version = useRef(0)
  useEffect(() => {
    const consulta = ++version.current
    let vigente = true
    Promise.all([leer('/api/planes/oferta'), usuarioId ? leer('/api/planes/actual') : Promise.resolve(null)]).then(([planes, cuenta]) => {
      if (!Array.isArray(planes) || planes.length !== 2 || !planes.every(esPlan) || new Set(planes.map(p => p.plan)).size !== 2 || (usuarioId && !esPlanActual(cuenta))) throw new Error('El servidor ha devuelto una oferta de planes no válida.')
      if (vigente && consulta === version.current) { setOferta(planes); setActual(cuenta as PlanActual | null) }
    }).catch(e => { if (vigente && consulta === version.current) setError(e instanceof Error ? e.message : 'No se han podido cargar los planes.') })
      .finally(() => { if (vigente && consulta === version.current) setCargando(false) })
    const invalidar = () => { version.current++ }
    return () => { vigente = false; invalidar() }
  }, [usuarioId, intento])
  async function cambiar(plan: NombrePlan) {
    if (cargando || cambiando) return
    const cambio = ++version.current
    setCambiando(true); setError('')
    try {
      const respuesta = await leer('/api/planes/demo/cambiar', plan)
      if (!esPlanActual(respuesta) || respuesta.plan !== plan) throw new Error('No se ha podido confirmar el cambio de plan. Vuelve a consultar tu cuenta.')
      if (cambio === version.current) setActual(respuesta)
    } catch (e) {
      if (cambio !== version.current) return
      setActual(null)
      setError(e instanceof Error ? e.message : 'No se ha podido confirmar el cambio de plan.')
      // La petición puede haber llegado al servidor aunque se haya perdido la respuesta.
      try { const respuesta = await leer('/api/planes/actual'); if (cambio === version.current && esPlanActual(respuesta)) setActual(respuesta) } catch { /* Mantener el estado desconocido. */ }
    } finally { if (cambio === version.current) setCambiando(false) }
  }
  return <section className="planes" aria-labelledby="planes-titulo">
    <p className="antetitulo">ELIGE TU EXPERIENCIA</p><h1 id="planes-titulo">Compara los planes</h1>
    <p>Genera itinerarios y guarda los recuerdos de tus viajes. Los límites corresponden a tu cuenta.</p>
    {error && <p role="alert" className="error-viaje">{error}</p>}
    {error && <button className="boton" type="button" disabled={cambiando || cargando} onClick={() => { version.current++; setCargando(true); setError(''); setIntento(i => i + 1) }}>Reintentar consulta</button>}
    {cargando && <p role="status">Actualizando planes…</p>}
    <div className="planes-tarjetas">{oferta.map(p => <article className="plan-tarjeta" key={p.plan}>
      <h2>{p.plan === 'FREE' ? 'Free' : 'Premium'}</h2>
      {actual?.plan === p.plan && <p className="plan-actual">Tu plan actual</p>}
      <ul><li>{p.generacionesMes} generaciones al mes</li><li>{p.generacionesHora} generaciones por ventana móvil de 60 minutos</li><li>{p.cuotaBytes === 2147483648 ? '2 GiB' : `${p.cuotaBytes / 1048576} MiB`} para recuerdos</li></ul>
      {actual?.demoHabilitada && actual.plan !== p.plan && <button className="boton boton-primario" type="button" disabled={cambiando || cargando} onClick={() => void cambiar(p.plan)}>{cambiando ? 'Actualizando…' : `Cambiar a ${p.plan === 'FREE' ? 'Free' : 'Premium'} (demo)`}</button>}
    </article>)}</div>
    <p>Disponibles ahora: creación de itinerarios y álbum de recuerdos. El cambio de plan, cuando está habilitado, es una demostración.</p>
    <p>Previsto para Sprint 3: variantes, compartir y compra de Premium. Estas funciones aún no están disponibles.</p>
    {!usuarioId && <button className="boton" type="button" onClick={onEntrar}>Inicia sesión para ver tu plan</button>}
  </section>
}

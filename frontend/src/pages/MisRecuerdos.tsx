import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { Icon } from '../assets/Icon'
import { abrirAlbum, descargarFoto, eliminarAlbum, eliminarFoto, listarAlbumes, megabytes, subirFoto, urlMiniatura, urlOriginal } from '../recuerdos'
import type { Album, ContenidoAlbum, Foto } from '../recuerdos'
import './MisRecuerdos.css'

type Props = { viajeInicialId?: string; onVerViajes: () => void }
function fecha(valor: string | null) { return valor ? valor.split('-').reverse().join('/') : '' }
function ImagenFoto({ foto, portada = false }: { foto: Foto | { id: string; nombre: string }; portada?: boolean }) {
  const [fallida, setFallida] = useState(false)
  return fallida ? <span className="recuerdo-sin-imagen">No se puede cargar la foto. Actualiza el álbum.</span>
    : <img src={urlMiniatura(foto.id)} alt={portada ? '' : foto.nombre} loading="lazy" onError={() => setFallida(true)} />
}
function VisorFoto({ foto, onCerrar, onEliminar, anterior, siguiente }: {
  foto: Foto; onCerrar: () => void; onEliminar: () => void; anterior?: () => void; siguiente?: () => void
}) {
  const dialogo = useRef<HTMLDialogElement>(null)
  const peticion = useRef<AbortController | null>(null)
  const [cargando, setCargando] = useState(true), [fallida, setFallida] = useState(false)
  const [descargando, setDescargando] = useState(false), [mensaje, setMensaje] = useState('')
  useEffect(() => {
    const elemento = dialogo.current
    elemento?.showModal()
    return () => { peticion.current?.abort(); elemento?.close() }
  }, [])
  async function descargar() {
    if (peticion.current) return
    const control = new AbortController(); peticion.current = control
    setDescargando(true); setMensaje('')
    try { await descargarFoto(foto,control.signal); if (!control.signal.aborted) setMensaje('Descarga iniciada.') }
    catch (e) { if (!control.signal.aborted) setMensaje(e instanceof Error ? e.message : 'No se ha podido descargar la foto.') }
    finally { if (!control.signal.aborted) { setDescargando(false); peticion.current = null } }
  }
  return <dialog ref={dialogo} className="recuerdo-visor" aria-labelledby="visor-foto-titulo" onCancel={onCerrar}>
    <div className="visor-cabecera"><h2 id="visor-foto-titulo">{foto.nombre}</h2><button className="boton" type="button" autoFocus onClick={onCerrar}>Cerrar</button></div>
    <div className="visor-imagen">
      {cargando && !fallida && <p role="status">Cargando fotografía…</p>}
      {fallida ? <p role="alert">No se ha podido abrir esta foto. Cierra el visor y actualiza el álbum.</p>
        : <img src={urlOriginal(foto.id)} alt={foto.nombre} style={{ visibility: cargando ? 'hidden' : undefined }} onLoad={() => setCargando(false)} onError={() => { setFallida(true); setCargando(false) }} />}
    </div>
    <div className="visor-acciones"><button className="boton" type="button" disabled={!anterior || descargando} onClick={anterior}>← Anterior</button>
      <button className="boton" type="button" disabled={!siguiente || descargando} onClick={siguiente}>Siguiente →</button>
      <button className="boton recuerdo-peligro" type="button" disabled={descargando} onClick={onEliminar}>Eliminar foto</button>
      <button className="boton boton-primario" type="button" disabled={descargando} onClick={() => void descargar()}>{descargando ? 'Preparando descarga…' : 'Descargar original'}</button>
    </div><p className="recuerdo-mensaje" role="status">{mensaje}</p>
  </dialog>
}
function ConfirmarBorrado({ foto, ocupado, error, onCancelar, onConfirmar }: {
  foto: Foto; ocupado: boolean; error: string; onCancelar: () => void; onConfirmar: () => void
}) {
  const dialogo = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const elemento = dialogo.current
    elemento?.showModal()
    return () => elemento?.close()
  }, [])
  return <dialog ref={dialogo} className="recuerdo-confirmacion" aria-labelledby="borrar-foto-titulo"
    onCancel={e => { if (ocupado) e.preventDefault(); else onCancelar() }}>
    <h2 id="borrar-foto-titulo">¿Eliminar esta foto?</h2><p><strong>{foto.nombre}</strong></p>
    <p>Se retirará del álbum y dejará de estar disponible en Rute. Esta acción no se puede deshacer.</p>
    {error && <p className="recuerdos-error" role="alert">{error}</p>}
    <div className="visor-acciones"><button className="boton" type="button" autoFocus disabled={ocupado} onClick={onCancelar}>Cancelar</button>
      <button className="boton recuerdo-borrar" type="button" disabled={ocupado} onClick={onConfirmar}>{ocupado ? 'Eliminando…' : error ? 'Reintentar eliminación' : 'Eliminar definitivamente'}</button></div>
  </dialog>
}
function ConfirmarEliminarAlbum({ album, ocupado, error, onCancelar, onConfirmar }: {
  album: Album; ocupado: boolean; error: string; onCancelar: () => void; onConfirmar: () => void
}) {
  const dialogo = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const elemento = dialogo.current
    elemento?.showModal()
    return () => elemento?.close()
  }, [])
  return <dialog ref={dialogo} className="recuerdo-confirmacion" aria-labelledby="borrar-album-titulo"
    onCancel={e => { if (ocupado) e.preventDefault(); else onCancelar() }}>
    <h2 id="borrar-album-titulo">¿Eliminar este álbum?</h2>
    <p><strong>{album.titulo}</strong><br />Viaje: {album.viajeTitulo}</p>
    <p className="recuerdos-error"><strong>El álbum y todas sus fotos se eliminarán de forma permanente.</strong> Esta acción no se puede deshacer.</p>
    <p>El viaje se conservará. Las fotos que ya hayas descargado a tu dispositivo no se modificarán.</p>
    {error && <p className="recuerdos-error" role="alert">{error}</p>}
    {ocupado && <p role="status">Eliminando el álbum y sus fotos…</p>}
    <div className="visor-acciones"><button className="boton" type="button" autoFocus disabled={ocupado} onClick={onCancelar}>Cancelar</button>
      <button className="boton recuerdo-borrar" type="button" disabled={ocupado} onClick={onConfirmar}>
        {ocupado ? 'Eliminando…' : album.eliminando || error ? 'Reintentar eliminación' : 'Eliminar álbum y todas las fotos'}
      </button></div>
  </dialog>
}
export function MisRecuerdos({ viajeInicialId, onVerViajes }: Props) {
  const [albumes, setAlbumes] = useState<Album[]>([]), [contenido, setContenido] = useState<ContenidoAlbum | null>(null)
  const [cargando, setCargando] = useState(true), [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState(''), [mensaje, setMensaje] = useState(''), [errorBorrado, setErrorBorrado] = useState('')
  const [archivo, setArchivo] = useState<File | null>(null), [seleccion, setSeleccion] = useState<string | null>(null)
  const [borrando, setBorrando] = useState<Foto | null>(null)
  const [borrandoAlbum, setBorrandoAlbum] = useState<Album | null>(null), [errorAlbum, setErrorAlbum] = useState('')
  const lectura = useRef<AbortController | null>(null), operacion = useRef<AbortController | null>(null)
  const selector = useRef<HTMLInputElement>(null), titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    const control = new AbortController(); lectura.current = control
    listarAlbumes(control.signal).then(async lista => {
      if (control.signal.aborted) return
      setAlbumes(lista)
      const inicial = lista.find(a => a.viajeId === viajeInicialId)
      if (inicial) { const datos = await abrirAlbum(inicial.id,control.signal); if (!control.signal.aborted) setContenido(datos) }
      else if (viajeInicialId) setMensaje('Este viaje ya no tiene álbum. Puede que lo hayas eliminado.')
    }).catch(e => { if (!control.signal.aborted) setError(e instanceof Error ? e.message : 'No se pueden cargar tus recuerdos.') })
      .finally(() => { if (!control.signal.aborted) setCargando(false) })
    return () => { control.abort(); lectura.current?.abort(); operacion.current?.abort() }
  }, [viajeInicialId])
  useEffect(() => { titulo.current?.focus() }, [contenido?.album.id])
  async function abrir(id: string) {
    if (operacion.current) return
    lectura.current?.abort(); const control = new AbortController(); lectura.current = control
    setCargando(true); setError(''); setMensaje(''); setArchivo(null)
    try { const datos = await abrirAlbum(id,control.signal); if (!control.signal.aborted) setContenido(datos) }
    catch (e) { if (!control.signal.aborted) setError(e instanceof Error ? e.message : 'No se puede abrir el álbum.') }
    finally { if (!control.signal.aborted) setCargando(false) }
  }
  async function listar() {
    lectura.current?.abort(); const control = new AbortController(); lectura.current = control
    setCargando(true); setError('')
    try { const lista = await listarAlbumes(control.signal); if (!control.signal.aborted) setAlbumes(lista) }
    catch (e) { if (!control.signal.aborted) setError(e instanceof Error ? e.message : 'No se pueden cargar los álbumes.') }
    finally { if (!control.signal.aborted) setCargando(false) }
  }
  async function actualizar() {
    if (contenido) await abrir(contenido.album.id)
    else await listar()
  }
  function volver() {
    setContenido(null); setError(''); setMensaje(''); setArchivo(null)
    void listar()
  }
  async function subir(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!contenido || !archivo || operacion.current) return
    const control = new AbortController(); operacion.current = control
    setOcupado(true); setError(''); setMensaje('')
    try {
      const foto = await subirFoto(contenido.album.id,archivo,control.signal)
      if (control.signal.aborted) return
      setContenido(actual => actual ? { ...actual, fotos: [foto,...actual.fotos],
        bytesUsados: actual.bytesUsados + foto.bytesOriginal + foto.bytesMiniatura,
        album: { ...actual.album, fotos: actual.album.fotos + 1, portadaId: foto.id } } : actual)
      setArchivo(null); if (selector.current) selector.current.value = ''
      setMensaje('Foto guardada correctamente en este álbum.')
    } catch (e) {
      if (!control.signal.aborted) {
        setError(e instanceof Error ? e.message : 'No se ha podido confirmar la subida. Actualiza el álbum.')
        try { const datos = await abrirAlbum(contenido.album.id,control.signal); if (!control.signal.aborted) setContenido(datos) } catch { /* El error de subida sigue visible. */ }
      }
    } finally { if (!control.signal.aborted) { setOcupado(false); operacion.current = null } }
  }
  async function borrar() {
    if (!borrando || !contenido || operacion.current) return
    const foto = borrando, control = new AbortController(); operacion.current = control
    setOcupado(true); setErrorBorrado(''); setMensaje('')
    try {
      await eliminarFoto(foto.id,control.signal)
      if (control.signal.aborted) return
      setContenido(actual => {
        if (!actual) return actual
        const fotos = actual.fotos.filter(f => f.id !== foto.id), disponibles = fotos.filter(f => f.estado === 'DISPONIBLE')
        return { ...actual,fotos,bytesUsados: Math.max(0,actual.bytesUsados - foto.bytesOriginal - foto.bytesMiniatura),
          album: { ...actual.album,fotos: disponibles.length,portadaId: disponibles[0]?.id ?? null } }
      })
      setSeleccion(null); setBorrando(null); setMensaje('Foto eliminada correctamente.')
      requestAnimationFrame(() => titulo.current?.focus({ preventScroll: true }))
    } catch (e) {
      if (!control.signal.aborted) {
        setErrorBorrado(e instanceof Error ? e.message : 'No se ha podido confirmar la eliminación.')
        try { const datos = await abrirAlbum(contenido.album.id,control.signal); if (!control.signal.aborted) setContenido(datos) } catch { /* Mantener el aviso de fallo. */ }
      }
    } finally { if (!control.signal.aborted) { setOcupado(false); operacion.current = null } }
  }
  function confirmarAlbum(album: Album) { setBorrandoAlbum(album); setErrorAlbum('') }
  async function borrarAlbum() {
    if (!borrandoAlbum || operacion.current) return
    lectura.current?.abort(); setCargando(false)
    const album = borrandoAlbum, control = new AbortController(); operacion.current = control
    setOcupado(true); setErrorAlbum(''); setMensaje('')
    try {
      await eliminarAlbum(album.id,control.signal)
      if (control.signal.aborted) return
      setAlbumes(actual => actual.filter(a => a.id !== album.id))
      setContenido(null); setSeleccion(null); setBorrando(null); setBorrandoAlbum(null); setArchivo(null); setError('')
      setMensaje('Álbum y fotos eliminados correctamente. El viaje se ha conservado.')
      requestAnimationFrame(() => titulo.current?.focus({ preventScroll: true }))
    } catch (e) {
      if (!control.signal.aborted) {
        setErrorAlbum(e instanceof Error ? e.message : 'No se ha podido confirmar la eliminación. Actualiza los álbumes antes de reintentar.')
        try {
          const lista = await listarAlbumes(control.signal)
          if (!control.signal.aborted) setAlbumes(lista)
          if (contenido?.album.id === album.id) {
            const datos = await abrirAlbum(album.id,control.signal)
            if (!control.signal.aborted) { setContenido(datos); setArchivo(null) }
          }
        } catch { /* Conservar el aviso; no anunciar un borrado no confirmado. */ }
      }
    } finally { if (!control.signal.aborted) { setOcupado(false); operacion.current = null } }
  }
  const disponibles = contenido?.fotos.filter(f => f.estado === 'DISPONIBLE') ?? []
  const pendientes = contenido?.fotos.filter(f => f.estado !== 'DISPONIBLE') ?? []
  const indice = disponibles.findIndex(f => f.id === seleccion), foto = disponibles[indice]
  const archivoExcesivo = !!archivo && !!contenido && archivo.size > contenido.limites.maxArchivoBytes
  return <section className="mis-recuerdos" aria-labelledby="recuerdos-titulo">
    {contenido && <button className="boton-volver" type="button" disabled={ocupado} onClick={volver}>← Todos los álbumes</button>}
    <p className="antetitulo">TU COLECCIÓN PERSONAL</p>
    <h1 id="recuerdos-titulo" ref={titulo} tabIndex={-1}>{contenido ? contenido.album.titulo : 'Mis recuerdos.'}</h1>
    <p className="subtitulo">{contenido ? 'Los momentos de este viaje, en un solo lugar.' : 'Un álbum privado para cada viaje que guardas.'}</p>
    {error && <p className="recuerdos-error" role="alert">{error}</p>}{mensaje && <p className="recuerdos-confirmacion" role="status">{mensaje}</p>}
    <div className="recuerdos-herramientas"><span className="recuerdo-privado">Privado · Solo tú puedes ver estas fotos</span>
      <div className="recuerdos-acciones"><button className="boton" type="button" disabled={cargando || ocupado} onClick={() => void actualizar()}>Actualizar</button>
        {contenido && <button className="boton recuerdo-peligro" type="button" disabled={cargando || ocupado} onClick={() => confirmarAlbum(contenido.album)}>
          {contenido.album.eliminando ? 'Reintentar eliminación del álbum' : 'Eliminar álbum'}</button>}</div></div>
    {cargando ? <p className="recuerdos-carga" role="status">Cargando tus recuerdos…</p> : contenido ? <>
      <div className="recuerdo-viaje"><span className="etiqueta-viaje">{contenido.album.viajeEliminado ? 'Viaje eliminado: ' : 'Viaje: '}{contenido.album.viajeTitulo}</span><span>{contenido.album.destino} · {fecha(contenido.album.fechaInicio)}{contenido.album.fechaFin !== contenido.album.fechaInicio && ` — ${fecha(contenido.album.fechaFin)}`}</span></div>
      {contenido.album.eliminando ? <p className="recuerdos-error" role="alert">La eliminación del álbum está pendiente. Sus fotos están bloqueadas. Pulsa «Reintentar eliminación del álbum» para completarla.</p> : <>
      <form className="recuerdo-subida" onSubmit={subir} aria-busy={ocupado}>
        <div><h2>Añade una fotografía</h2><p>JPEG o PNG · Máximo {megabytes(contenido.limites.maxArchivoBytes)} y {Math.round(contenido.limites.maxPixeles / 1000000)} megapíxeles por foto.</p>
          <p>{disponibles.length} de {contenido.limites.maxFotosAlbum} fotos · Cuenta: {megabytes(contenido.bytesUsados)} de {megabytes(contenido.limites.cuotaBytes)} usados, incluidas miniaturas.</p></div>
        <label className="recuerdo-selector">Seleccionar una foto<input ref={selector} type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" disabled={ocupado} onChange={e => { setArchivo(e.target.files?.[0] ?? null); setError(''); setMensaje('') }} /></label>
        {archivo && <p className="recuerdo-seleccionada">Seleccionada: {archivo.name} · {megabytes(archivo.size)}. Todavía no está subida.</p>}
        {archivoExcesivo && <p className="recuerdos-error" role="alert">La foto supera el máximo de {megabytes(contenido.limites.maxArchivoBytes)}. Selecciona una imagen más pequeña.</p>}
        <button className="boton boton-primario" type="submit" disabled={ocupado || !archivo || archivoExcesivo}>{ocupado ? 'Procesando…' : 'Subir foto'}</button>
      </form>
      {pendientes.length > 0 && <div className="recuerdos-pendientes">{pendientes.map(f => <div key={f.id}><div><strong>{f.nombre}</strong><p>{f.estado === 'ELIMINANDO' ? 'Eliminación pendiente. El archivo ya no es accesible.' : f.estado === 'ERROR' ? 'Subida no completada. Esta foto no está disponible.' : 'Subida pendiente de confirmar. Si no termina, podrás retirarla pasados diez minutos.'}</p></div>
        <button className="boton" type="button" disabled={ocupado} onClick={() => { setBorrando(f); setErrorBorrado('') }}>{f.estado === 'ELIMINANDO' ? 'Reintentar eliminación' : 'Retirar subida pendiente'}</button></div>)}</div>}
      {disponibles.length === 0 ? <div className="recuerdos-vacio"><Icon name="album" /><h2>Este viaje aún no tiene fotos.</h2><p>Selecciona tu primera imagen y pulsa «Subir foto» para guardarla en este álbum.</p></div>
        : <ul className="recuerdos-galeria">{disponibles.map(f => <li key={f.id}><button type="button" className="foto-miniatura" aria-label={`Abrir foto ${f.nombre}`} onClick={() => setSeleccion(f.id)}><ImagenFoto foto={f} /><span>{f.nombre}</span></button></li>)}</ul>}
      </>}
    </> : albumes.length === 0 ? <div className="recuerdos-vacio"><Icon name="album" /><h2>Aún no tienes álbumes.</h2><p>Al guardar un nuevo viaje se creará su álbum privado. Los álbumes eliminados no se recuperan automáticamente.</p><button className="boton boton-primario" type="button" onClick={onVerViajes}>Ir a Mis viajes</button></div>
      : <ul className="recuerdos-albumes">{albumes.map(a => <li key={a.id}><button type="button" className="album-tarjeta" aria-label={`Abrir álbum de ${a.viajeTitulo}`} disabled={ocupado || a.eliminando} onClick={() => void abrir(a.id)}>
        <div className="album-portada">{a.portadaId ? <ImagenFoto key={a.portadaId} foto={{ id:a.portadaId,nombre:a.titulo }} portada /> : <Icon name="album" />}</div>
        <div className="album-descripcion"><h2>{a.titulo}</h2><span className="etiqueta-viaje">{a.viajeEliminado ? 'Viaje eliminado: ' : 'Viaje: '}{a.viajeTitulo}</span><p>{a.destino}</p><span>{a.eliminando ? 'Eliminación pendiente' : `${a.fotos} ${a.fotos === 1 ? 'foto' : 'fotos'} · Álbum privado`}</span></div></button>
        <button className="boton recuerdo-peligro album-borrar" type="button" disabled={ocupado} onClick={() => confirmarAlbum(a)} aria-label={`${a.eliminando ? 'Reintentar eliminación del álbum' : 'Eliminar álbum'} de ${a.viajeTitulo}`}>
          {a.eliminando ? 'Reintentar eliminación' : 'Eliminar álbum'}</button></li>)}</ul>}
    {foto && <VisorFoto key={foto.id} foto={foto} onCerrar={() => setSeleccion(null)} onEliminar={() => { setBorrando(foto); setErrorBorrado('') }}
      anterior={indice > 0 ? () => setSeleccion(disponibles[indice-1].id) : undefined} siguiente={indice < disponibles.length-1 ? () => setSeleccion(disponibles[indice+1].id) : undefined} />}
    {borrando && <ConfirmarBorrado foto={borrando} ocupado={ocupado} error={errorBorrado} onCancelar={() => setBorrando(null)} onConfirmar={() => void borrar()} />}
    {borrandoAlbum && <ConfirmarEliminarAlbum album={borrandoAlbum} ocupado={ocupado} error={errorAlbum} onCancelar={() => setBorrandoAlbum(null)} onConfirmar={() => void borrarAlbum()} />}
  </section>
}

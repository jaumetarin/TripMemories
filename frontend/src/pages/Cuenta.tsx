import { useEffect, useRef, useState } from 'react'
import type { SubmitEvent } from 'react'
import { ErrorCuenta, iniciarSesion, registrarCuenta, reenviarVerificacion, verificarCorreo } from '../sesion'
import type { UsuarioCuenta } from '../sesion'
import { Icon } from '../assets/Icon'
import './Cuenta.css'

export type ModoCuenta = 'login' | 'registro' | 'reenviar' | 'verificar'
type Props = { modo: ModoCuenta; token: string; onCambiar: (modo: ModoCuenta) => void; onEntrar: (usuario: UsuarioCuenta) => void }
const AVISO_CONTRASENA = 'Revisa los requisitos de la contraseña antes de crear tu cuenta.'

export function Cuenta({ modo, token, onCambiar, onEntrar }: Props) {
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [contrasenaRevisada, setContrasenaRevisada] = useState(false)
  const [confirmacion, setConfirmacion] = useState('')
  const [ocupado, setOcupado] = useState(false)
  const [mensaje, setMensaje] = useState('')
  const [error, setError] = useState('')
  const [campos, setCampos] = useState<Record<string, string>>({})
  const [completado, setCompletado] = useState(false)
  const errores = useRef<HTMLDivElement>(null)
  const enviando = useRef(false)
  const requisitosContrasena = [
    { texto: 'Entre 8 y 128 caracteres', cumple: contrasena.length >= 8 && contrasena.length <= 128 },
    { texto: 'Al menos una letra', cumple: /\p{L}/u.test(contrasena) },
    { texto: 'Al menos un número', cumple: /\p{Nd}/u.test(contrasena) },
    { texto: 'Al menos un símbolo, como !, @ o #', cumple: /[\p{P}\p{S}]/u.test(contrasena) },
  ]
  const contrasenaValida = requisitosContrasena.every(requisito => requisito.cumple)
  const mostrarRevision = modo === 'registro' && contrasenaRevisada

  useEffect(() => { if (error) errores.current?.focus() }, [error])

  async function enviar(evento: SubmitEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (enviando.current) return
    setError(''); setCampos({})
    if (modo === 'registro') {
      setContrasenaRevisada(true)
      if (!contrasenaValida) {
        setError(AVISO_CONTRASENA)
        return
      }
    }
    if (modo === 'registro' && contrasena !== confirmacion) {
      setCampos({ confirmacionContrasena: 'Las contraseñas no coinciden.' }); setError('Revisa la confirmación de la contraseña.'); return
    }
    enviando.current = true; setOcupado(true)
    try {
      if (modo === 'login') onEntrar(await iniciarSesion(correo, contrasena))
      else if (modo === 'registro') {
        await registrarCuenta(nombre, correo, contrasena, confirmacion)
        setMensaje('Si la solicitud puede completarse, recibirás un enlace de verificación. Revisa también el correo no deseado. Si ya tienes cuenta, inicia sesión.')
        setCompletado(true)
      } else if (modo === 'reenviar') {
        await reenviarVerificacion(correo)
        setMensaje('Si existe una solicitud pendiente para ese correo y puede reenviarse, recibirás otro enlace. Espera al menos un minuto entre solicitudes.')
        setCompletado(true)
      } else {
        await verificarCorreo(token, contrasena)
        setMensaje('Correo verificado. Tu cuenta de Rute está lista para iniciar sesión.')
        setCompletado(true)
      }
      setContrasena(''); setConfirmacion('')
    } catch (fallo) {
      if (fallo instanceof ErrorCuenta) { setError(fallo.message); setCampos(fallo.campos) }
      else setError(fallo instanceof Error && fallo.name === 'Error' ? fallo.message : 'No hemos podido conectar con el servidor. Vuelve a intentarlo.')
    } finally { enviando.current = false; setOcupado(false) }
  }

  const titulo = completado ? modo === 'verificar' ? 'Tu próximo viaje empieza aquí.' : 'Revisa tu correo.'
    : modo === 'registro' ? 'Empieza tu próxima historia.' : modo === 'verificar' ? 'Confirma que eres tú.' : modo === 'reenviar' ? 'Un nuevo enlace para ti.' : 'Qué bien tenerte de vuelta.'
  function errorCampo(campo: string) { return campos[campo] ? <p className="cuenta-error-campo" id={'error-' + campo}>{campos[campo]}</p> : null }

  return <section className="cuenta" aria-labelledby="cuenta-titulo">
    <div className="cuenta-presentacion"><span className="cuenta-simbolo"><Icon name="brujula" /></span>
      <p className="antetitulo">TU CUENTA DE RUTE</p><h1 id="cuenta-titulo">{titulo}</h1>
      <p>{modo === 'registro' ? 'Un lugar para planear tus viajes y guardar lo que merece quedarse.'
        : modo === 'verificar' ? 'Verifica tu dirección antes de empezar. Solo tendrás que hacerlo una vez.'
        : modo === 'reenviar' ? 'Te enviaremos otro enlace si tu correo está pendiente de verificación.' : 'Inicia sesión para dar forma a tu próximo viaje.'}</p>
      <div className="cuenta-promesa"><Icon name="mapa" /><span>Tu destino. Tus planes. A tu ritmo.</span></div>
    </div>
    <div className="cuenta-panel">
      {completado ? <div className="cuenta-confirmacion" role="status"><span className="cuenta-confirmacion-icono">✓</span><h2>{modo === 'verificar' ? 'Cuenta verificada' : 'Comprueba tu buzón'}</h2>
        <p>{mensaje}</p>{modo !== 'verificar' && <p>Los enlaces caducan a los 30 minutos. Abre el más reciente y confirma con la contraseña que elegiste al registrarte.</p>}
        <button className="boton boton-primario" type="button" onClick={() => onCambiar('login')}>Ir a iniciar sesión <Icon name="flecha" /></button>
        {modo !== 'verificar' && <button className="cuenta-enlace" type="button" onClick={() => onCambiar('reenviar')}>No me ha llegado el correo</button>}
      </div> : <>
        {modo === 'login' || modo === 'registro' ? <nav className="cuenta-pestanas" aria-label="Acceso a Rute">
          <button type="button" aria-current={modo === 'login' ? 'page' : undefined} disabled={ocupado} onClick={() => onCambiar('login')}>Iniciar sesión</button>
          <button type="button" aria-current={modo === 'registro' ? 'page' : undefined} disabled={ocupado} onClick={() => onCambiar('registro')}>Crear cuenta</button>
        </nav> : <h2>{modo === 'verificar' ? 'Verificar correo' : 'Reenviar verificación'}</h2>}
        <form onSubmit={enviar} noValidate>
          <fieldset disabled={ocupado}>
            <legend className="solo-lectores">{modo === 'registro' ? 'Datos de registro' : 'Datos de acceso'}</legend>
            {modo === 'registro' && <div className="cuenta-campo"><label htmlFor="cuenta-nombre">Nombre</label>
              <input id="cuenta-nombre" value={nombre} onChange={e => setNombre(e.target.value)} autoComplete="name" maxLength={120} required
                aria-invalid={!!campos.nombre} aria-describedby={campos.nombre ? 'error-nombre' : undefined} />{errorCampo('nombre')}</div>}
            {modo !== 'verificar' && <div className="cuenta-campo"><label htmlFor="cuenta-correo">Correo electrónico</label>
              <input id="cuenta-correo" type="email" value={correo} onChange={e => setCorreo(e.target.value)} autoComplete="email" maxLength={255} required
                aria-invalid={!!campos.correo} aria-describedby={campos.correo ? 'error-correo' : undefined} />{errorCampo('correo')}</div>}
            {modo !== 'reenviar' && <div className="cuenta-campo"><label htmlFor="cuenta-contrasena">{modo === 'verificar' ? 'Contraseña elegida al registrarte' : 'Contraseña'}</label>
              <input id="cuenta-contrasena" type="password" value={contrasena} onChange={e => {
                setContrasena(e.target.value)
                if (error === AVISO_CONTRASENA) setError('')
                if (campos.contrasena) setCampos({ ...campos, contrasena: '' })
              }} onBlur={modo === 'registro' ? () => setContrasenaRevisada(true) : undefined}
                autoComplete={modo === 'registro' ? 'new-password' : 'current-password'} minLength={modo === 'registro' ? 8 : undefined}
                maxLength={128} required aria-invalid={!!campos.contrasena || mostrarRevision && !contrasenaValida}
                aria-describedby={[
                  modo === 'registro' ? 'cuenta-regla-contrasena' : '',
                  mostrarRevision ? 'cuenta-estado-contrasena' : '',
                  campos.contrasena ? 'error-contrasena' : '',
                ].filter(Boolean).join(' ') || undefined} />
              {modo === 'registro' && <ul className="cuenta-reglas" id="cuenta-regla-contrasena" aria-label="Requisitos de la contraseña">
                {requisitosContrasena.map(requisito => <li key={requisito.texto}
                  className={mostrarRevision ? requisito.cumple ? 'cuenta-regla-cumplida' : 'cuenta-regla-pendiente' : undefined}>
                  <span aria-hidden="true">{mostrarRevision ? requisito.cumple ? '✓' : '×' : '•'}</span>
                  <span>{requisito.texto}{mostrarRevision && <span className="solo-lectores">{requisito.cumple ? ': cumplido' : ': pendiente'}</span>}</span>
                </li>)}
              </ul>}
              {mostrarRevision && <p id="cuenta-estado-contrasena" role="status"
                className={contrasenaValida ? 'cuenta-contrasena-valida' : 'cuenta-error-campo'}>
                {contrasenaValida ? 'La contraseña cumple los requisitos.' : 'Completa los requisitos pendientes. Los espacios no cuentan como símbolo.'}
              </p>}
              {errorCampo('contrasena')}</div>}
            {modo === 'registro' && <div className="cuenta-campo"><label htmlFor="cuenta-confirmacion">Confirmar contraseña</label>
              <input id="cuenta-confirmacion" type="password" value={confirmacion} onChange={e => setConfirmacion(e.target.value)} autoComplete="new-password" maxLength={128} required
                aria-invalid={!!campos.confirmacionContrasena} aria-describedby={campos.confirmacionContrasena ? 'error-confirmacionContrasena' : undefined} />{errorCampo('confirmacionContrasena')}</div>}
          </fieldset>
          {error && <div className="cuenta-error" ref={errores} tabIndex={-1} role="alert">{error}</div>}
          <button className="boton boton-primario cuenta-enviar" type="submit" disabled={ocupado || modo === 'verificar' && !token}>
            {ocupado ? 'Un momento…' : modo === 'registro' ? 'Crear mi cuenta' : modo === 'verificar' ? 'Verificar mi correo' : modo === 'reenviar' ? 'Enviar nuevo enlace' : 'Entrar en Rute'}
            {!ocupado && <Icon name="flecha" />}</button>
          <p className="cuenta-ayuda" role="status">{ocupado ? 'Estamos comprobando tu solicitud. No hace falta volver a enviarla.'
            : modo === 'registro' ? 'Necesitarás verificar tu correo antes de iniciar sesión.' : modo === 'verificar' ? 'Si no recuerdas haber solicitado esta cuenta, no la actives.' : ''}</p>
        </form>
        {modo !== 'registro' && <button type="button" className="cuenta-enlace" disabled={ocupado} onClick={() => onCambiar(modo === 'reenviar' ? 'registro' : 'reenviar')}>
          {modo === 'reenviar' ? 'Volver a crear cuenta' : 'Reenviar correo de verificación'}</button>}
        {(modo === 'verificar' || modo === 'reenviar') && <button type="button" className="cuenta-enlace" disabled={ocupado} onClick={() => onCambiar('login')}>Volver a iniciar sesión</button>}
      </>}
    </div>
  </section>
}

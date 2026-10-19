import { useRef, useState } from 'react'
import { Icon } from './graphics/Icon'
import { Home } from './components/Home'
import './App.css'

type Pantalla = 'inicio' | 'crear' | 'viajes' | 'recuerdos'

function App() {
  const [pantalla, setPantalla] = useState<Pantalla>('inicio')
  const [destinoTexto, setDestinoTexto] = useState('')
  const contenido = useRef<HTMLElement>(null)

  function navegar(destino: Pantalla) {
    setPantalla(destino)
    if (destino !== 'crear') setDestinoTexto('')
    contenido.current?.focus()
    window.scrollTo({ top: 0, behavior: 'instant' })
  }

  const crearItinerario = () => navegar('crear')

  return (
    <>
      <a className="saltar-contenido" href="#contenido">Saltar al contenido</a>
      <header className="cabecera">
        <div className="cabecera-interior">
          <button className="marca" type="button" onClick={() => navegar('inicio')} aria-label="TripMemories, inicio">
            <span className="simbolo-marca"><Icon name="ubicacion" /></span>
            <span>TripMemories</span>
          </button>
          <nav className="navegacion" aria-label="Navegación principal">
            <button type="button" onClick={() => navegar('inicio')} aria-current={pantalla === 'inicio' ? 'page' : undefined}>Inicio</button>
            <button type="button" onClick={() => navegar('viajes')} aria-current={pantalla === 'viajes' ? 'page' : undefined}>Mis viajes</button>
            <button type="button" onClick={() => navegar('recuerdos')} aria-current={pantalla === 'recuerdos' ? 'page' : undefined}>Mis recuerdos</button>
          </nav>
          <div className="acciones-cabecera">
            <button className="boton boton-cabecera" type="button" onClick={crearItinerario} aria-label="Crear itinerario" aria-current={pantalla === 'crear' ? 'page' : undefined}>
              <Icon name="sumar" /><span>Crear itinerario</span>
            </button>
            <button className="avatar" type="button" disabled aria-label="Mi perfil, próximamente" title="Mi perfil, próximamente"><Icon name="usuario" /></button>
          </div>
        </div>
      </header>

      <main className="contenido" id="contenido" ref={contenido} tabIndex={-1}>
        {pantalla === 'inicio' && (
          <Home
            onCrearItinerario={crearItinerario}
            onVerViajes={() => navegar('viajes')}
            onVerRecuerdos={() => navegar('recuerdos')}
          />
        )}

        {pantalla === 'crear' && (
          <section className="crear-viaje" aria-labelledby="crear-titulo">
            <button className="boton-volver" type="button" onClick={() => navegar('inicio')}>← Volver al inicio</button>
            <div className="crear-intro">
              <p className="antetitulo">TU PRÓXIMA HISTORIA</p>
              <h1 id="crear-titulo">Empecemos por el destino.</h1>
              <p className="subtitulo">Los grandes viajes empiezan con unos pequeños detalles.</p>
            </div>
            <div className="crear-distribucion">
              <nav className="acompanamiento" aria-label="Etapas del itinerario">
                <ol className="pasos">
                  <li aria-current="step"><span>1</span><div><strong>El destino</strong><p>Dónde y cuándo.</p></div></li>
                  <li><span>2</span><div><strong>Tus preferencias</strong><p>Un viaje a tu medida.</p></div></li>
                  <li><span>3</span><div><strong>Tu itinerario</strong><p>Todo listo para explorar.</p></div></li>
                </ol>
              </nav>
              <div className="formulario-viaje">
                <div className="titulo-formulario"><span className="icono-coleccion"><Icon name="ubicacion" /></span><div><h2>Tu viaje</h2><p>Elige dónde y cuándo.</p></div></div>
                <div>
                  <label htmlFor="destino">Destino</label>
                  <input
                    id="destino"
                    name="destino"
                    type="text"
                    maxLength={120}
                    placeholder="¿Qué lugar te gustaría descubrir?"
                    value={destinoTexto}
                    onChange={event => setDestinoTexto(event.target.value)}
                  />
                </div>
                <div className="fechas">
                  <div><label htmlFor="inicio">Fecha de inicio</label><input id="inicio" name="fechaInicio" type="date" /></div>
                  <div><label htmlFor="fin">Fecha de fin</label><input id="fin" name="fechaFin" type="date" /></div>
                </div>
                <p className="aviso-formulario" id="aviso-guardar">Próximamente podrás continuar y guardar tu viaje. Por ahora, los datos de esta pantalla no se guardan.</p>
                <button className="boton boton-primario continuar" type="button" disabled aria-describedby="aviso-guardar">Continuar <Icon name="flecha" /></button>
              </div>
            </div>
          </section>
        )}

        {(pantalla === 'viajes' || pantalla === 'recuerdos') && (
          <section className="vista-lista" aria-labelledby="titulo-lista">
            <button className="boton-volver" type="button" onClick={() => navegar('inicio')}>← Volver al inicio</button>
            <p className="antetitulo">TU COLECCIÓN PERSONAL</p>
            <h1 id="titulo-lista">{pantalla === 'viajes' ? 'Mis viajes.' : 'Mis recuerdos.'}</h1>
            <p className="subtitulo">{pantalla === 'viajes' ? 'Lugares por descubrir. Historias por escribir.' : 'Hay momentos que merecen quedarse.'}</p>
            <div className="estado-vacio">
              <span className={`ilustracion-vacia${pantalla === 'recuerdos' ? ' icono-ambar' : ''}`}><Icon name={pantalla === 'viajes' ? 'mapa' : 'album'} /></span>
              <h2>{pantalla === 'viajes' ? 'Tu primer destino te espera.' : 'El principio de muchos recuerdos.'}</h2>
              <p>{pantalla === 'viajes' ? 'Aquí tendrás tus próximos viajes y los que ya forman parte de tu historia.' : 'Tus fotos y vídeos se reunirán aquí, organizados en álbumes de cada viaje.'}</p>
              <button className="boton boton-primario" type="button" onClick={crearItinerario}>Crear un itinerario <Icon name="flecha" /></button>
            </div>
          </section>
        )}
      </main>
      <footer className="pie-pagina"><span>TripMemories</span><span>Planea. Viaja. Recuerda.</span></footer>
    </>
  )
}

export default App

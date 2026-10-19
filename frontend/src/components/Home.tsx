import { Icon } from '../graphics/Icon'
import { TravelIllustration } from '../graphics/TravelIllustration'

type HomeProps = {
  onCrearItinerario: () => void
  onVerViajes: () => void
  onVerRecuerdos: () => void
}

export function Home({ onCrearItinerario, onVerViajes, onVerRecuerdos }: HomeProps) {
  return (
    <>
      <section className="bienvenida" aria-labelledby="titulo-inicio">
        <div>
          <p className="antetitulo">TU ESPACIO PARA VIAJAR</p>
          <h1 id="titulo-inicio">Lo mejor está por venir.</h1>
          <p className="subtitulo">Un lugar para planear tus viajes. Y volver a vivirlos.</p>
        </div>
        <span className="sello-bienvenida">
          <span aria-hidden="true" /> Una nueva aventura te espera
        </span>
      </section>

      <section className="hero-viaje" aria-labelledby="titulo-aventura">
        <div className="hero-texto">
          <span className="insignia"><Icon name="brujula" /> Tu próximo capítulo</span>
          <h2 id="titulo-aventura">Todo empieza<br />con un destino.</h2>
          <p>Ese lugar que tienes en mente puede ser tu próxima gran historia. Empieza a darle forma.</p>
          <button className="boton boton-primario" type="button" onClick={onCrearItinerario}>
            Crear mi itinerario <Icon name="flecha" />
          </button>
          <span className="hero-nota">A tu ritmo. A tu manera.</span>
        </div>
        <div className="hero-ilustracion"><TravelIllustration /></div>
      </section>

      <section className="colecciones" aria-labelledby="titulo-colecciones">
        <div className="titulo-seccion">
          <h2 id="titulo-colecciones">Tu mundo, en un solo lugar.</h2>
          <p>Cada destino. Cada recuerdo.</p>
        </div>
        <div className="tarjetas-coleccion">
          <button className="tarjeta-coleccion" type="button" onClick={onVerViajes}>
            <span className="icono-coleccion"><Icon name="maleta" /></span>
            <span className="texto-coleccion">
              <strong>Mis viajes</strong>
              <span>De la primera idea al último día.</span>
              <span className="enlace-tarjeta">Explorar mis viajes <Icon name="derecha" /></span>
            </span>
            <span className="mini-ruta" aria-hidden="true"><span /><i /><span /></span>
          </button>
          <button className="tarjeta-coleccion" type="button" onClick={onVerRecuerdos}>
            <span className="icono-coleccion icono-ambar"><Icon name="album" /></span>
            <span className="texto-coleccion">
              <strong>Mis recuerdos</strong>
              <span>Los momentos que se quedan contigo.</span>
              <span className="enlace-tarjeta">Abrir mis recuerdos <Icon name="derecha" /></span>
            </span>
            <span className="mini-album" aria-hidden="true"><span /><span /><span /></span>
          </button>
        </div>
      </section>
    </>
  )
}

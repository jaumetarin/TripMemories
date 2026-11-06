import type { BloqueItinerario } from '../api'

// Datos de presentación: el backend ya ha elegido las actividades y su orden.
export interface VisitaMapa {
  clave: string
  lugarId: string
  numero: number
  nombre: string
  direccion: string | null
  inicio: string
  fin: string
  posicion: [number, number] | null
}

export interface UbicacionVisitas {
  posicion: [number, number]
  visitas: VisitaMapa[]
}

export function horaLocal(valor: string): string {
  return valor.includes('T') ? valor.split('T')[1].slice(0, 5) : valor.slice(0, 5)
}

export function visitasDelDia(bloques: readonly BloqueItinerario[]): VisitaMapa[] {
  return [...bloques].sort((a, b) => a.inicio.localeCompare(b.inicio))
    .filter(bloque => bloque.tipo === 'VISITA' && bloque.candidato !== null)
    .map((bloque, indice): VisitaMapa => {
      const lugar = bloque.candidato!.lugar
      const { latitud, longitud } = lugar
      const ubicacionValida = latitud !== null && longitud !== null
        && Number.isFinite(latitud) && Number.isFinite(longitud)
        && Math.abs(latitud) <= 90 && Math.abs(longitud) <= 180
      return {
        clave: JSON.stringify([bloque.inicio, lugar.id]), lugarId: lugar.id, numero: indice + 1,
        nombre: lugar.nombre, direccion: lugar.direccion, inicio: bloque.inicio, fin: bloque.fin,
        posicion: ubicacionValida ? [latitud, longitud] : null,
      }
    })
}

export function agruparUbicaciones(visitas: readonly VisitaMapa[]): UbicacionVisitas[] {
  const grupos = new Map<string, UbicacionVisitas>()
  for (const visita of visitas) {
    if (visita.posicion === null) continue
    const clave = visita.posicion.join(',')
    const grupo = grupos.get(clave)
    if (grupo) grupo.visitas.push(visita)
    else grupos.set(clave, { posicion: visita.posicion, visitas: [visita] })
  }
  return [...grupos.values()]
}

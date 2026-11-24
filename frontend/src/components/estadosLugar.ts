import type { EstadoLugar } from '../api'
export const estadosLugar: EstadoLugar[] = ['OBLIGATORIO', 'PREFERIDO', 'EVITADO']

export const etiquetasEstado: Record<EstadoLugar, string> = {
  OBLIGATORIO: 'Obligatorio', PREFERIDO: 'Preferido', EVITADO: 'Evitado',
}
export const simbolosEstado: Record<EstadoLugar, string> = { OBLIGATORIO: '!', PREFERIDO: '★', EVITADO: '⊘' }

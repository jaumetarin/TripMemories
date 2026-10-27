import type { ReactNode } from 'react'

export type IconName = 'ubicacion' | 'brujula' | 'album' | 'sumar' | 'usuario' | 'flecha' | 'derecha' | 'mapa' | 'maleta' | 'candado' | 'casa' | 'informacion'

const trazos: Record<IconName, ReactNode> = {
  ubicacion: <><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
  brujula: <><circle cx="12" cy="12" r="9" /><path d="m16 8-2.5 5.5L8 16l2.5-5.5L16 8Z" /></>,
  album: <><rect x="4" y="3" width="16" height="18" rx="2" /><circle cx="10" cy="8" r="1.5" /><path d="m5 17 5-5 3 3 3-4 4 6M4 18h16" /></>,
  sumar: <path d="M12 5v14M5 12h14" />,
  usuario: <><circle cx="12" cy="7" r="3" /><path d="M5 21v-3a7 7 0 0 1 14 0v3" /></>,
  flecha: <path d="M4 12h16m-6-6 6 6-6 6" />,
  derecha: <path d="m9 6 6 6-6 6" />,
  mapa: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15" /><path d="m5.5 10 1 1m4-2 1 1m5 4 2-2" /></>,
  maleta: <><rect x="3" y="7" width="18" height="14" rx="3" /><path d="M8 7V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v3M8 11v6m8-6v6" /></>,
  candado: <><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></>,
  casa: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V10Z" /><path d="M9 21v-8h6v8" /></>,
  informacion: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6m0-10v1" /></>,
}

export function Icon({ name }: { name: IconName }) {
  return <svg className="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{trazos[name]}</svg>
}

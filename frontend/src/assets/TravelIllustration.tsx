/** Decorative artwork: no destination, route or travel data is represented. */
export function TravelIllustration() {
  return (
    <svg className="ilustracion-viaje" viewBox="0 0 560 430" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="fondo-mapa" x1="80" y1="40" x2="440" y2="390" gradientUnits="userSpaceOnUse"><stop stopColor="#fff" /><stop offset="1" stopColor="#f0f8f3" /></linearGradient>
        <linearGradient id="agua" x1="340" y1="90" x2="240" y2="420" gradientUnits="userSpaceOnUse"><stop stopColor="#d2eae6" /><stop offset="1" stopColor="#b9dcd6" /></linearGradient>
        <linearGradient id="pin" x1="300" y1="100" x2="360" y2="190" gradientUnits="userSpaceOnUse"><stop stopColor="#3fae9b" /><stop offset="1" stopColor="#087a6a" /></linearGradient>
        <filter id="sombra-mapa" x="-30%" y="-30%" width="170%" height="180%"><feDropShadow dx="0" dy="22" stdDeviation="18" floodColor="#234d44" floodOpacity=".12" /></filter>
        <filter id="sombra-pin" x="-60%" y="-40%" width="220%" height="220%"><feDropShadow dx="0" dy="8" stdDeviation="7" floodColor="#176b5a" floodOpacity=".2" /></filter>
        <clipPath id="recorte-mapa"><rect x="102" y="66" width="342" height="290" rx="24" /></clipPath>
      </defs>
      <circle cx="289" cy="214" r="176" fill="#dfeee5" fillOpacity=".45" />
      <circle cx="289" cy="214" r="202" stroke="#cadfd3" strokeOpacity=".35" />
      <g transform="rotate(-9 273 211)" filter="url(#sombra-mapa)">
        <rect x="90" y="54" width="366" height="314" rx="34" fill="white" />
        <g clipPath="url(#recorte-mapa)">
          <rect x="102" y="66" width="342" height="290" fill="url(#fondo-mapa)" />
          <path d="M359 50c-64 57-26 92-67 137s-32 91-64 117-26 56-46 77h262V50H359Z" fill="url(#agua)" />
          <path d="m98 119 121 30 51-71M102 234l101-37 54 57M135 360l69-163 15-48 75 21M173 72l4 67m-60 190 109-22" stroke="white" strokeWidth="11" strokeLinejoin="round" />
          <path d="m98 119 121 30 51-71M102 234l101-37 54 57M135 360l69-163 15-48 75 21" stroke="#e5e7dd" strokeWidth="1" />
          <rect x="116" y="159" width="47" height="33" rx="12" transform="rotate(14 116 159)" fill="#dcebcf" />
          <rect x="236" y="91" width="36" height="40" rx="11" transform="rotate(25 236 91)" fill="#deebd4" />
          <path d="M153 285c1-38 80-21 76-65s-6-39 33-54 70 15 81-31" stroke="white" strokeWidth="8" strokeLinecap="round" />
          <path d="M153 285c1-38 80-21 76-65s-6-39 33-54 70 15 81-31" stroke="#278e7d" strokeWidth="3" strokeLinecap="round" strokeDasharray="5 8" />
          <circle cx="153" cy="285" r="10" fill="white" /><circle cx="153" cy="285" r="5" fill="#278e7d" />
          <circle cx="229" cy="221" r="7" fill="white" stroke="#278e7d" strokeWidth="2" />
        </g>
      </g>
      <g filter="url(#sombra-pin)">
        <path d="M346 104c-22 0-40 17-40 39 0 29 40 59 40 59s40-30 40-59c0-22-18-39-40-39Z" fill="url(#pin)" />
        <circle cx="346" cy="143" r="14" fill="#f9fffc" /><circle cx="346" cy="143" r="6" fill="#d7efe5" />
      </g>
      <g transform="rotate(8 414 299)" filter="url(#sombra-mapa)">
        <rect x="338" y="251" width="146" height="106" rx="20" fill="white" />
        <rect x="353" y="266" width="116" height="57" rx="11" fill="#e6eee1" />
        <circle cx="446" cy="280" r="7" fill="#f4d89c" />
        <path d="m353 313 27-30 21 24 20-16 48 32H353v-10Z" fill="#9cb9a5" /><path d="m383 323 36-21 50 21h-86Z" fill="#739c8e" />
        <rect x="356" y="336" width="48" height="4" rx="2" fill="#ced8d2" /><rect x="410" y="336" width="23" height="4" rx="2" fill="#e4e9e5" />
      </g>
      <g transform="rotate(-7 130 135)">
        <rect x="57" y="110" width="177" height="47" rx="23.5" fill="white" />
        <circle cx="82" cy="134" r="12" fill="#edf6ef" />
        <path d="m77 134 4 4 7-8" stroke="#298571" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        <text x="102" y="139" fill="#3c665b" fontSize="12" fontFamily="system-ui, sans-serif" fontWeight="600">Tu próxima aventura</text>
      </g>
    </svg>
  )
}

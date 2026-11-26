# AGENTS.md — Guía del proyecto Rute (TripMemories)

Este archivo resume el contexto técnico del repositorio y las instrucciones de trabajo de la rama `avd` para cualquier persona o agente de IA que colabore en el proyecto.

---

## 1. Qué es este proyecto

**Rute** (repositorio, paquete Java y base de datos `tripmemories`) es una aplicación web desarrollada por un equipo de cinco estudiantes de la Universitat Politècnica de València (UPV) para:
- Planificar itinerarios turísticos diarios personalizados (entre 1 y 10 días) con desplazamientos a pie, pausas de comida, descansos, márgenes y mapa sincronizado.
- Gestionar cuentas de usuario, guardar/editar viajes propios y conservar álbumes privados con fotografías de recuerdos.

Documentación de referencia en el repositorio:
- [`README.md`](README.md): documentación funcional y técnica acumulada (UT1–UT31).
- [`docs/continuidad-planificador-ut09-ut16.md`](docs/continuidad-planificador-ut09-ut16.md): relevo técnico histórico del planificador.
- [`docs/avd-pin-requisitos.md`](docs/avd-pin-requisitos.md): especificaciones, diapositivas y rúbricas completas para la memoria de **AVD-PIN** en esta rama (`avd`).

---

## 2. Arquitectura y tecnologías

```text
TripMemories/
├── frontend/        # React 19 + TypeScript + Vite 8 + Leaflet / React-Leaflet
├── backend/         # Java 25 + Spring Boot 4.1.2 + Spring Security + JDBC + Flyway
├── docs/            # Documentación técnica y guías de trabajo
├── compose.yaml     # PostgreSQL 18 + PostGIS 3.6 para desarrollo local
└── README.md
```

### Frontend (`frontend/`)
- **Entry point y enrutado interno**: [`frontend/src/App.tsx`](frontend/src/App.tsx) (pantallas `inicio`, `crear`, `viajes`, `recuerdos`, `perfil`, `cuenta`).
- **Comunicación HTTP**: [`frontend/src/api.ts`](frontend/src/api.ts), [`sesion.ts`](frontend/src/sesion.ts), [`viajes.ts`](frontend/src/viajes.ts), [`edicion.ts`](frontend/src/edicion.ts), [`trazados.ts`](frontend/src/trazados.ts), [`recuerdos.ts`](frontend/src/recuerdos.ts).
- **Proxy de desarrollo** ([`frontend/vite.config.ts`](frontend/vite.config.ts)): redirige `/api` a `http://localhost:8080`.
- **Mapas**: `leaflet` y `react-leaflet` con teselas de OpenStreetMap por defecto (`VITE_MAP_TILES_URL` opcional).

### Backend (`backend/`, paquete raíz `es.upv.pin.backend`)
- `controller/`: endpoints REST bajo `/api/**`.
- `service/`: lógica de negocio, planificación heurística (`GeneracionItinerarioService`, `RepartoVisitas`, `ValidadorPlanService`), edición de itinerarios, álbumes y autenticación.
- `client/`: clientes HTTP externos (`GeoapifyClient`, `GeminiClient`, `TavilyResearchClient`, `OpenRouteServiceClient`, `BrevoClient`).
- `repository/`: acceso a PostgreSQL con JDBC y consultas parametrizadas (`UsuarioRepository`, `ViajeRepository`, `AlbumRepository`).
- `model/` y `dto/`: entidades, enumerados y DTOs de entrada/salida.
- `config/` y `exception/`: seguridad (`SecurityConfig`) y mapeo global de errores HTTP (`*ExceptionHandler`).
- **Base de datos y migraciones**: PostgreSQL 18 + PostGIS 3.6 con migraciones Flyway `V1`–`V8` en `backend/src/main/resources/db/migration/` y datos semilla de desarrollo en `backend/src/main/resources/db/seed/dev.sql`.

---

## 3. Reglas técnicas y de seguridad

1. **Claves y secretos solo en backend**: las variables `GEOAPIFY_API_KEY`, `GEMINI_API_KEY`, `TAVILY_API_KEY`, `ORS_API_KEY` y `BREVO_API_KEY` se leen desde variables de entorno en `application.properties`. Nunca deben guardarse en código, Git ni variables `VITE_*`.
2. **Seguridad HTTP**: todas las rutas `/api/**` requieren sesión (`JSESSIONID`), excepto `/api/auth/csrf`, `/api/auth/registro`, `/api/auth/login`, `/api/auth/reenviar`, `/api/auth/verificar` y `/api/auth/logout`. Las operaciones de escritura (`POST`, `PUT`, `DELETE`) requieren cabecera `X-CSRF-TOKEN`.
3. **Migraciones inmutables**: no modificar archivos `V1__*.sql`..`V8__*.sql` ya existentes; cualquier cambio de esquema requiere una nueva migración `V9__...`.
4. **Sin datos inventados**: si una API externa falla o no aporta precio, horario o ruta, se conserva como desconocido o no disponible (con aviso), nunca se rellena con valores ficticios.

---

## 4. Comandos útiles

### Base de datos local
```powershell
docker compose up -d --wait db
```

### Backend (desde `backend/`)
```powershell
# Arrancar servidor (puerto 8080)
.\mvnw.cmd spring-boot:run

# Ejecutar toda la suite (requiere Docker para pruebas de integración con Testcontainers)
.\mvnw.cmd test

# Ejecutar pruebas sin PostgreSQL/Flyway
.\mvnw.cmd clean '-Dspring.flyway.enabled=false' test
```

### Frontend (desde `frontend/`)
```powershell
npm install
npm run dev
npm run lint
npm run build
```

---

## 5. Contexto de la rama `avd` (Memoria AVD-PIN)

Esta rama local está dedicada a **documentación y pruebas para la asignatura AVD-PIN**.
Antes de redactar la memoria o diseñar/ejecutar pruebas para la entrega, consultar [`docs/avd-pin-requisitos.md`](docs/avd-pin-requisitos.md), donde están recogidos:
- La estructura obligatoria de la memoria PDF (Portada, Introducción, Plan de pruebas/validación, Validación de RF, Validación de RNF, Conclusiones, Bibliografía, Anexos).
- Los errores comunes señalados en la **Sesión 5** (*PROYECTO AVD-PIN 2*).
- Las rúbricas oficiales de evaluación (**Entrega 1** y **Entrega 2**).
- La lista de documentación y datos pendientes de completar por el equipo.

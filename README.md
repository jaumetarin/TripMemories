# TripMemories

Aplicación web para planificar itinerarios de viaje y guardar recuerdos asociados a viajes, actividades y lugares. Proyecto universitario desarrollado por un equipo de cinco estudiantes de la Universitat Politècnica de València.

## Tecnologías

- **Frontend:** React, TypeScript, Vite y Leaflet.
- **Backend:** Java 25 y Spring Boot 4.1.1 (Maven).
- **Base de datos prevista:** PostgreSQL con PostGIS.

## Estructura del repositorio

```text
TripMemories/
├── frontend/    # Aplicación React
└── backend/     # API Spring Boot
```

## Requisitos para desarrollar

- Git
- Java JDK 25
- Node.js 24 y npm
- Visual Studio Code o un editor equivalente

No hace falta instalar Maven por separado: el backend incluye Maven Wrapper (`mvnw` y `mvnw.cmd`). La base de datos se configurará durante la UT de implantación y poblado.

## Arranque local

Abre dos terminales desde la raíz del repositorio.

**Frontend:**

```bash
cd frontend
npm install
npm run dev
```

Vite mostrará la URL local, normalmente <http://localhost:5173/>.

**Backend (Git Bash):**

```bash
cd backend
./mvnw spring-boot:run
```

En PowerShell, usa `.\mvnw.cmd spring-boot:run` dentro de `backend`. Por defecto, Spring Boot escucha en <http://localhost:8080/>. Hasta que se añadan endpoints, esa dirección puede mostrar un error 404 aunque el servidor se haya iniciado correctamente.

Para detener cualquiera de los servidores, pulsa `Ctrl+C` en su terminal.

## Estado del proyecto

- Repositorio inicializado y proyectos frontend y backend generados.
- Modelo de dominio definido para usuarios, viajes, preferencias, itinerarios, actividades, álbumes y archivos multimedia.
- Pendiente: implantación del modelo en PostgreSQL/PostGIS y datos iniciales.

# TripMemories

Aplicación web para planificar itinerarios de viaje y guardar recuerdos asociados a viajes, actividades y lugares. Proyecto universitario desarrollado por un equipo de cinco estudiantes de la Universitat Politècnica de València.

## Tecnologías

- **Frontend:** React, TypeScript, Vite y Leaflet.
- **Backend:** Java 25, Spring Boot 4.1.1 y Maven Wrapper.
- **Base de datos:** PostgreSQL 18 con PostGIS 3.6, ejecutada con Docker Compose.
- **Migraciones:** Flyway.

## Estructura

```text
TripMemories/
├── frontend/                         # Aplicación React
├── backend/                          # API Spring Boot
│   └── src/main/resources/db/
│       ├── migration/                # Esquema aplicado por Flyway
│       └── seed/                     # Datos de ejemplo para desarrollo
├── compose.yaml                      # PostgreSQL/PostGIS local
└── README.md
```

## Requisitos

- Git
- Java JDK 25
- Node.js 24 (incluye npm)
- Docker Desktop en funcionamiento
- Visual Studio Code o un editor equivalente

No hace falta instalar Maven por separado: el backend incluye `mvnw` para Git Bash y `mvnw.cmd` para PowerShell.

## Arranque local

Ejecuta estos comandos desde la carpeta raíz del repositorio. Mantén una terminal abierta para el backend y otra para el frontend.

### 1. Base de datos

```bash
docker compose up -d --wait db
```

La base local se llama `tripmemories`, usa el usuario `tripmemories`, la contraseña de desarrollo `tripmemories_dev` y escucha en `localhost:5432`. Docker conserva sus datos en un volumen persistente.

### 2. Backend

En Git Bash:

```bash
cd backend
./mvnw spring-boot:run
```

En PowerShell, desde `backend`, ejecuta `.\mvnw.cmd spring-boot:run`. El backend escucha en <http://localhost:8080/>. Flyway aplica automáticamente las migraciones pendientes de `backend/src/main/resources/db/migration/` al arrancar. Un 404 en la raíz es normal mientras no haya endpoints definidos.

### 3. Frontend

En otra terminal, desde la raíz del repositorio:

```bash
cd frontend
npm install
npm run dev
```

Vite mostrará la URL local, normalmente <http://localhost:5173/>.

## Datos de ejemplo

Después de que el backend haya arrancado y Flyway haya creado las tablas, ejecuta **una vez** desde la raíz:

```bash
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U tripmemories -d tripmemories < backend/src/main/resources/db/seed/dev.sql
```

La cuenta de ejemplo es `demo@tripmemories.local` con contraseña `Demo1234!`. El archivo `dev.sql` es solo para desarrollo y puede ejecutarse de nuevo sin duplicar los registros de ejemplo. Sus rutas `demo://` son marcadores; todavía no corresponden a fotos ni vídeos físicos.

## Modelo de datos

El esquema incluye siete tablas: `usuario`, `viaje`, `preferencias`, `itinerario`, `actividad`, `album` y `archivo_multimedia`. Los viajes pueden ser planificados o externos. Las fotos y vídeos de un álbum pueden asociarse a una actividad del mismo viaje o guardar un lugar libre cuando no existe actividad. Los identificadores externos de lugares no son claves foráneas locales.

La migración inicial está en `backend/src/main/resources/db/migration/V1__crear_modelo.sql`. Para cambios posteriores del esquema, añade otra migración versionada; no modifiques V1 una vez aplicada a una base de datos compartida.

## Detener los servicios

Pulsa `Ctrl+C` en las terminales del backend y del frontend. Para detener la base:

```bash
docker compose down
```

Este comando conserva el volumen con los datos de PostgreSQL.

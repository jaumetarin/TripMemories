# TripMemories

Aplicación web para planificar itinerarios de viaje y guardar recuerdos asociados a viajes, actividades y lugares. Proyecto universitario desarrollado por un equipo de cinco estudiantes de la Universitat Politècnica de València.

## Tecnologías

- **Frontend:** React, TypeScript, Vite y Leaflet.
- **Backend:** Java 25, Spring Boot 4.1.2 y Maven Wrapper.
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

## Organización del frontend

El código de `frontend/src` se organiza en tres carpetas:

```text
src/
├── pages/                # Pantallas: Home y CrearItinerario, con su CSS al lado
├── components/           # Piezas de la interfaz, como BuscadorDestinos
├── assets/               # Iconos, ilustraciones y tripmemories.svg
├── api.ts                # Llamadas al backend y tipos de sus respuestas
├── App.tsx               # Estructura general y navegación entre pantallas
├── App.css               # Estilos compartidos de la aplicación
├── main.tsx              # Punto de entrada de React
└── index.css             # Estilos base
```

`api.ts` reúne la comunicación HTTP con nuestro backend. Las reglas de negocio y
la conexión con Geoapify permanecen en el backend.

## Organización del backend

El código Java se organiza por capas dentro de `es.upv.pin.backend`:

| Paquete | Responsabilidad |
| --- | --- |
| `controller` | Endpoints HTTP; reciben peticiones y delegan en los servicios. |
| `service` | Reglas de negocio y coordinación de las operaciones. |
| `repository` | Acceso a PostgreSQL; reservado para las funcionalidades que lo necesiten. |
| `model` | Objetos del dominio, como User o Trip; se añadirán al implementar esas funcionalidades. |
| `dto` | Datos de entrada y salida de la API. |
| `client` | Comunicación con APIs externas, como Geoapify. |
| `config` | Configuración e inicialización de dependencias. |
| `exception` | Excepciones y su conversión a respuestas HTTP. |

`BackendApplication` permanece en el paquete raíz para que Spring detecte todas las
capas. Los paquetes `model` y `repository` incluyen un `package-info.java` que documenta
su propósito. Las pruebas reproducen la estructura de paquetes en `src/test/java`.

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

## Formulario de itinerario

El campo Destino busca ciudades reales al escribir al menos tres caracteres.
Espera una pausa de 350 ms y muestra hasta cinco sugerencias con su región y país.
Puedes elegirlas con el ratón, tocando la pantalla o con las flechas y Enter; Escape
cierra la lista. La selección conserva el identificador y las coordenadas del lugar.
Si editas el nombre, tendrás que seleccionar de nuevo un resultado.

La comunicación sigue este recorrido:

```text
BuscadorDestinos → GET /api/destinos?texto=Mad → DestinoController
                → DestinoService → GeoapifyClient → Geoapify
```

Configura `GEOAPIFY_API_KEY` **en la terminal del backend antes de arrancarlo**.
En PowerShell puedes introducirla sin mostrarla en pantalla ni guardarla en el historial:

```powershell
$env:GEOAPIFY_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave de Geoapify' -AsSecureString)).Password
.\mvnw.cmd spring-boot:run
```

En Git Bash, dentro de `backend`:

```bash
read -rsp 'Clave de Geoapify: ' GEOAPIFY_API_KEY
export GEOAPIFY_API_KEY
./mvnw spring-boot:run
```

Si ya tienes la variable configurada en esa terminal, solo arranca el backend.
La clave permanece en el servidor: no la pongas en archivos versionados ni en
variables `VITE_*`. Cada terminal nueva necesita disponer de la variable.

Arranca también el frontend y reinicia Vite si estaba abierto antes de estos cambios:
el proxy de desarrollo redirige `/api` a `http://localhost:8080`, sin necesitar CORS.
En un futuro despliegue habrá que redirigir `/api` al backend desde el alojamiento.
El buscador comunica los errores de conexión, de configuración y del proveedor.
La generación y el guardado del itinerario siguen pendientes.

### Calendario y duración del viaje

Tras seleccionar un destino, elige las fechas en los dos calendarios y pulsa
**Continuar**. El backend valida `POST /api/itinerarios/validar-fechas` y devuelve
las fechas y su duración inclusiva: cuenta el primer día y el último. La misma
fecha en ambos campos equivale a un día; del 1 al 10 equivale a diez días.

Los campos incompletos, el fin anterior al inicio y los intervalos de más de diez
días muestran un mensaje y bloquean el avance. El calendario permite introducir
estos intervalos para poder corregirlos con las explicaciones del backend.
Los datos se conservan al corregir un error y al pulsar **Atrás** desde el paso
de horarios. Cambiar de sección abandona este borrador, que aún no se guarda.

La duración enviada por el navegador se ignora: `FechasService` la calcula con
`LocalDate`, sin depender de horas, zonas horarias ni cambios de horario.

Comprobación manual, con un destino seleccionado:

| Inicio | Fin | Resultado esperado al continuar |
| --- | --- | --- |
| Vacío | 2026-11-01 | Solicita una fecha de inicio |
| 2026-11-01 | Vacío | Solicita una fecha de fin |
| 2026-11-10 | 2026-11-01 | Explica que el fin debe ser igual o posterior al inicio |
| 2026-11-01 | 2026-11-11 | Explica el límite de diez días |
| 2026-11-01 | 2026-11-01 | Avanza a horarios con un día |
| 2026-11-01 | 2026-11-10 | Avanza a horarios con diez días |

### Horarios y alojamiento

El segundo paso permite indicar llegada el primer día y salida el último, con
horas iniciales de 10:00 y 18:00. El alojamiento es texto libre opcional de hasta
250 caracteres; no se geocodifica ni se busca automáticamente. **Continuar sin
alojamiento** vacía ese dato y avanza al resumen.

El resumen lateral muestra destino, duración inclusiva, fechas y horario. El
botón **Atrás** permite volver al calendario sin perder los datos de este paso.
Este paso es únicamente una interfaz de frontend: sus valores se mantienen en el
estado de React, sin enviarse al backend ni validarse con reglas de negocio.
Al continuar se muestra una confirmación editable. La validación de fechas del
primer paso permanece en el backend. Los pasos posteriores
(preferencias, lugares y generación) y el guardado del viaje están pendientes.

Para ejecutar las pruebas del calendario y del autocompletado sin base de datos
ni clave real, desde `backend`:

```powershell
.\mvnw.cmd '-Dtest=FechasServiceTests,FechasControllerTests,GeoapifyClientTests,DestinoServiceTests,DestinoControllerTests' test
```

### Comprobar el autocompletado

Con el backend arrancado, abre <http://localhost:8080/api/destinos?texto=Mad>.
Debe devolver una lista JSON; después prueba el campo Destino de la web.
Las pruebas del cliente, servicio y endpoint usan respuestas simuladas y no necesitan
una clave real ni PostgreSQL. Desde `backend`, en PowerShell:

```powershell
.\mvnw.cmd '-Dtest=GeoapifyClientTests,DestinoServiceTests,DestinoControllerTests' test
```

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

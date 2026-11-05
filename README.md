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

### Destino, fechas y horas

La primera pantalla reúne destino, fecha y hora de llegada, fecha y hora de salida
y alojamiento opcional. Cada pareja fecha/hora se envía como una fecha y hora
local del destino, sin conversiones a UTC. El navegador solo recoge los campos y
presenta las respuestas; las reglas de disponibilidad se comprueban en el backend.

Los campos y el resumen muestran las fechas en `dd/mm/yy`, por ejemplo `01/11/26`.
Puedes escribirlas o usar el botón del calendario. Al escribir dos cifras de año
se interpretan como 2000–2099; también se acepta el año completo. En móvil puedes
introducir los números sin barras y se formatean al salir del campo. La API sigue
recibiendo la fecha completa en formato ISO (`2026-11-01T10:00`).

```text
CrearItinerario → POST /api/itinerarios/validar-fecha-hora
               → FechaHoraController → FechaHoraService → FechaHoraDto
```

Ejemplo de petición (la duración se omite porque la calcula el servicio):

```json
{
  "llegada": "2026-11-01T10:00",
  "salida": "2026-11-01T18:00"
}
```

`FechaHoraDto` sirve para la entrada y la respuesta. Devuelve `llegada`, `salida`
y `duracionDias`. La salida debe ser estrictamente posterior a la llegada,
comparando fecha y hora conjuntamente. Las fechas cuentan de forma inclusiva:
del 1 al 1 es un día; del 1 al 10 son diez días. La duración que pudiera enviar
el navegador se ignora y se recalcula con las fechas del calendario.

Los campos incompletos, las ventanas vacías o invertidas y los intervalos de más
de diez días bloquean el avance con un mensaje que permite corregirlos. Al
continuar se abre **Preferencias y gasto**, con un resumen lateral de los datos
validados. **Atrás** regresa al formulario conservando sus valores y el destino.
El alojamiento es texto libre opcional de hasta 250 caracteres, mantenido solo
en el frontend. Cambiar de sección o recargar abandona el borrador sin guardar.

| Llegada | Salida | Resultado esperado al continuar |
| --- | --- | --- |
| Fecha u hora pendiente | 2026-11-01 18:00 | Solicita completar la llegada |
| 2026-11-01 10:00 | Fecha u hora pendiente | Solicita completar la salida |
| 2026-11-01 18:00 | 2026-11-01 10:00 | Rechaza la salida anterior |
| 2026-11-01 10:00 | 2026-11-01 10:00 | Rechaza la ventana vacía |
| 2026-11-01 23:00 | 2026-11-02 07:00 | Acepta; dos días inclusivos |
| 2026-11-01 10:00 | 2026-11-01 18:00 | Acepta; un día |
| 2026-11-01 10:00 | 2026-11-10 18:00 | Acepta; diez días |
| 2026-11-01 23:59 | 2026-11-11 00:00 | Rechaza; once días inclusivos |

`FechaHoraService.validarActividad` permite comprobar que una actividad tenga
un intervalo positivo y quede completamente entre llegada y salida. Puede
empezar exactamente a la llegada o acabar exactamente a la salida. Esta regla
está probada y preparada para usarse en el futuro generador. La selección de
lugares, la generación y el guardado siguen pendientes.
No se ha creado todavía un plan de actividades ni una persistencia del borrador.

Para ejecutar las pruebas de disponibilidad y autocompletado sin base de datos
ni clave real, desde `backend`:

```powershell
.\mvnw.cmd '-Dtest=PreferenciasServiceTests,PreferenciasControllerTests,FechaHoraServiceTests,FechaHoraControllerTests,GeoapifyClientTests,DestinoServiceTests,DestinoControllerTests' test
```

### Intereses y exclusiones

`PreferenciasGasto` obtiene su catálogo del backend: seis categorías turísticas y
dieciocho subcategorías, tres por categoría. `CategoriaTuristica` define los
intereses y `SubcategoriaTuristica` relaciona cada exclusión parcial con su
categoría principal. Sus códigos estables se usan en las peticiones; sus nombres
legibles se muestran en la web.

| Endpoint | Función |
| --- | --- |
| `GET /api/itinerarios/catalogo-preferencias` | Devuelve códigos, nombres y subcategorías agrupadas. |
| `POST /api/itinerarios/validar-preferencias` | Comprueba las listas, gasto y distancia; devuelve la configuración validada. |
| `GET /api/itinerarios/opciones-gasto-distancia` | Devuelve niveles, límites, valores iniciales y significado de gasto y distancia. |

`PreferenciasDto` sirve para entrada y salida. Ejemplo compatible:

```json
{
  "intereses": ["GASTRONOMIA", "NATURALEZA_PARQUES"],
  "categoriasExcluidas": ["ARTE_MUSEOS"],
  "subcategoriasExcluidas": ["COMIDA_RAPIDA", "SENDERISMO"],
  "nivelGasto": "MODERADO",
  "distanciaKm": 3
}
```

`PreferenciasService.validar` elimina duplicados manteniendo el orden y rechaza
las listas ausentes, los elementos nulos y las contradicciones. Las listas vacías
son válidas. Los códigos desconocidos o que pertenecen al tipo equivocado producen
un error HTTP 400, al igual que un JSON incorrecto.

Excluir una subcategoría de un interés es compatible. Seleccionar una categoría
como interés y excluirla por completo produce un error con su nombre y pide
desmarcar una de las dos opciones. No se elimina ninguna selección automáticamente.
Excluir las tres subcategorías disponibles no implica por sí solo excluir toda
la categoría: la exclusión completa es siempre una selección explícita.

El frontend comprueba las selecciones al cambiarlas y solo permite abrir el resumen
con la respuesta válida correspondiente a la selección actual. Mientras espera o
hay errores, **Continuar** permanece bloqueado. Las respuestas antiguas se descartan.
Si falla la conexión, el borrador se conserva y se puede reintentar la comprobación.
Si falla el catálogo, se ofrece reintentar su carga y no se avanza sin validación.

Las exclusiones están agrupadas en apartados desplegables y las seleccionadas
también aparecen como etiquetas que se pueden retirar. Siguen visibles aunque
se desmarque el interés principal. El estado se mantiene en `CrearItinerario`,
por lo que ir al resumen o volver al primer paso conserva las preferencias, el
destino, las fechas, las horas, el alojamiento, el gasto y la distancia.

**Vida nocturna** forma parte de las exclusiones de ocio.
El nivel de gasto y la distancia se incluyen en la configuración
validada, como se describe en la UT5.

Las validaciones de esta UT son independientes de Geoapify y de PostgreSQL.
El borrador no se persiste: recargar o abandonar la sección lo descarta.
La selección de actividades y la generación del itinerario siguen pendientes;
el futuro generador deberá reutilizar `PreferenciasService.validar` antes de
consumir la configuración, incluso si procede de otro cliente.

Comprobaciones de aceptación con un destino y fechas válidos:

1. Seleccionar Gastronomía y Naturaleza; excluir Comida rápida y Senderismo.
   Se permite continuar y el resumen muestra las cuatro selecciones.
2. Marcar también «Evitar toda la categoría: Gastronomía». Se muestra el error,
   se conservan las selecciones y se bloquea continuar. Desmarcar una de las dos
   opciones corrige el error y permite avanzar.
3. Volver del resumen, retirar un interés y cambiar una exclusión. Se conserva
   el resto de la configuración, incluidas las exclusiones cuyo interés se retiró.
4. Marcar, desmarcar y volver a marcar una opción. Solo aparece una vez en el resumen.

### UT5: gasto y distancia

`NivelGasto` define cuatro códigos estables con su nombre y descripción:

| Código | Presentación | Orientación para el futuro Planner |
| --- | --- | --- |
| `ECONOMICO` | Económico | Priorizar opciones gratuitas o de bajo coste. |
| `MODERADO` | Moderado | Combinar opciones gratuitas y de pago. |
| `FLEXIBLE` | Flexible | Dar más prioridad al interés de la experiencia. |
| `SIN_PREFERENCIA` | Sin preferencia | No priorizar por gasto. |

No se asignan importes ni se calcula un presupuesto. `distanciaKm` es una
**distancia deseada entre actividades consecutivas**, no un máximo obligatorio
ni una distancia diaria total. El selector admite de 1 a 10 km, en pasos de 1 km.
El backend publica esas opciones y su significado en
`GET /api/itinerarios/opciones-gasto-distancia`; los valores iniciales son
`SIN_PREFERENCIA` y 3 km y no se vuelven a aplicar sobre datos ya editados.

`PreferenciasDto` sigue siendo el objeto de entrada y salida de
`POST /api/itinerarios/validar-preferencias`, ahora con cinco campos. El backend
requiere un nivel admitido y una distancia presente, finita, entera y en el rango.
Los campos ausentes no se rellenan silenciosamente; tampoco se aceptan ordinales
numéricos como nivel de gasto. Las respuestas inválidas usan HTTP 400 y `mensaje`.
Esta ampliación requiere actualizar juntos el frontend y el backend.

El formulario obtiene los niveles, etiquetas, descripciones y límites del backend.
Al cambiar gasto o distancia se comprueba la configuración completa después de
200 ms sin cambios. La respuesta válida solo permite avanzar si corresponde
a los valores actuales; las respuestas anteriores se cancelan o descartan.
El resumen usa el resultado validado y explica el significado de ambos valores.
Volver y editarlos conserva intereses, exclusiones, fechas, horas y alojamiento.

Todavía no existe un Planner ni un endpoint de generación. Esta UT prepara su
configuración de entrada: al implementar el generador habrá que reutilizar
`PreferenciasService.validar` y aplicar estas dos preferencias al valorar las
actividades y trayectos. La validación por sí sola no ordena actividades. No hay
persistencia del borrador ni cambios en Flyway o en las tablas en esta UT.

Aceptación manual con un destino y disponibilidad válidos:

1. Elegir Económico y 2 km, continuar y comprobar ambos valores y sus explicaciones.
2. Volver a editar, elegir Flexible y 8 km y comprobar que el resumen se actualiza.
3. Revisar que el resto del viaje y las selecciones de UT4 se conservan.
4. Probar también 1 y 10 km y los cuatro niveles, incluido Sin preferencia.
5. Si falla la conexión, comprobar el bloqueo, la conservación y el reintento.

Pruebas automáticas de UT4 y UT5, sin Geoapify ni PostgreSQL:

```powershell
.\mvnw.cmd '-Dtest=PreferenciasServiceTests,PreferenciasControllerTests,GastoDistanciaServiceTests,GastoDistanciaControllerTests' test
```

### UT6: revisión y confirmación de la configuración

Alcance acordado: preparar y validar el viaje para el futuro Planner.
**No se generan actividades ni se guarda el viaje en base de datos.**
El paso de la PA que pide generar el itinerario queda pendiente del generador.

`POST /api/itinerarios/confirmar-configuracion` recibe y devuelve un único
`ConfiguracionItinerarioDto`:

```json
{
  "destino": {
    "id": "identificador-del-destino-seleccionado",
    "nombre": "Madrid",
    "region": "Comunidad de Madrid",
    "pais": "España",
    "latitud": 40.4168,
    "longitud": -3.7038
  },
  "disponibilidad": {
    "llegada": "2026-11-10T10:00",
    "salida": "2026-11-12T18:00",
    "duracionDias": 3
  },
  "alojamiento": "Hotel Madrid",
  "preferencias": {
    "intereses": ["GASTRONOMIA"],
    "categoriasExcluidas": ["ARTE_MUSEOS"],
    "subcategoriasExcluidas": ["COMIDA_RAPIDA"],
    "nivelGasto": "MODERADO",
    "distanciaKm": 3
  }
}
```

`ConfiguracionItinerarioService.validar` comprueba destino y coordenadas,
reutiliza `FechaHoraService` y `PreferenciasService`, recalcula la duración
ignorando la enviada por el cliente y devuelve listas sin duplicados y sin
referencias mutables a las listas de entrada. El alojamiento es opcional,
se recortan los espacios exteriores y su máximo es 250 caracteres.

El destino se comprueba estructuralmente (identificador y nombre presentes,
coordenadas finitas y dentro de rango); esta operación no consulta Geoapify
ni acredita por sí sola la identidad de un lugar. Los `Double` de `DestinoDto`
permiten detectar coordenadas ausentes, sin convertirlas silenciosamente a cero.

Respuesta válida: HTTP 200 con la configuración normalizada y `Cache-Control: no-store`.
Errores de formato o de negocio: HTTP 400 con `mensaje`. El endpoint
es independiente de Geoapify, no almacena estado entre solicitudes y no crea un
identificador de viaje, sesión de confirmación ni itinerario.

En la interfaz:

1. Completar el formulario y abrir `Revisa tu plan`. El estado es pendiente.
2. Pulsar `Confirmar configuración`. El resumen pasa a mostrar la respuesta
   validada por el backend. Mientras se procesa se bloquean confirmación,
   edición y retroceso; se ignoran clics duplicados.
3. Pulsar `Editar` en cualquier apartado. Se invalida la confirmación anterior.
4. Guardar los cambios para regresar directamente al resumen y confirmar de nuevo.
   No se reinician los otros datos.
5. Si falla la conexión, conservar el borrador, mostrar el error y ofrecer reintento.

La petición usa una copia completa de la configuración vigente. Al abandonar
la página se cancela; una respuesta cancelada no puede restaurar una confirmación
anterior. El estado confirmado es local al formulario y se pierde al recargar
o abandonar la sección. No existe todavía persistencia del borrador.

El futuro Planner deberá consumir la última `ConfiguracionItinerarioDto`
confirmada y volver a pasarla por `ConfiguracionItinerarioService.validar`
en el servidor. La prueba de integración con generación real se añadirá entonces;
un HTTP 200 de confirmación no se presenta como un itinerario generado.

Aceptación disponible: confirmar Económico y 3 km; editar solo el gasto a
Moderado; regresar al resumen; confirmar y comprobar que destino, fechas,
horarios, alojamiento, listas y distancia permanecen iguales. Repetir con
otros apartados y con valores inválidos para comprobar el bloqueo.

```powershell
.\mvnw.cmd '-Dtest=ConfiguracionItinerarioServiceTests,ConfiguracionItinerarioControllerTests' test
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

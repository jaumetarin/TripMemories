# Rute

Aplicación web para planificar itinerarios de viaje y guardar recuerdos asociados a viajes, actividades y lugares. Proyecto universitario desarrollado por un equipo de cinco estudiantes de la Universitat Politècnica de València.

El nombre de la aplicación es **Rute**. Se conservan los nombres internos del
repositorio, el paquete Java y la base de datos `tripmemories` para mantener las
instalaciones existentes.

## UT21–UT22: cuentas, verificación y sesión

La portada es pública. Crear o generar un itinerario, consultar el perfil y abrir
Mis viajes o Mis recuerdos requiere iniciar sesión. El backend protege **todas**
las rutas `/api/**`, salvo las operaciones públicas de autenticación indicadas
abajo. Los ejemplos de consultas de otras secciones también necesitan ahora la
cookie de sesión y, si son POST, el token CSRF.

### Registro y datos almacenados

El formulario solicita nombre, correo, contraseña y confirmación. Las contraseñas
de registro admiten entre 8 y 128 caracteres y deben incluir al menos una letra,
un número y un símbolo (como `!`, `@` o `#`). Se admiten letras con tildes y `ñ`;
los espacios se permiten, pero no cuentan como símbolo. No se exige combinar
mayúsculas y minúsculas. La confirmación se compara y no se guarda.

Solo en Crear cuenta, al salir del campo de contraseña, la interfaz comprueba
los requisitos y señala cuáles faltan. Después del primer chequeo, los indicadores
se actualizan al corregir el texto. También se comprueba al enviar y en el backend,
para impedir registros inválidos aunque se omita la interfaz. El login y la
verificación del correo solo comprueban la contraseña existente: no aplican la
nueva regla de composición a cuentas o registros anteriores.

Se reutiliza `usuario`: `id` UUID, `nombre`, `correo`, `password_hash` y el nuevo
`correo_verificado`. El índice existente sobre `lower(correo)` impide duplicados
también cuando llegan registros simultáneos. Nombre y correo se normalizan al
registrar; el correo se almacena en minúsculas. El nombre no es un identificador único.

La contraseña se convierte en un **hash Argon2id**, con sal aleatoria por contraseña,
19 MiB de memoria, dos iteraciones y paralelismo uno. No se guarda la contraseña
original ni existe una operación para descifrarla. La configuración sigue el mínimo
recomendado por [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
Los hashes BCrypt anteriores se pueden comprobar y actualizar a Argon2id tras
un acceso correcto, pero no se marca automáticamente como verificada ninguna
cuenta anterior ni la cuenta de demostración del seed.

Flyway aplica `V2__cuentas_y_verificacion.sql` al arrancar. Añade el indicador a
`usuario` y crea `verificacion_correo`: un registro por usuario con el **hash** del
token, su caducidad y la fecha del último envío. Las pruebas utilizan otro PostgreSQL
temporal con Testcontainers; no aplican migraciones sobre tus datos locales.

### Verificar el correo con Brevo

1. Crear una cuenta en [Brevo](https://www.brevo.com/), completar su activación y
   configurar/verificar un remitente. Si se utiliza un dominio propio, completar
   también la autenticación del dominio que solicite Brevo.
2. Crear una **API key de Brevo** para envíos transaccionales. No confundirla con
   una clave SMTP; este proyecto usa su [API HTTP de envío](https://developers.brevo.com/docs/send-a-transactional-email).
3. En la misma PowerShell desde la que se arranca el backend, ejecutar:

   ```powershell
   cd C:\Users\denis\Desktop\TripMemories\backend
   $claveBrevo = Read-Host 'Pega la API key de Brevo' -AsSecureString
   $env:BREVO_API_KEY = [System.Net.NetworkCredential]::new('', $claveBrevo).Password.Trim()
   Remove-Variable claveBrevo
   $env:BREVO_SENDER_EMAIL = 'tu-remitente-verificado@tu-dominio.com'
   $env:BREVO_SENDER_NAME = 'Rute'
   $env:APP_FRONTEND_URL = 'http://localhost:5173'
   .\mvnw.cmd spring-boot:run
   ```

   Sustituir el correo por el remitente aprobado. No pegar la clave en Java,
   `application.properties`, variables `VITE_*`, Git ni chats. Estas variables solo
   duran en esa terminal. Para conservarlas en Windows, guardarlas como variables
   del usuario y abrir una terminal nueva; reiniciar el backend tras cambiarlas.

4. Abrir siempre `http://localhost:5173`, pulsar Crear cuenta y completar el formulario.
   Sin las variables de Brevo el backend puede arrancar, pero el registro y el reenvío
   devuelven un error comprensible y **no crean altas nuevas**.
5. Abrir el correo, pulsar Verificar mi correo y confirmar con la contraseña elegida
   al registrarse. Esta confirmación impide activar por error un registro creado
   por otra persona. Después iniciar sesión con correo y contraseña. No se pide
   volver a verificar el correo en cada acceso.

El enlace es aleatorio, dura 30 minutos y sirve una vez. Se incluye en el fragmento
de la URL y se retira de la barra al abrir la pantalla; la verificación ocurre mediante
POST explícito, no por visitar el enlace. El nuevo envío invalida el enlace anterior.
Se puede solicitar otro desde Iniciar sesión → Reenviar correo de verificación,
dejando al menos 60 segundos entre envíos. Si se repite el registro de una solicitud
pendiente tras esa espera, se renuevan nombre, contraseña y enlace; una cuenta
verificada nunca se sobrescribe desde el registro.

Las altas y reenvíos válidos responden con el mismo mensaje genérico exista o no
la cuenta, sin publicar datos privados ni confirmar qué direcciones están registradas.
Un fallo de envío no activa la cuenta: se mantiene pendiente y se puede reenviar.
Se envía después de confirmar la transacción, en una cola en memoria limitada; si
el proceso se reinicia antes del envío habrá que solicitar un nuevo enlace. Brevo
debe estar activo y autorizar el remitente: configurar las variables no sustituye
esa activación. Revisar su panel de entregas y el correo no deseado si no llega.

### Sesión y protección de las operaciones

La sesión se guarda en el servidor. El navegador conserva una cookie `JSESSIONID`
HttpOnly, SameSite=Lax; no se guardan credenciales ni tokens de acceso en
localStorage. El login renueva el identificador de sesión y su protección CSRF.
Dos horas sin actividad o reiniciar el servidor termina la sesión. Cerrar sesión
invalida la sesión en el backend, elimina la cookie y retira los datos privados de
la interfaz; también se avisa a otras pestañas abiertas de Rute.

| Método y ruta | Función | Acceso |
| --- | --- | --- |
| `GET /api/auth/csrf` | Token y nombre de cabecera CSRF | Público |
| `POST /api/auth/registro` | Alta pendiente de verificación | Público + CSRF |
| `POST /api/auth/reenviar` | Solicitar otro enlace | Público + CSRF |
| `POST /api/auth/verificar` | Token y contraseña del registro | Público + CSRF |
| `POST /api/auth/login` | Correo y contraseña | Público + CSRF |
| `POST /api/auth/logout` | Invalidar sesión | CSRF |
| `GET /api/auth/sesion` | Identidad de la sesión actual | Autenticado |
| Resto de `/api/**` | Operaciones de Rute | Autenticado; POST con CSRF |

`frontend/src/sesion.ts` centraliza estas peticiones y `api.ts` lo utiliza también
para el itinerario. Las operaciones que modifican datos envían `X-CSRF-TOKEN`
obtenido de `/api/auth/csrf`; se renueva después del login/logout. Un error 401 al
consultar datos privados lleva al acceso sin seguir mostrando el formulario privado.

Se limitan los intentos por IP y operación durante 15 minutos: 5 registros, 5
reenvíos, 10 accesos y 10 verificaciones. Un exceso devuelve 429 antes de ejecutar
la operación. Son controles en memoria de una instancia; se reinician con el proceso.
No se confía en cabeceras `X-Forwarded-For` enviadas por el cliente. Al desplegar tras
un proxy habrá que configurar explícitamente los proxies de confianza y revisar
el origen de la IP para que todos los visitantes no compartan el límite del proxy.

### Base de datos y despliegue futuro

Las conexiones locales conservan los valores anteriores. Para usar PostgreSQL
compartido se configuran `DB_URL` (URL JDBC), `DB_USER` y `DB_PASSWORD` en el backend;
Flyway aplica las mismas migraciones. No hace falta cambiar los repositorios Java.

En producción configurar `APP_FRONTEND_URL` con la URL HTTPS de Rute y
`SESSION_COOKIE_SECURE=true`. Servir el frontend y `/api` bajo el mismo origen
(proxy hacia el backend), equivalente al proxy Vite de desarrollo. El frontend
actual usa rutas `/api` relativas y cookies del mismo origen: dos servicios con
dominios distintos requieren preparar ese proxy o adaptar conscientemente cookies,
CORS y peticiones; no basta con cambiar una URL. CORS admite únicamente el origen
configurado; en desarrollo se admiten `localhost` y `127.0.0.1` con el mismo puerto.
Usar siempre el mismo nombre en cada sesión, porque sus cookies son distintas.
Cambiar las variables requiere reiniciar el backend.

Para ejecutar todas las pruebas del backend, con Docker Desktop abierto:

```powershell
cd backend
.\mvnw.cmd test
```

Los correos de las pruebas se simulan: no hace falta Brevo, no se envían mensajes
reales y no se consumen sus créditos. Las pruebas cubren validación, duplicados,
persistencia, enlace caducado/reutilizado, CSRF, límites, renovación de sesión y
logout. El envío real debe comprobarse después de configurar el remitente y la clave.

Los borradores y las propuestas sin guardar pertenecen a la sesión y se pierden
al cerrarla. Las cuentas y los viajes guardados permanecen en PostgreSQL.
Los recuerdos se consultan en **Mis recuerdos**: cada viaje guardado dispone de un álbum privado.

## UT23: guardar, recuperar y actualizar viajes propios

Después de generar una propuesta se puede indicar un título y guardarla. **Mis viajes**
lista únicamente los viajes del usuario autenticado y permite abrirlos después de
cerrar sesión, cambiar el título sin regenerar o editar la configuración y guardar
una nueva propuesta sobre el mismo viaje. Editar o generar no modifica lo guardado:
la actualización solo se confirma al pulsar **Guardar cambios** y completar la transacción.

El servidor conserva la configuración y los datos generales como JSONB versionado,
y cada bloque del itinerario como actividad con fecha, orden, tipo, inicio y fin.
Los detalles de visitas, restaurantes, alternativas y recorridos se conservan en
JSONB. La lectura reconstruye el plan guardado, incluidos los días sin actividades,
sin consultar a los proveedores ni consumir otra generación. Al reabrir se restauran
las selecciones guardadas en el catálogo de sesión para poder seguir editándolas.

Todos los endpoints requieren sesión. Las escrituras requieren además CSRF:

- `GET /api/viajes`: listado propio por último guardado.
- `GET /api/viajes/{id}`: configuración, itinerario y referencias de actividades.
- `POST /api/viajes/desde-generacion/{generacionId}`: crea con `{titulo}` y devuelve 201.
- `POST /api/viajes/{id}/desde-generacion/{generacionId}`: actualiza con `{titulo, revision}`.
- `PUT /api/viajes/{id}`: cambia el título con `{titulo, revision}`, conservando el plan.

La propuesta se identifica mediante `X-Itinerario-Id`, obtenido al generar. El backend
solo acepta la última generación correcta de esa sesión; el navegador no envía un
itinerario arbitrario para persistirlo. Una generación fallida no elimina la anterior.
Este resultado guardable dura hasta la siguiente generación correcta o el fin de la
sesión, independientemente de los 15 minutos de caché de reutilización.

Las consultas y actualizaciones filtran por el usuario del backend. Un viaje ajeno
y uno inexistente responden igual con 404. Cada actualización comprueba la revisión
esperada e incrementa su valor: dos ediciones concurrentes no se sobrescriben y la
desactualizada recibe 409. Se conserva la versión anterior del itinerario y sus
actividades; no se ofrece todavía una pantalla de historial. Cualquier fallo durante
la escritura revierte toda la operación. La interfaz conserva los cambios pendientes
y solo muestra éxito tras recibir la confirmación del servidor.

Flyway aplica `V3__persistencia_viajes.sql` y `V4__ampliar_nombre_destino.sql` al
arrancar el backend. No se modifican las migraciones anteriores. Los itinerarios
antiguos sin los datos completos de este formato no se inventan ni regeneran:
su recuperación devuelve un error controlado.

La suite del backend incluye pruebas con PostgreSQL temporal para creación,
recuperación tras volver a entrar, actualización, permisos, CSRF, revisiones
concurrentes y fallo SQL a mitad del guardado con recuperación de la versión anterior.
Se ejecuta con `.\mvnw.cmd test` desde `backend`, con Docker disponible.
El frontend se verifica con `npm run build`, `npm run lint` y el recorrido de edición
y guardado en el navegador. Las pruebas usan proveedores simulados.

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
├── docs/                             # Relevo técnico del planificador
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
| `repository` | Acceso a PostgreSQL mediante consultas parametrizadas; incluye `UsuarioRepository`. |
| `model` | Objetos internos, como `Usuario` y la identidad `UsuarioAutenticado`. |
| `dto` | Datos de entrada y salida de la API. |
| `client` | Comunicación con APIs externas, como Geoapify. |
| `config` | Configuración e inicialización de dependencias. |
| `exception` | Excepciones y su conversión a respuestas HTTP. |

`BackendApplication` permanece en el paquete raíz para que Spring detecte todas las
capas. Las pruebas reproducen la estructura de paquetes en `src/test/java`.

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
La generación y la consulta del itinerario se describen en las secciones de
UT12 a UT15. El guardado sigue pendiente.

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
El alojamiento es opcional. Se busca por nombre o dirección dentro del destino
seleccionado y debe elegirse una sugerencia con coordenadas válidas. Si se escribe
texto sin seleccionar una ubicación, se impide continuar hasta seleccionar una
sugerencia o vaciar el campo. El borrador se conserva durante los pasos del
formulario y se pierde al recargar o abandonar la creación.

La llegada debe ser hoy o un día posterior. El calendario de salida permite
seleccionar desde el día de llegada; el backend comprueba que la salida sea
posterior considerando fecha y hora. Las fechas concretas de los ejemplos son
ilustrativas: para probar los endpoints deben actualizarse a fechas válidas.
Las pruebas automatizadas utilizan un reloj fijo.

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
Excluir todas las subcategorías disponibles no implica por sí solo excluir toda
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

Las exclusiones de ocio son compras, boleras y centros recreativos, zoológicos y
acuarios, y parques de atracciones. Ya no se ofrece «Vida nocturna»: cines, cafés,
bares, pubs y discotecas no se clasifican como visitas de Ocio. Los cafés siguen
perteneciendo a Gastronomía cuando se selecciona ese interés.
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
  "alojamiento": "",
  "alojamientoLocalizado": null,
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
referencias mutables a las listas de entrada. 
El alojamiento es opcional. Su texto admite hasta 250 caracteres y se recortan
los espacios exteriores. Si se indica, debe incluir `alojamientoLocalizado`
con identificador, nombre, dirección y coordenadas válidas. El texto debe
coincidir con el nombre o la dirección de la selección.
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


## Candidatos reales de lugares — UT 07

El backend consulta Geoapify Places para recuperar candidatos del destino
seleccionado. La clave se obtiene de `GEOAPIFY_API_KEY`, igual que en el
autocompletado, y permanece fuera del código y del frontend.

### Endpoints

Ambos requieren el parámetro `destinoId`, cuyo valor es el `id` real devuelto
por `GET /api/destinos?texto=Madrid`.

- `GET /api/candidatos/turisticos?destinoId=ID_DEL_DESTINO`
- `GET /api/candidatos/gastronomicos?destinoId=ID_DEL_DESTINO`

La búsqueda turística incluye sitios de interés, museos y parques.
La gastronómica busca restaurantes. Cada consulta solicita hasta 50
resultados dentro del destino; no pretende recuperar todos sus lugares.

Cada candidato contiene `id`, `nombre`, `latitud`, `longitud`, `categorias`
y `direccion`. Los datos no disponibles pueden permanecer ausentes o nulos.

### Organización

`CandidatoController` recibe la petición y delega en `CandidatoService`.
El servicio selecciona las categorías y utiliza `GeoapifyClient` para
consultar el proveedor. Los DTO de Geoapify representan su respuesta y
`CandidatoDto` define el formato que devuelve nuestra API.

### Resultados y errores

- `200` con candidatos cuando hay resultados.
- `200` con `[]` cuando la consulta válida no encuentra lugares.
- `400` ante parámetros inválidos.
- `503` si falta la clave, falla el proveedor o la respuesta no es válida.

No se inventan lugares para completar el resultado.

Los endpoints originales de esta UT obtienen candidatos sin personalización.
La preparación por preferencias y la eliminación de duplicados se añaden en UT8.
La construcción del itinerario corresponde al futuro Planner.

### Comprobación

Con el backend arrancado:

1. Consultar `/api/destinos?texto=Madrid`.
2. Copiar el identificador del destino elegido.
3. Consultar ambos endpoints de candidatos con ese identificador.

El puerto predeterminado es 8080. Desde backend:

```powershell
.\mvnw.cmd spring-boot:run 
```

Las pruebas de `GeoapifyClientTests` y `CandidatoControllerTests` utilizan
respuestas simuladas y no requieren clave real ni PostgreSQL.
Cubren la recepción de candidatos, respuestas vacías y fallos del proveedor.

## UT8: filtrado de candidatos por preferencias

`POST /api/candidatos/preparar` recibe la configuración completa de UT6 y devuelve
una lista de `LugarDto`, la representación de los candidatos preparados para el Planner.
Conserva el identificador Geoapify, nombre, dirección, coordenadas, categorías y subcategorías
de TripMemories, y el horario solo cuando está disponible:

```json
[
  {
    "id": "lugar-a",
    "nombre": "Restaurante A",
    "direccion": "Calle A, Madrid",
    "latitud": 40.4,
    "longitud": -3.7,
    "categorias": ["GASTRONOMIA"],
    "subcategorias": [],
    "horario": "Mo-Fr 10:00-18:00"
  }
]
```

- `PreparacionCandidatosService` valida la configuración, consulta Geoapify y filtra.
- `ClasificacionLugaresService` relaciona las categorías del proveedor con nuestros
  intereses y exclusiones. Las categorías y subcategorías internas se conservan en el resultado.
- Tres grupos de búsqueda por interés (cuatro para Historia/Cultura local y seis para Ocio), con hasta
  100 resultados por página, en secuencia.
  Los grupos idénticos de Historia y Cultura local se consultan una sola vez por preparación.
  Solo el respaldo general de atracciones admite hasta cinco páginas. Con las seis categorías
  son como máximo 24 llamadas de Places, sin incluir autocompletado ni restaurantes del plan.
  Ocio puede añadir hasta tres geocodificaciones de calles comerciales detectadas.
  La paginación termina antes si llega una página incompleta o repetida.
- Sin intereses seleccionados se consultan las seis categorías generales, salvo
  las categorías completas que el usuario haya excluido.
- Se agrupan los repetidos por identificador Geoapify y se reúnen sus etiquetas
  antes de aplicar las exclusiones. Una copia excluida descarta el lugar completo.
- También se agrupan identificadores distintos cuando el nombre normalizado y la ubicación
  identifican el mismo recinto. Se conservan las etiquetas, coordenadas y horario disponibles
  y las exclusiones se aplican al grupo completo, no solo a una copia.
- El resultado mantiene ese identificador y todas las categorías y subcategorías
  reconocidas en las distintas copias, sin repetidos, para relacionar el lugar entre fases.
- Las coordenadas se recuperan del primer par válido de una misma copia del lugar.
  Si ninguna copia contiene un par completo, finito y dentro de rango, ambas se devuelven
  como `null`; no se sustituyen por cero ni se mezclan coordenadas de distintas copias.
- Las categorías y subcategorías excluidas prevalecen sobre los intereses.
- Cultura local incluye `commercial.marketplace` en su consulta. Los mercados tradicionales
  se reconocen por esa categoría aunque no haya etiquetas de origen; se aplica su exclusión.
- La detección de algunas subcategorías depende de etiquetas de origen explícitas:
  mercados gastronómicos, senderismo y espectáculos tradicionales. La cobertura depende del proveedor.
- Se omiten los resultados sin identificador, sin nombre o sin clasificación reconocida.
  También se descartan nombres genéricos como «sin nombre», «desconocido» y «unknown»
  (coincidencia completa, sin distinguir mayúsculas y tras quitar espacios exteriores).
  La dirección puede ser nula; la interfaz muestra «Dirección no disponible».
- El horario se recoge de `opening_hours` o, si falta, de `datasource.raw.opening_hours`
  en la misma respuesta de Places. Se conserva el texto publicado por el proveedor;
  no se interpreta para programar actividades ni se hacen consultas adicionales de detalles.
  Si está ausente o vacío, el campo `horario` se omite del JSON y no aparece en la tarjeta.
- HTTP 200 con `[]` significa que no hay resultados; 400 indica configuración inválida
  y 503 un fallo del proveedor. No se presenta un fallo como si fuera una lista vacía.

Después de confirmar el resumen aparece **Buscar lugares disponibles**. La interfaz
muestra solo nombre, dirección y, cuando existe, el horario publicado, aunque conserva
el resto de los datos recibidos. Las tarjetas utilizan el identificador del lugar como clave.
Al editar y confirmar otra vez se utiliza la selección
vigente. Los estados de carga, error y reintento conservan el formulario.
Cada nueva búsqueda consulta el proveedor; no hay caché ni cambios en PostgreSQL.
Los endpoints originales de UT7 siguen funcionando.

Pruebas desde backend, sin necesitar ni modificar la base de datos:

```powershell
.\mvnw.cmd clean '-Dspring.flyway.enabled=false' test
```

## UT9 y UT10: evaluación de candidatos y alternativa local

`POST /api/candidatos/evaluar` recibe la misma configuración completa que
`/api/candidatos/preparar`. El backend prepara los lugares una vez y selecciona una muestra
de hasta 120, intercalando los grupos de búsqueda. Devuelve cada `LugarDto` evaluado junto
con afinidad (1–100), relevancia turística estimada (0–100 o `null` si se desconoce),
duración estimada en minutos, etiqueta, motivo y origen de la evaluación. El
identificador de Geoapify solo relaciona respuestas: Gemini recibe referencias
temporales, nunca el identificador externo. Gemini no decide el itinerario ni el
orden de los días; esa tarea corresponde a UT12.

El orden de las fases responde a una decisión del planificador: **Geoapify aporta
lugares reales; UT8 elimina duplicados y aplica intereses y exclusiones; Gemini
estima afinidad, relevancia y duración para la muestra seleccionada; Tavily investiga solo
algunos de los mejor situados**. La IA no puede crear lugares ni fijar rutas.
Después de Tavily no se hace otra llamada a Gemini: el plan diario de UT12 lo
construirá una heurística propia del backend con tiempos peatonales de UT11.

La afinidad, la relevancia y la duración de Gemini son estimaciones. La etiqueta y el motivo
visibles se construyen a partir de las categorías del lugar y los intereses
elegidos; no se publican afirmaciones libres de Gemini que no tengan respaldo
en esos datos.

La evaluación usa `gemini-3.1-flash-lite` con la clave `GEMINI_API_KEY` configurada
solo en el backend. Ante clave ausente, error, respuesta incompleta o datos
inválidos, se conservan los lotes anteriores correctos y los restantes usan una clasificación
local determinista sin relevancia inventada. No se hacen más llamadas de IA tras el fallo;
la respuesta incluye `fallback: true` y una advertencia. Cada candidato mantiene su propio
`origenEvaluacion` (`GEMINI` o `LOCAL`). Un fallo de Geoapify
continúa siendo un error 503; una búsqueda válida sin lugares devuelve una lista
vacía. Las duraciones se muestran siempre con `≈` porque son orientativas.
La alternativa local se basa en categorías e intereses, nunca en el identificador
del proveedor. Esto conserva el flujo cuando falla Gemini sin fingir una
evaluación semántica equivalente.

La búsqueda externa para los candidatos mejor situados es opcional y utiliza
`TAVILY_API_KEY` únicamente en el backend. Investiga progresivamente según la
capacidad orientativa del viaje: 10:00–18:00 por día, recortada por la llegada y
la salida. Este cálculo no garantiza que las actividades quepan, porque los
desplazamientos y el reparto diario se resolverán en UT11/UT12. Cada petición
tiene un plazo total de 100 segundos y un límite técnico de 100 búsquedas; si
se alcanza, se devuelven las evaluaciones ya disponibles con un aviso. Sin clave
Tavily, la evaluación Gemini o local sigue funcionando y los datos externos
permanecen desconocidos.
Se eligió una búsqueda separada porque la consulta web de Gemini no estaba
disponible en el nivel gratuito de la API usado por el proyecto; Tavily permite
una cuota gratuita sin tarjeta (véanse las [tarifas de Gemini](https://ai.google.dev/gemini-api/docs/pricing)
y el [plan gratuito de Tavily](https://help.tavily.com/articles/8816424538-pricing)).
El cliente usa REST en el backend, conserva URL
de origen y descarta resultados cuyo título no identifica el lugar o proceden de
redes sociales y ciertas páginas de reseñas. La información de terceros sigue
siendo parcial y puede variar.

Configura las claves **en la terminal del backend**, como `GEOAPIFY_API_KEY`;
no las pongas en el frontend ni en Git. En PowerShell, antes de arrancar:

```powershell
$env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave de Gemini' -AsSecureString)).Password
$env:TAVILY_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave de Tavily' -AsSecureString)).Password
```

`TAVILY_API_KEY` es opcional. Las claves de las pruebas reales se pasaron solo al
proceso y no se guardaron en este repositorio.

El precio básico por persona se clasifica en `FREE` (0 €), `LOW` (>0–25 €),
`MEDIUM` (>25–50 €), `HIGH` (>50 €) o `UNKNOWN`. Solo se clasifica cuando existe
un precio atribuible a una fuente y expresado en euros; otras monedas siguen
como `UNKNOWN` hasta disponer de una conversión fiable. Los precios extraídos
de resultados de búsqueda se identifican como estimados. Los horarios de
Geoapify siguen sin verificar; los encontrados en búsqueda se identifican como
información parcial. No se rellenan horarios ni precios ausentes con supuestos.

La respuesta pública usa DTO propios, no los esquemas de Gemini, Tavily o
Geoapify. `CandidatoExceptionHandler` devuelve 400 para configuración inválida
y 503 para fallos de obtención de lugares. La vista de candidatos muestra
afinidad, motivo, duración estimada, coste, horario, procedencia y avisos. La
consulta del itinerario por días se incorporó después, en UT15.

La prueba real de Valencia (arte y museos, un día de 10:00 a 18:00) devolvió 81
candidatos evaluados por Gemini. Tavily aportó fuentes para 6, precio para 4 y
horario externo para 4; el contador orientativo alcanzó 420 de 480 minutos antes
del límite de 100 segundos. Otra ejecución comparable terminó en unos 16
segundos: la latencia y la cobertura no son constantes. `minutosInvestigados`
suma duraciones de candidatos con algún hallazgo, **no** significa que esos
lugares ya formen un día viable. La suite completa pasó 237 pruebas de backend
y la compilación del frontend.

## Continuación del primer itinerario: UT11 a UT16

El [documento de continuidad](docs/continuidad-planificador-ut09-ut16.md) contiene
el contrato actual, decisiones justificadas, pruebas realizadas, límites conocidos,
orden de trabajo y criterios de aceptación para el equipo. La secuencia del primer plan es:

1. **UT11:** obtener tiempos y distancias a pie con OpenRouteService; avisar si
   una ruta no está disponible, sin inventar minutos.
2. **UT12:** construir con una heurística propia los días, visitas y traslados
   que caben en la disponibilidad. Si faltan candidatos viables, devolver un
   plan parcial con tiempo libre explícito.
3. **UT13:** incluir comidas, descansos y márgenes sin solapamientos.
4. **UT14:** validar el plan y explicar conflictos y datos inciertos.
5. **UT15:** presentar el itinerario por días con detalles y traslados.
6. **UT16:** sincronizar lugares de la lista y marcadores del mapa.

Gasto y distancia todavía no participan en el ranking de Gemini. El Planner
deberá considerarlos como preferencias al disponer de precios y recorridos, y
mantener `UNKNOWN` cuando un dato no esté respaldado. UT17 añadirá después el
control de solicitudes repetidas y cuotas de generación.


## UT11: desplazamientos a pie con OpenRouteService

El backend incorpora el cálculo de distancias y tiempos peatonales mediante
OpenRouteService.

La clave se configura en la variable de entorno `ORS_API_KEY`. El archivo
application.properties contiene únicamente la referencia a esa variable.

### Endpoint

POST /api/desplazamientos/calcular

Recibe una lista de puntos con `id`, `latitud` y `longitud`.
Admite hasta 51 puntos por cálculo, con identificadores únicos: hasta 50 lugares y un origen.

Devuelve los desplazamientos entre lugares distintos, con distancia en metros,
duración en segundos, identificadores de origen y destino, estado y fuente.

Estados posibles:
- DISPONIBLE.
- COORDENADAS_AUSENTES.
- RUTA_NO_DISPONIBLE.

Los datos desconocidos permanecen como null. No se inventan tiempos ni distancias.

Los errores de entrada devuelven HTTP 400. Los fallos del proveedor, la clave
ausente y las respuestas inválidas del proveedor devuelven HTTP 503.

Las pruebas del cliente, servicio y controlador utilizan respuestas simuladas.

La integración real de UT11 se comprobó el 6/11/2026 con tres lugares de Madrid
y con una matriz de 50 puntos (2.450 pares dirigidos). La clave se pasó solo al
proceso de prueba. La UT12 utiliza `DesplazamientoService` y gestiona los
recorridos no disponibles; los avisos de un plan generado se presentan en su API.


## UT12: generación del plan diario

`POST /api/itinerarios/generar` recibe como JSON la misma configuración de
`/api/itinerarios/confirmar-configuracion`: `destino`, `disponibilidad`, `alojamiento`, `alojamientoLocalizado`,
`preferencias` y `ritmo`.. El servicio valida la configuración y genera
el itinerario a partir de los candidatos evaluados y sus desplazamientos a pie.
La respuesta tiene `Cache-Control: no-store` y contiene:

- `dias`: días con `fecha`, `inicioDisponible`, `finDisponible` y `bloques`.
  Cada bloque incluye tipo, inicio y fin, candidato o datos del trayecto cuando
  corresponda, fuente, indicador `condicionada` y descripción. Si un día no
  tiene ventana entre 10:00 y 18:00, sus límites son `null` y no tiene bloques.
- `advertencias`: explicaciones de datos o recorridos incompletos.
- `parcial`: indica que hay un día sin visitas o sin ventana útil, un hueco
  de tiempo libre de al menos 15 minutos o lugares que no se han programado.
- `candidatosEvaluados` y `candidatosEnMatriz`: contadores de lugares evaluados
  y candidatos turísticos incluidos en la matriz de recorridos.
El origen y los restaurantes no se cuentan en `candidatosEnMatriz`.

La heurística distribuye visitas y traslados dentro de la ventana diaria de
10:00 a 18:00, recortada por la llegada y la salida, sin solapamientos ni
visitas duplicadas. Si no hay suficientes visitas viables, deja tiempo libre
explícito y comunica las limitaciones. Los horarios que no sean `VERIFIED`
se presentan como propuestas condicionadas: el texto disponible no confirma
que el lugar está abierto en la fecha y hora elegidas.
La prioridad combina afinidad y relevancia estimada: `(2 × afinidad + relevancia) / 3`.
Sin estimación de relevancia se utiliza la afinidad. Las selecciones manuales se
protegen antes de recortar la muestra de IA (120) y los lugares para rutas (50, más el origen).
Las visitas automáticas se reparten por interés antes de comparar prioridades, tanto
en esos recortes como en el plan final. Dentro del turno de una categoría se admite
una banda de cinco puntos de prioridad; las referencias publicadas del lugar y sus
nombres internacionales ayudan a priorizar referentes. Después se considera un trayecto
dentro de la distancia deseada, menor tiempo a pie y gasto acorde.
La distancia deseada es flexible y un coste desconocido no se considera gratis.

La aplicación admite matrices de hasta 51 puntos con coordenadas
válidas: un origen y hasta 50 lugares entre visitas y restaurantes. Los restaurantes
solo ocupan puestos libres después de reservar los lugares seleccionados. Si hay más
candidatos, se limita la muestra y se añade un aviso. Una ruta
concreta no disponible puede causar un plan parcial con advertencia, sin
inventar tiempos. Si falla el proveedor en conjunto, la respuesta es HTTP 503.
Con menos de dos puntos geolocalizados no se consulta ORS. Si ninguna fecha
tiene tiempo disponible, no se consulta ningún proveedor.

Los límites se definen en `LimitesItinerario`: 50 lugares y 51 puntos de ruta,
contando el origen. El cliente y el servicio de desplazamientos rechazan 52 puntos.
La comprobación real del 24/11/2026 recibió matrices de distancia y duración de
51 × 51 de OpenRouteService. Las pruebas simuladas comprueban además que 50
obligatorios con tiempo y rutas suficientes se conservan, con y sin alojamiento,
y que un restaurante sugerido no desplaza selecciones manuales.
El JSON mal formado o una configuración inválida reciben HTTP 400. Ambos
errores incluyen un objeto JSON con `mensaje`.

## UT13 y UT14: pausas y validación del plan

La configuración admite un objeto `ritmo` opcional. Cada campo omitido adopta
su valor inicial:

```json
"ritmo": {
  "comidaInicio": "13:00",
  "comidaFin": "15:00",
  "descansoCadaMinutos": 120,
  "descansoDuracionMinutos": 15,
  "margenMinutos": 10
}
```

La franja de comida se reserva completa y puede ajustarse entre las 10:00 y
las 18:00. En un día parcialmente disponible se reserva solo el solape con
esa franja, sin moverla. Las visitas no se acortan ni dividen para encajar.
El Planner puede añadir bloques `COMIDA`, `DESCANSO` y `MARGEN` además de
visitas, traslados y tiempo libre. Sitúa el descanso en una frontera entre
actividades cuando se alcanza el intervalo configurado; la comida también
cuenta como descanso. Los minutos del margen se suman a cada traslado ORS.

Cuando hay un día que incluye la franja de comida completa, se buscan
restaurantes reales aunque no se haya marcado gastronomía como interés. Si
gastronomía está excluida, no se proponen restaurantes. También se respetan las
subcategorías excluidas. Se consulta Geoapify una vez por generación, solicitando
hasta 100 restaurantes del destino, y se seleccionan hasta diez cerca de las
zonas de las visitas candidatas. La proximidad geográfica sirve solo para
preseleccionar: nunca se presenta como una ruta a pie.

Con los recorridos de OpenRouteService se elige un restaurante al que se pueda
llegar antes de la comida y se favorece la cercanía a las visitas de antes y
después. Las visitas y los descansos previos dejan tiempo para ese traslado y
su margen. Si no hay ruta al restaurante se conservan las visitas viables y la
pausa para comer, sin inventar un desplazamiento. Los días que solo incluyen
parte de la franja mantienen una pausa libre, sin forzar una reserva o acortar
las visitas.

La comida puede incluir un restaurante principal y hasta tres alternativas.
Solo el principal participa en los trayectos temporizados. Las opciones muestran
nombre, dirección, horario si existe y distancia/tiempo a pie desde la visita
anterior cuando el proveedor los ha calculado. Si no hay visita previa, esos
datos permanecen ausentes. Las alternativas no recalculan el itinerario al
mostrarlas; elegir otra exige revisar los desplazamientos. No se infieren
precios ni apertura confirmada. Un restaurante con cierre inequívoco según su
horario de fuente no se propone.

Si no hay restaurante viable, la tarjeta dice «Pausa para comer. Puedes elegir
un restaurante por la zona», sin un aviso de disponibilidad o reserva para un
local inexistente. El aviso general recuerda que ese desplazamiento no está
calculado. El origen, las visitas y los restaurantes comparten el límite interno de 51 puntos de rutas.

La validación final comprueba ventanas, orden, solapes, duraciones,
desplazamientos, duplicados y exclusiones. Las visitas incompatibles con un
horario inequívoco se intentan recolocar y, si no caben, se explican y dejan
tiempo libre. Solo se interpretan reglas semanales simples de Geoapify,
como `Mo-Fr 10:00-18:00`, `Sa off` o `24/7`. Otros textos, incluidos los
fragmentos externos, permanecen inciertos; una visita o restaurante así se
presenta como propuesta condicionada. Los costes sin respaldo siguen como
desconocidos. Si una incoherencia estructural permanece en el plan, la API no
devuelve ese plan como válido.

El formulario permite ajustar el ritmo y revisarlo antes de confirmar. La
consulta por días se describe a continuación; el mapa corresponde a UT16.

## UT15: consulta del itinerario por días

Tras confirmar la configuración, «Generar itinerario» llama una sola vez a
`POST /api/itinerarios/generar`. El backend evalúa los candidatos dentro de esa
petición; la interfaz no consulta `/api/candidatos/evaluar` por separado. La
respuesta incorpora `candidatos` y `evaluacionFallback` para conservar la lista
completa de lugares considerados sin repetir consultas a los proveedores.

El resultado principal permite cambiar de día y abrir detalles de cada visita.
Muestra visitas, traslados, comida, descansos, márgenes y tiempo libre, así como
avisos, datos condicionados y días sin disponibilidad. Al cambiar de día se
cierran los detalles del día anterior. «Lugares considerados» es una vista
secundaria que distingue los candidatos programados de los que quedaron fuera;
volver al itinerario conserva el día seleccionado.

Si la generación falla, la configuración confirmada permanece disponible para
reintentar. Volver al resumen y abrir de nuevo el resultado no genera otra vez;
editar la configuración invalida el resultado mostrado. El plan no se guarda de
forma permanente; UT17 añade la reutilización temporal del último resultado en
la sesión del backend. El mapa se describe a continuación.

## UT16: visitas del día en el mapa

El itinerario muestra las tarjetas y el mapa juntos en ordenador. En móvil
permite alternar entre «Lista» y «Mapa», conservando la visita seleccionada.
Pulsar una tarjeta centra el mapa y destaca su marcador; pulsar un marcador
destaca la tarjeta correspondiente. «Ver tarjeta» vuelve a la lista y enfoca
esa visita. «Ver todas las visitas del día» restaura el encuadre completo.

Solo se representan los bloques `VISITA` programados del día seleccionado,
con los mismos números en tarjetas y marcadores. No se representan los
candidatos descartados, las comidas ni sus alternativas. Cambiar de día
actualiza ambas vistas y limpia la selección anterior. Volver desde «Lugares
considerados» conserva el día y la selección; un plan nuevo no hereda la visita
seleccionada del anterior.

Las visitas sin coordenadas válidas permanecen en la lista con un aviso y no
reciben ubicaciones inventadas. Las visitas con coordenadas idénticas comparten
un marcador que permite seleccionar cada una. Los días sin visitas tienen un
estado vacío explicativo. La lista mantiene horas, direcciones, traslados,
detalles y avisos, y puede utilizarse sin depender del mapa. Si falla la carga
del mapa o de su fondo, se muestra un aviso y se mantiene la alternativa textual.

Se utilizan Leaflet y React Leaflet, ya incluidos en el frontend. El fondo
predeterminado procede de OpenStreetMap con su atribución visible. No necesita
clave API. Opcionalmente, `VITE_MAP_TILES_URL` permite configurar una URL de
teselas compatible; si se cambia de proveedor debe revisarse también la
atribución en `MapaItinerario.tsx`. Las variables `VITE_` son públicas: no deben
contener claves privadas. El mapa no vuelve a generar el plan ni consulta
Geoapify, Gemini u OpenRouteService al seleccionar visitas o cambiar de día;
usa las coordenadas del itinerario recibido y carga las teselas del fondo.

Cada tarjeta incluye «Sobre este lugar», separado de la dirección y de los
datos prácticos. El backend puede entregar `descripcion` y `fuenteDescripcion`
en cada candidato evaluado. Tavily extrae una frase descriptiva de los resultados
de la búsqueda que ya se realiza para ese lugar, conserva su URL y limita el
extracto a 600 caracteres. No se hace una petición adicional por abrir la tarjeta.
Las frases que solo informan de precios u horarios no se usan como descripción.
No todos los lugares tienen un extracto reconocible: en ese caso ambos campos
son `null` y la tarjeta indica que la descripción no está disponible. El texto
se presenta como extracto de una fuente externa, sin convertirlo en información
verificada. Un resultado anterior o un backend sin estos campos sigue siendo
compatible con el frontend.

Las tarjetas distinguen la franja programada, el tipo de lugar, la dirección,
la descripción y un desplegable «Datos prácticos de la visita». Este último
conserva duración estimada, afinidad, coste, estado del horario y fuentes. Los
costes desconocidos siguen siendo desconocidos. Para recibir descripciones en
planes nuevos hay que reiniciar el backend actualizado y generar de nuevo;
requieren que la investigación con Tavily esté configurada y encuentre un texto
adecuado. No hay cambios en las tablas de la base de datos.

Los avisos de «Antes de tu viaje» explican qué revisar antes de salir: horarios
y precios pendientes de comprobar, visitas que no encajan y desplazamientos
que no están calculados. No muestran identificadores de lugares ni términos
internos como matrices o valoraciones locales. Los lugares descartados por
horario se agrupan en un solo mensaje con sus nombres; los restantes se cuentan
aparte. Los avisos repetidos se muestran una sola vez y el aviso de plan parcial
se integra en el mismo panel. Las limitaciones del plan no cambian: solo su
explicación al usuario.

Validación desde `frontend`:

```powershell
npm run lint
npm run build
```

Para comprobar manualmente la interacción:

1. Generar un itinerario con visitas en varios días y abrir el resultado.
2. Pulsar una tarjeta y comprobar su número y marcador destacado.
3. Pulsar otro marcador y comprobar la tarjeta seleccionada; usar «Ver tarjeta».
4. Cambiar de día y comprobar que solo aparecen las visitas de ese día.
5. Repetir en móvil alternando «Lista» y «Mapa» y comprobar que se conserva la selección.
6. Revisar un día vacío o una visita sin coordenadas: la explicación y el itinerario
   textual deben seguir disponibles.

## UT17: control de generaciones por sesión

El endpoint `POST /api/itinerarios/generar` aplica un control de sesión antes de
entrar al generador y, por tanto, antes de llamar a proveedores. Se utiliza
`HttpSession` y su cookie `JSESSIONID`, sin cuentas ni tablas nuevas. La versión
inicial mantiene únicamente el último resultado por sesión en memoria.

Valores iniciales y variables de entorno opcionales:

| Variable | Valor inicial | Significado |
| --- | --- | --- |
| `ITINERARIOS_MAX_GENERACIONES` | `3` | Generaciones nuevas admitidas por periodo y sesión |
| `ITINERARIOS_VENTANA_MINUTOS` | `60` | Duración del periodo del contador |
| `ITINERARIOS_VIGENCIA_MINUTOS` | `15` | Tiempo de reutilización desde que termina la generación |

Todos los valores deben ser positivos. La sesión expira tras dos horas de
inactividad. Los contadores y resultados se pierden al terminar la sesión o
reiniciar el backend. Este control está pensado para una única instancia;
distintos navegadores o sesiones privadas tienen sesiones diferentes.

El backend valida la configuración sin proveedores y compara los valores
validados. El orden o la repetición de intereses y exclusiones no crea otra
configuración. Si coincide con el último resultado vigente, lo devuelve sin
consumir cuota, incluso cuando ya no quedan generaciones nuevas. Cambiar un
parámetro relevante exige generar de nuevo. Solo un resultado nuevo correcto
sustituye al anterior; consultar uno existente no prolonga su vigencia.

La comprobación y la reserva del contador son atómicas. Mientras una generación
está en curso, otra generación nueva de la misma sesión recibe HTTP `409`.
Una solicitud por encima del límite recibe HTTP `429`. Ambas respuestas incluyen
`mensaje`, `Retry-After` en segundos y `Cache-Control: no-store`. No entran al
generador ni consumen otra ejecución. Se permite leer el resultado anterior
compatible mientras se genera otro. Una generación admitida que falla cuenta
como intento porque puede haber consumido proveedores, pero libera el bloqueo
y no borra el resultado anterior. Desconectar el navegador no libera el control
mientras el trabajo del backend siga ejecutándose.

El JSON del itinerario mantiene su estructura. Las respuestas correctas añaden:

- `X-Itinerario-Reutilizado`: `true` si se devuelve el resultado de la sesión.
- `X-Itinerario-Vigente-Hasta`: instante ISO de caducidad de esa reutilización.

`GET /api/itinerarios/consumo` devuelve `limite`, `restantes`, `reinicio` y
`enCurso`, sin consumir cuota ni consultar proveedores. El frontend consulta
este estado al abrir el resumen y ofrece «Actualizar disponibilidad». Desactiva
«Generar» mientras trabaja, mientras consulta el estado o si otra petición de
la sesión está generando. Un resultado ya abierto puede seguir leyéndose sin
llamadas externas. Con cuota agotada se permite comprobar si la configuración
actual tiene un resultado vigente; el backend rechaza cualquier ejecución nueva.
La consulta del estado es informativa: la decisión definitiva se toma siempre
en el POST. Al editar el formulario se mantienen las reglas de confirmación de UT6.

Las llamadas del frontend usan `/api` y la cookie del mismo origen, compatible
con el proxy de Vite. Para probar desde PowerShell hay que conservar la cookie
usando la misma `WebRequestSession` y `-WebSession` en todas las peticiones;
sin ello se prueban sesiones distintas.

Pruebas de aceptación automatizadas en `ControlGeneracionServiceTests` y
`GeneracionItinerarioControllerTests`: agotar cuota sin invocar el generador de
nuevo; dos peticiones simultáneas con una única ejecución; reutilización incluso
sin cuota; caducidad y renovación mediante reloj controlado; cambios relevantes,
normalización de listas, aislamiento de sesiones y recuperación tras errores.
Los proveedores se sustituyen por dobles de prueba, sin gastar sus cuotas.

Esta UT simplificada controla el flujo de generación. No añade cachés de
candidatos ni cuotas a los endpoints independientes de evaluación, búsqueda o
desplazamientos. No es un límite global de las claves API de los proveedores.

## Mejora de cobertura y selección de candidatos

Las consultas están organizadas en `ClasificacionLugaresService`, sin listas de nombres por ciudad:

| Interés | Grupos de búsqueda |
| --- | --- |
| Gastronomía | Restaurantes; cafeterías y comida rápida; mercados |
| Arte y museos | Museos; galerías; arte público |
| Historia y arquitectura | Puertas, torres y fortificaciones; edificios y patrimonio religioso; respaldo de atractivos turísticos; estadios |
| Naturaleza y parques | Parques; playas; espacios naturales |
| Cultura local | Espacios culturales, artesanía y mercados; fortificaciones; respaldo de atractivos turísticos; estadios |
| Ocio y entretenimiento | Centros comerciales; boleras, recreativos y actividades; parques temáticos, zoológicos y acuarios; moda, grandes almacenes y libros para descubrir calles comerciales |

Un estadio solo se admite para Historia/Cultura si también tiene etiquetas de interés
turístico o histórico. Las atracciones generales pueden ser candidatas culturales y
arquitectónicas sin afirmar su historia. Las obras de arte, fuentes, miradores y los
lugares ya identificados como comercios, restauración, ocio o naturaleza conservan su
clasificación específica; no se convierten automáticamente en patrimonio.
Un mercado solo se clasifica como gastronómico si sus etiquetas lo indican.

Después de agrupar todas las copias y aplicar exclusiones se intercalan los grupos
y se reparte la muestra por intereses. Dentro de Historia y Cultura local se favorecen
conjuntos turísticos, puertas, torres, palacios y plazas
frente a listas largas de memoriales u obras de arte. Es una preferencia por tipo para
dar variedad a la muestra, no una afirmación de popularidad. Si se alcanza el límite de
evaluación, el usuario recibe un aviso sobre la selección de una muestra.

`ITINERARIOS_MAX_CANDIDATOS` permite bajar el máximo evaluado (1–120; por defecto 120).
Gemini mantiene lotes de 40: como máximo tres llamadas por evaluación, sin una segunda
llamada para la relevancia. El modelo sigue siendo `gemini-3.1-flash-lite`, con razonamiento
`minimal`, respuestas breves y un máximo de 8192 tokens de salida por llamada.
Puede reconocer referentes usando conocimiento general, pero debe devolver `relevancia: null`
cuando no reconoce un lugar. No confirma precios ni horarios ni añade candidatos.
La interfaz muestra la relevancia como estimada o «Sin estimación».

Los registros del backend muestran el número de consultas, lugares únicos, candidatos
evaluados y tokens comunicados por Gemini. Para revisar descartes se puede activar
`logging.level.es.upv.pin.backend.service=DEBUG`; se distinguen filtros iniciales,
selección previa de rutas y limitaciones de horario o tiempo/rutas al planificar.
Estos registros no incluyen claves. UT17 sigue reutilizando el último plan compatible
de cada sesión; esta mejora no añade una caché compartida entre usuarios.

Las pruebas cubren los tipos observados en Valencia (Serranos, Quart, Ciudad de las Artes,
plaza del Ayuntamiento y plaza de Toros), paginación, diversidad entre grupos, exclusiones,
ausencia de duplicados, límite de IA y prioridad antes del recorte de rutas. Se simulan
las respuestas externas; la relevancia no garantiza incluir un lugar si no cabe en las
ventanas o sus horarios no son compatibles. La cobertura de Places tampoco es exhaustiva.

Comprobación manual del 16/11/2026: con Historia y Cultura local en Valencia, el servicio
preparó 226 candidatos en nueve llamadas a Places. Plaza de Toros, Serranos, Quart,
plaza del Ayuntamiento y Ciudad de las Artes quedaron dentro de los primeros 120.
Una petición real separada a Gemini con dos lugares validó el nuevo esquema y comunicó
531 tokens en total; esta cifra no representa el consumo de un itinerario completo.

### Refinado de recintos equivalentes y ocio

`IdentidadLugares` normaliza acentos, puntuación, artículos y variantes como
«Parc/Laberint» y «Parque/Laberinto». Agrupa nombres equivalentes con categorías
compatibles a menos de 200 metros, o hasta 500 metros en recintos de parques.
Un parque y su laberinto pueden tener categorías distintas y representar la misma
visita. No basta con estar cerca: un museo dentro de un parque conserva su identidad
si tiene otro nombre. Sin coordenadas no se deduce equivalencia entre IDs distintos.
Los restaurantes con el mismo nombre requieren además la misma dirección y una
distancia máxima de 30 metros, para conservar sucursales diferentes.

La agrupación se realiza antes de filtrar, investigar o enviar datos a Gemini; un
ID canónico relaciona mapa, lista y planificación. El planificador evita volver a
insertar equivalentes y el validador también los detecta entre días.
Si los alias publican horarios distintos, se devuelve el horario como desconocido
para no elegir arbitrariamente una disponibilidad.

Ocio consulta centros comerciales, boleras, recreativos, escape rooms, parques de
actividades, minigolf, parques temáticos/acuáticos, zoológicos y acuarios. Los tipos
de cine, café, bar, pub, cervecería y discoteca se descartan para esta categoría
incluso si el proveedor les añade una etiqueta de ocio o compras. No se convierten
automáticamente tiendas individuales en visitas.

`CallesComercialesService` agrupa comercios reales de moda, libros y grandes almacenes
por calle: exige al menos tres establecimientos únicos, dos portales distintos y
una separación de al menos 120 metros. Prioriza la variedad comercial y localiza
como máximo tres calles con Geocoding, `type=street` y filtro del destino. El nombre,
el tipo y las coordenadas devueltos deben ser válidos. No hay listas fijas por ciudad
ni llamadas adicionales a Gemini. La calle se clasifica como Compras, sin copiar
horarios o precios de sus tiendas. No se afirma que todas esas calles sean famosas;
su relevancia se evalúa después y la cobertura depende de los datos del proveedor.
Excluir Compras evita las consultas comerciales y de calles; las restantes opciones
de ocio siguen disponibles. La muestra de Gemini continúa limitada a 120 candidatos.

Al probar cambios de catálogo o de selección, reinicia el backend y recarga la web
para preparar una configuración nueva, sin conservar un resultado anterior en pantalla.

## UT18: alojamiento opcional y origen del recorrido

El alojamiento se busca mediante
`GET /api/alojamientos?texto=...&destinoId=...`.

El frontend espera una pausa de 350 ms y al menos tres caracteres antes de
consultar. El backend devuelve hasta cinco sugerencias válidas, sin duplicados.
Una búsqueda sin coincidencias devuelve una lista vacía; un fallo del proveedor
se comunica como error, sin inventar ubicaciones.

La configuración conserva:
- `alojamiento`: texto del campo.
- `alojamientoLocalizado`: selección con `id`, `nombre`, `direccion`,
  `latitud` y `longitud`, o `null` si no se indica alojamiento.

Editar el texto invalida la selección. Cambiar de destino borra el alojamiento.
Las consultas inválidas reciben HTTP 400 y la falta de clave o un fallo de
Geoapify reciben HTTP 503, con un campo `mensaje`.

Cada día comienza en las coordenadas del alojamiento seleccionado. Si no hay
alojamiento, se utiliza la referencia geográfica del destino. El origen ocupa
un puesto adicional a los 50 lugares, dentro del límite interno de 51 puntos de rutas.

El primer trayecto y su margen cuentan dentro de la disponibilidad diaria.
Si no existe una ruta válida, no se supone un desplazamiento de cero minutos.
El recorrido no incluye el regreso al punto de inicio.

La revisión muestra la ubicación elegida. El resultado identifica el origen
en los desplazamientos y lo representa mediante el marcador «Inicio» del mapa.

Antes de enviar candidatos a Gemini, se excluyen los que coincidan con el
alojamiento por identificador, nombre o dirección normalizados. Estos lugares
se conservan en el backend con su evaluación local.

Las claves `GEOAPIFY_API_KEY` y `ORS_API_KEY` se configuran en el entorno del
backend. Nunca deben incluirse en el repositorio ni en variables `VITE_*`.

Se han comprobado los recorridos con alojamiento y sin él, la selección y
borrado de sugerencias, el cambio de destino, los primeros desplazamientos
y la representación del origen.

Para ejecutar las pruebas del backend, desde `backend`:

```powershell
.\mvnw.cmd '-Dspring.flyway.enabled=false' test
```

### UT19: seleccionar lugares y prioridades

Tras las preferencias aparece el paso opcional **Elegir lugares**. La lista y el mapa
comparten una única selección, mantenida en el borrador del formulario y enviada en
`ConfiguracionItinerarioDto.selecciones`. Cada entrada contiene un `LugarDto` y un solo
`EstadoLugar`: `OBLIGATORIO`, `PREFERIDO` o `EVITADO`. Repetir una selección no duplica
el lugar; cambiar de estado lo sustituye y retirarlo devuelve el lugar al tratamiento
automático. Los nombres, símbolos y botones permiten reconocer el estado sin depender
solo del color. En móvil, las vistas Lista/Mapa conservan la selección.

Las prioridades se distinguen también por color: verde para Obligatorio, amarillo para
Preferido y rojo para Evitado, tanto en botones y etiquetas como en marcadores y resumen.
Se mantienen el nombre y el símbolo de cada estado. La búsqueda inicial muestra una
pantalla de espera; al filtrar o ampliar se conserva la lista y aparece un aviso de carga.
La generación muestra una pantalla dedicada que explica la espera y evita repetir la
acción. No se simulan porcentajes ni etapas completadas. Si falla, se recupera el resumen
con los datos confirmados y el foco en el mensaje de error. Las animaciones respetan
la preferencia de movimiento reducido.

Endpoints de sesión (sin cuentas ni tablas nuevas):

- `POST /api/lugares/buscar`: `{configuracion, texto, ampliar}`. Primero prepara lugares
  compatibles del destino. Escribir filtra el catálogo del servidor; **Buscar más lugares**
  añade consultas por nombre a Places con el mismo destino y categorías. Requiere al menos
  tres caracteres. `completarCatalogo: true` solicita la búsqueda específica completa;
  `limiteResultados` controla cuántas coincidencias mostrar (30 por defecto, hasta 150).
  La respuesta indica `catalogoCompleto` para distinguir la muestra de la búsqueda ampliada.
  La cobertura depende del proveedor y no es un inventario exhaustivo.
- `POST /api/lugares/seleccion`: `{configuracion, lugarId, estado}`. Actualiza el estado;
  `estado: null` retira la selección. El backend obtiene los metadatos de su catálogo,
  sin confiar en nombres, categorías o coordenadas enviados por el navegador.
- `POST /api/lugares/revisar`: recibe la configuración y retira con un aviso los lugares
  incompatibles después de cambiar destino o preferencias. Conserva los demás.

El catálogo se guarda en la sesión, separado del borrador, con resultados de consultas
vigentes 15 minutos, hasta tres destinos y 4000 lugares por destino. Se reutilizan las
búsquedas equivalentes; se conserva la metadata seleccionada al recortar el catálogo.
La sesión sigue caducando a las dos horas de inactividad. Tras reiniciar el servidor o
perder la sesión hay que revisar los lugares antes de confirmar. No se añaden claves API.

Se admiten hasta 50 selecciones y se reserva un punto adicional para el origen. Los elegidos
se conservan antes del recorte de IA y de rutas. Aunque se reduzca `ITINERARIOS_MAX_CANDIDATOS`,
la muestra incluye las selecciones manuales (como máximo 120 candidatos y tres lotes de IA).
Un itinerario aceptado debe incluir todos los obligatorios. Se programan primero,
atendiendo a su última oportunidad de apertura y evitando que otra visita consuma
el tiempo para llegar a ellos. Los preferidos van después y las visitas automáticas
al final. Si algún obligatorio no cabe, no se devuelve un plan incompleto: la respuesta
es HTTP 422 con `codigo: OBLIGATORIOS_PENDIENTES`, `mensaje` y una lista de `problemas`.
La interfaz conserva el formulario, muestra los motivos y ofrece revisar los lugares
o cambiar fechas y disponibilidad. Hay que editar y confirmar antes de volver a generar.
Los preferidos que no se incluyen se explican en los avisos y no bloquean el resultado.

Un horario ausente o que no se pueda interpretar no se convierte en abierto 24 horas:
la visita puede incluirse, también si es obligatoria, con «Horario pendiente de confirmar».
Los cierres interpretables sí se respetan. Se conservan ventanas, descansos, comidas,
duraciones y caminos disponibles. La heurística no prueba que exista o no una solución
global; si no consigue incluir todos los obligatorios, pide ajustes en lugar de afirmar
que se ha cumplido la selección.

`CriteriosItinerario` conserva turnos por categoría desde la muestra de IA hasta el
itinerario final. Los obligatorios y preferidos no consumen los turnos automáticos.
Cada lugar compartido se cuenta una sola vez, reservándolo para el interés con menos
alternativas en caso de empate. Se priorizan los intereses menos representados entre
los que aún tienen visitas alcanzables y compatibles ese día. Una categoría agotada,
cerrada o sin ruta deja de bloquear las demás. Si abre más tarde ese mismo día, se
puede reservar su turno y esperar. El recuento se mantiene entre días. Las señales
documentales y la relevancia estimada orientan la representatividad, sin certificarla
ni añadir llamadas o lotes de Gemini a los límites existentes.

`RepartoVisitas` reserva visitas para los días posteriores según las ventanas y los
horarios interpretables. Recalcula el objetivo diario con los candidatos pendientes,
distribuyendo primero los que tienen menos fechas compatibles y teniendo en cuenta
la duración y la pausa para comer. Primero busca una visita distinta para cada día,
recolocando reservas cuando sus fechas se solapan; después reparte las visitas extra.
No reserva candidatos sin un camino desde el origen. El objetivo es orientativo:
protege la reserva de cada día y no aplaza visitas cuya última fecha posible sea hoy.
Los obligatorios flexibles también se reparten, manteniendo su prioridad y la
comprobación final de que todos están incluidos. Las rutas, márgenes, descansos y horarios reales se comprueban
al insertar cada visita, y el resultado pasa por el validador del plan.

Con suficientes candidatos compatibles se evita consumirlos todos al principio y
dejar los últimos días vacíos. Si faltan candidatos de un interés se explica y se
ajusta el reparto a los disponibles; no se inventan visitas, horarios ni rutas. Un
día que realmente no pueda recibir visitas incluye una explicación con su fecha.
La corrección se aplica al generar planes nuevos; los viajes guardados se conservan.
Los evitados y sus alias quedan fuera de visitas y propuestas gastronómicas.
Cambiar las selecciones invalida la confirmación y la clave de reutilización de UT17.

Comprobación de aceptación: buscar un lugar, marcarlo obligatorio dos veces (una sola
entrada), cambiarlo a preferido y evitado, comprobar lista/mapa/resumen, volver y retirar
la selección. Cambiar intereses o exclusiones conserva los compatibles y avisa de los
retirados. Las pruebas del backend cubren además sesión, metadatos del servidor, caché,
búsqueda por nombre, protección antes de límites y obligatorios imposibles o con apertura
posterior. El frontend se comprueba con `npm run build`, `npm run lint` y navegación manual.

Las consultas a Geoapify disponen de 20 segundos de lectura. Places reintenta una sola
vez la misma petición únicamente si se agota ese tiempo; no repite errores HTTP de cuota,
clave o parámetros ni respuestas inválidas. El registro identifica las categorías,
la página y el intento sin incluir la clave ni la URL. Si ambos intentos fallan,
la interfaz informa de la espera agotada y permite reintentar, sin afirmar que no hay lugares.

#### Carga progresiva y reutilización de Geoapify

`muestraInicial` consulta un conjunto acotado de grupos por interés, sin páginas
adicionales ni geocodificación de calles. Historia usa las atracciones generales;
Cultura añade mercados y espacios culturales; Ocio combina actividades recreativas,
zoos/parques de atracciones y centros comerciales; Naturaleza consulta parques, playas
y espacios naturales. Arte y Gastronomía usan sus dos primeros grupos.
Los grupos compartidos se consultan una sola vez: Historia, Cultura, Ocio y Naturaleza
suponen ocho consultas distintas. Se omiten los grupos excluidos. La muestra se
clasifica, agrupa y filtra con las mismas reglas que los candidatos del planificador.
Una muestra vacía no se presenta como prueba de que no existen más lugares.

`MuestraLugaresService` reparte el límite de pantalla en cuotas iguales por interés
activo. Con un límite de 30 y cuatro intereses muestra hasta 28 lugares: siete de cada
categoría. Un lugar compartido ocupa una sola cuota. Se alternan las categorías en la
lista y los tipos dentro de cada categoría; las compras ocupan como máximo un tercio
de la cuota de Ocio (con un mínimo de una plaza). Si faltan alternativas, se muestra
el recuento y se invita a ampliar o buscar por nombre, sin rellenar con otra categoría.
Las selecciones manuales se presentan aparte. Una búsqueda por texto no aplica cuotas.

Las peticiones usan `bias=proximity` con el centro del destino para orientar la búsqueda
geográfica, salvo los grupos históricos específicos (`tourism.sights.*`): en las pruebas
de Madrid su ordenación externa agotaba los dos intentos de 20 segundos; sin ella,
ambos grupos respondieron en 6,58 y 8,69 s con 59 y 89 lugares. Estas consultas siguen
filtradas por la ciudad y se ordenan localmente para la pantalla. No son tiempos garantizados.
La comprobación posterior a través del backend completó la preparación de Madrid con
Historia, Cultura, Ocio y Naturaleza en 24,22 s: 712 candidatos compatibles, sin timeout
en estos grupos. Esta medición incluye el catálogo completo, no la evaluación con IA
ni el cálculo de recorridos.
Entre lugares del mismo tipo, se priorizan referencias Wikidata/Wikipedia
publicadas por el proveedor, la cantidad de nombres internacionales y la cercanía.
Son señales orientativas, no una puntuación de popularidad ni garantía de recuperar
todos los imprescindibles. Se reconocen también plazas ajardinadas publicadas como
parques. No hay listas de ciudades predefinidas ni llamadas adicionales a la IA.
`LugarDto` conserva el tipo y estas señales para repetir el mismo criterio al reutilizar
el catálogo; la pantalla muestra nombres, categorías y cantidades comprensibles.

**Cargar más lugares de mis intereses** ejecuta la preparación completa bajo demanda y
muestra inicialmente hasta 60 resultados; **Mostrar más resultados encontrados** permite
ver más del catálogo ya recuperado, hasta 150. Buscar por nombre agrupa las categorías
permitidas en una sola petición, sin obligar a cargar previamente el catálogo completo.
Las selecciones y sus estados se conservan al ampliar y al volver al resumen.

Las consultas independientes se ejecutan en paralelo y se reúnen en un orden estable;
las páginas de una misma consulta conservan su secuencia. `LimiteConsultasGeoapify`
limita cada instancia del backend a tres solicitudes simultáneas para todos sus usuarios
y separa sus inicios al menos 350 ms. También se aplica al autocompletado y a las calles.
Los límites de una clave compartida con otros procesos siguen aplicándose conjuntamente.

`CacheGeoapify` guarda hasta 128 respuestas públicas durante 15 minutos en memoria.
La clave distingue operación, destino, categorías, límite, página, nombre y sesgo geográfico. No almacena
decisiones personales, errores ni respuestas inválidas; sí admite una lista vacía válida.
Una consulta equivalente simultánea espera el resultado en vez de duplicar la petición.
Se reutiliza entre sesiones, pantalla de selección, preparación del plan y restaurantes.
Al generar se completan las búsquedas específicas y las páginas necesarias aprovechando
las respuestas iniciales vigentes. Las exclusiones se aplican de nuevo a los datos
recuperados, de modo que la caché no conserva preferencias antiguas. Reiniciar el backend
vacía esta caché; no requiere tablas, Redis, nuevas dependencias ni variables de entorno.

Medición del 24/11/2026, Madrid del 25 al 27 con Historia, Cultura local, Ocio y Naturaleza:
la muestra equilibrada de 28 lugares tardó 4,05 s, con siete resultados por categoría.
Incluyó el Retiro, Plaza Mayor, Plaza de España, mercados, bolera, zoo y parque de
atracciones, con dos centros comerciales. En la optimización anterior, antes de este
reparto y estas señales, la muestra inicial tardó 7,414 s frente a 45,9 s del catálogo
completo; repetirla tardó 0,008 s y abrirla en otra sesión 0,029 s. Son mediciones de
la búsqueda, no de la generación con IA,
y no garantizan una latencia fija del proveedor. Las pruebas simulan caché, caducidad,
concurrencia, filtros actuales, carga inicial, ampliación y reutilización por el planificador.


## UT24: control de propiedad de viajes

El propietario se obtiene del usuario autenticado en el backend. Las consultas
y actualizaciones de viajes comprueban ese propietario; conocer el identificador
de un viaje no concede acceso a sus datos.

Se verifican los cinco endpoints de viajes: listado, consulta, creación desde
una generación, cambio de título y actualización desde una generación.

- Sin autenticación, las peticiones con CSRF válido cuando corresponde reciben 401.
- Consultar o modificar un viaje ajeno recibe 404, sin revelar su contenido.
- Un viaje ajeno y uno inexistente producen la misma respuesta de consulta.
- Una generación solo puede guardarse desde la sesión que la produjo.
- Una propuesta válida propia no permite sobrescribir un viaje ajeno.
- Las escrituras sin CSRF o con un token incorrecto reciben 403.
- Cerrar sesión invalida el acceso anterior. Al volver a entrar, los viajes
  guardados siguen disponibles para su propietario.

Las pruebas de integración utilizan dos cuentas almacenadas en PostgreSQL
temporal y autenticadas mediante el login real. Comprueban que los rechazos
conservan los datos y las versiones existentes y no crean viajes adicionales.
El generador está simulado para evitar llamadas a proveedores externos.

Ejecutar las pruebas desde `backend`, con Docker Desktop disponible:

    .\mvnw.cmd test

El alcance comprende viajes, propuestas de generación y álbumes privados.
Las consultas y operaciones sobre fotografías también comprueban sesión,
propiedad y CSRF cuando modifican datos.

## UT25–UT27: editar las actividades de un viaje guardado

En **Mis viajes → Abrir viaje → Editar actividades** puedes eliminar visitas,
subirlas o bajarlas dentro del mismo día y sustituirlas por alternativas compatibles.
El editor muestra una propuesta con el recorrido y los horarios recalculados;
el viaje guardado no cambia hasta pulsar **Guardar cambios del itinerario**.
Cancelar conserva la versión anterior. La navegación avisa si quedan cambios pendientes.

Los conflictos de horarios, recorridos o disponibilidad bloquean el guardado.
Los avisos requieren aceptación explícita. Eliminar una visita obligatoria pide
confirmación y conserva su prioridad, indicando que queda incumplida. Una visita
obligatoria puede reordenarse, pero no sustituirse automáticamente.

Las alternativas proceden de los candidatos ya guardados: se filtran por preferencias,
exclusiones, gasto, duplicados y proximidad de hasta 10 km a la visita original.
Se comparan hasta seis candidatos y se ofrecen hasta tres que encajen en el día.
Elegir una opción exige confirmar; cancelar la selección no modifica el recorrido.
La búsqueda de alternativas no vuelve a consultar Gemini ni Geoapify.

Se reutilizan recorridos guardados con coordenadas coincidentes y una caché por
sesión de hasta 512 pares dirigidos durante 15 minutos. Solo los trayectos que faltan
se consultan a OpenRouteService, agrupados por origen y hasta 50 destinos por petición.
Los fallos no se cachean; **Recalcular recorridos** permite reintentarlos.

Todas las operaciones comprueban autenticación, propietario, CSRF y revisión del viaje.
Cada modificación renueva el identificador de propuesta para rechazar acciones obsoletas.
Los borradores viven en la sesión, caducan a los 30 minutos y se limitan a tres viajes.
El guardado utiliza la actualización atómica existente y conserva las versiones anteriores;
un fallo de escritura revierte la operación completa. No requiere nuevas tablas ni claves.

Los endpoints POST están bajo `/api/viajes/{viajeId}/edicion`: `/iniciar` y,
con `/{propuestaId}`, `/reordenar`, `/recalcular`, `/guardar`,
`/visitas/{actividadId}/eliminar`, `/visitas/{actividadId}/alternativas` y
`/visitas/{actividadId}/sustituir`. El servidor conserva y valida la propuesta;
no acepta un itinerario arbitrario enviado por el navegador.

Las pruebas cubren reconstrucción, horarios, comidas, caché, reutilización y errores
del proveedor. Las pruebas de integración usan PostgreSQL temporal, login y CSRF
reales, y OpenRouteService simulado: comprueban permisos, revisiones, persistencia,
obligatorios, alternativas y reversión de errores al guardar.


### Correcciones de la revisión de UT25–UT27

Un fallo del proveedor en rutas alternativas que no usa el recorrido final no bloquea
un día válido. Los conflictos de otros días permanecen visibles, pero no impiden
comparar sustituciones compatibles del día seleccionado.

Si una respuesta de edición se pierde, **Recuperar cambios pendientes** consulta el
borrador vigente sin reiniciarlo ni repetir la modificación. El endpoint POST
`/api/viajes/{viajeId}/edicion/recuperar` exige propietario, revisión y CSRF válidos.
Si el guardado ya se completó, hay que recargar el viaje para ver la nueva revisión.

Cada traslado del día reconstruido debe respetar la distancia seleccionada, con un
máximo de 10 km, comprobada sobre la distancia real de la ruta. Esto incluye los
trayectos del alojamiento y del restaurante.

Los avisos de edición se guardan también como incidencias estructuradas dentro del
JSON existente. Se conservan los de días intactos y se reemplazan los del día
recalculado. Los viajes anteriores sin estos metadatos siguen siendo compatibles;
sus avisos de texto se conservan para evitar perder información.


### Editor unificado de actividades

La edición se hace en las propias tarjetas del itinerario, con sus horarios,
trayectos y mapa actualizados. **Mover** permite elegir una posición dentro del día;
en escritorio también se puede arrastrar el control hasta otra visita del mismo día.
Las alternativas aparecen junto a la actividad con duración, coste y diferencia de
tiempo: **Elegir esta actividad** la aplica al borrador y **Conservar la actual**
cierra la comparación sin sustituirla.

**Eliminar** muestra una opción **Deshacer** por cada visita retirada. Restaurar
recalcula ese día y conserva las demás sustituciones y cambios pendientes. Solo
se restauran visitas retiradas de ese borrador, sin aceptar candidatos del navegador
ni permitir duplicados. Los obligatorios mantienen su prioridad. El endpoint POST
`/{propuestaId}/visitas/{actividadId}/restaurar`, bajo la ruta de edición del viaje,
aplica autenticación, propiedad, CSRF, revisión y vigencia de la propuesta.

Los conflictos aparecen junto a la visita afectada; los días señalan sus conflictos
y las visitas sin hueco siguen disponibles para editarlas. Una barra compacta encima
del itinerario reúne **Descartar** y **Guardar cambios**. **Revisar avisos** abre los
detalles y la aceptación dentro de la página, sin un panel flotante sobre el mapa.
En móvil la barra permanece en el flujo de la página para no tapar las tarjetas.
Se conserva el día seleccionado al actualizar el recorrido, se anuncian los cambios
y se devuelve el foco a la acción editada o al botón para deshacer. La interfaz
mantiene la alternativa de mover mediante teclado y funciona en pantalla móvil.

### UT28 · Mostrar los trazados del recorrido

El mapa del día dibuja los caminos peatonales de los bloques `TRASLADO` presentes
en el plan, incluidos alojamiento y restaurantes seleccionados. No añade la vuelta
al alojamiento ni conexiones que el Planner no haya programado. Las geometrías se
piden a Directions de OpenRouteService (`foot-walking`, GeoJSON, sin instrucciones),
usando la misma `ORS_API_KEY` del backend. No se consulta Gemini ni Geoapify.

Se cargan cuando el mapa del día es visible (en móvil, al abrir Mapa). La lista y
los marcadores siguen disponibles mientras se carga o si falla el proveedor. Un
aviso identifica los tramos sin geometría y ofrece reintentar; no se inventa una
línea recta como sustituto. Las coordenadas GeoJSON son longitud/latitud y solo se
invierten al presentarlas en Leaflet. No se modifican las horas del plan al dibujar.

El servidor resuelve los puntos desde el plan autorizado: no acepta una lista de
coordenadas arbitrarias del navegador. Los endpoints POST exigen sesión y CSRF:

- `/api/itinerarios/{generacionId}/trazados`: cuerpo `{ "dia": "2027-01-01" }`.
- `/api/viajes/{viajeId}/trazados`: cuerpo con `dia` y `revision`.
- `/api/viajes/{viajeId}/edicion/{propuestaId}/trazados`: cuerpo con `dia` y `revision`.

Los viajes y borradores comprueban propiedad y revisión antes de consultar ORS y
de conservar los resultados. Las propuestas obsoletas se rechazan. Cada consulta
dispone de 15 segundos y como máximo hay tres peticiones externas simultáneas en
el servicio; puede devolverse geometría parcial. Las peticiones equivalentes de
una sesión se comparten y se conservan hasta 256 geometrías correctas en memoria.
Los fallos no se cachean como éxitos.

El campo opcional `trazados` se guarda en el JSON existente `datos_generacion`, sin
tablas nuevas. Los viajes antiguos sin ese campo siguen siendo compatibles. Cargar
un día guardado enriquece únicamente ese caché derivado, con una actualización
atómica que no cambia actividades, revisión ni fecha del último guardado. En un
resultado nuevo se conserva en sesión hasta guardar el viaje; en edición se queda
en el borrador hasta Guardar cambios. Descartar conserva el itinerario guardado.

La clave de cada trazado incluye sentido, identificadores, coordenadas y perfil.
Al editar se conservan solo los tramos vigentes. El frontend retira inmediatamente
las líneas de la versión anterior, cancela la solicitud anterior e ignora respuestas
atrasadas. Las pruebas cubren GeoJSON inválido, errores parciales, deduplicación de
peticiones, persistencia entre sesiones, permisos, revisiones y retirada de trazados
después de eliminar actividades.

### UT29–UT31 · Álbumes privados y fotografías

Cada viaje guardado crea automáticamente **un único álbum privado**. La migración
V6 crea también los álbumes de los viajes anteriores. En **Mis recuerdos** se ve
la etiqueta **Viaje: …**, el destino y el número de fotos. Desde el detalle de
**Mis viajes** se puede abrir directamente el álbum correspondiente. Cambiar el
nombre del viaje actualiza su etiqueta sin crear otro álbum.

La relación es **foto → álbum → viaje**. No se pide ni guarda una asociación de
la foto con un lugar o una visita. Las antiguas columnas opcionales de lugares
permanecen en el esquema por compatibilidad, pero esta funcionalidad no las usa.
Los registros de ejemplo `demo://` siguen siendo marcadores, no fotos disponibles.

Se sube una imagen cada vez. Límites predeterminados configurables:

| Límite | Valor | Variable de entorno |
| --- | --- | --- |
| Formatos reales | JPEG y PNG | — |
| Tamaño por original | 10 MiB (10.485.760 bytes) | `FOTOS_MAX_ARCHIVO_BYTES` |
| Fotos por álbum | 100 | `FOTOS_MAX_POR_ALBUM` |
| Cuota por cuenta | 500 MiB, originales **y miniaturas** | `FOTOS_CUOTA_BYTES` |
| Dimensiones decodificadas | 40 millones de píxeles | `FOTOS_MAX_PIXELES` |
| Solicitud multipart completa | 11 MiB, incluido su envoltorio | `FOTOS_MAX_SOLICITUD_BYTES` |
| Descarte de subidas rechazadas por Tomcat | Hasta 12 MiB | `FOTOS_MAX_DESCARTE_BYTES` |

Si se aumenta el tamaño por foto, hay que ampliar también el límite de la
solicitud multipart y de descarte. La interfaz detecta tamaños excesivos usando el
límite que proporciona el backend; el servidor lo vuelve a comprobar. El backend comprueba extensión, contenido real, tamaño,
decodificación y dimensiones; no confía en el MIME declarado por el navegador.
La cuota se reserva bajo bloqueo de la cuenta para que dos subidas simultáneas
no superen el límite. También cuentan las subidas pendientes de limpiar.

Las fotos se almacenan fuera de `static` y de `public`, en la carpeta privada
`backend/.rute-data/fotos` al arrancar desde `backend`. Está excluida de Git.
`RUTE_FOTOS_DIRECTORIO` permite usar otra carpeta, preferiblemente absoluta.
PostgreSQL guarda metadatos y claves opacas; no guarda los bytes de las imágenes.
El original se conserva para la descarga y se genera una miniatura JPEG de hasta
480 píxeles para la galería. No se descargan todos los originales al abrirla.

`AlmacenamientoFotos` separa las operaciones de archivos del resto del código;
`AlmacenamientoFotosLocal` es su implementación actual. Al desplegar habrá que
configurar un disco persistente privado o añadir otra implementación, por ejemplo
Supabase Storage. **El almacenamiento local no sincroniza las fotos entre equipos**:
la base de datos y la carpeta deben corresponder al mismo entorno. No se ha
implementado todavía el almacenamiento en la nube.

Endpoints protegidos por sesión y comprobación de propietario en cada petición:

- `GET /api/albumes` y `GET /api/albumes/{id}`: listado y contenido propio.
- `POST /api/albumes/{id}/fotos`: multipart con un único campo archivo `foto`.
- `GET /api/fotos/{id}/miniatura`, `/contenido` y `/descargar`: miniatura, visor y original.
- `DELETE /api/fotos/{id}`: borrado confirmado de los dos archivos y sus metadatos.

POST y DELETE requieren CSRF. No se publican rutas físicas ni URLs públicas;
los archivos se sirven con `Cache-Control: no-store, private`. Cerrar sesión,
acceder desde otra cuenta o intentar usar la URL tras eliminar no da acceso.
Una copia que el usuario ya haya descargado no se puede retirar de su dispositivo.

Una foto solo pasa de `PENDIENTE` a `DISPONIBLE` después de almacenar original y
miniatura. Si falla, se intenta limpiar ambos; si la limpieza falla, queda `ERROR`,
sin imagen accesible y conservando la reserva de cuota hasta retirarla. Una subida
interrumpida en `PENDIENTE` se puede retirar pasados diez minutos, para no interferir
con una subida activa. El borrado usa `ELIMINANDO`: si falla, no se anuncia éxito y
se ofrece reintentar. La cuota solo se libera cuando se completa la limpieza.

La galería ofrece un estado vacío, visor con anterior/siguiente, descarga del
original y eliminación con confirmación. Cancelar conserva la foto. Ante un fallo
se mantiene el aviso y se vuelve a consultar el álbum para no mostrar éxito falso.

Las pruebas usan PostgreSQL efímero y fotos sintéticas. Cubren formatos inválidos,
imagen corrupta, tamaño y píxeles, cuota compartida, concurrencia, permisos,
correspondencia entre originales y miniaturas, fallos preparados de almacenamiento,
reintentos de borrado y pérdida de acceso a las URLs eliminadas.

### Eliminar viajes conservando recuerdos

**Mis viajes** ofrece **Eliminar viaje** en cada tarjeta y en el detalle. La
confirmación conserva el álbum y sus fotos por defecto; si el álbum no contiene
archivos, se retira automáticamente. El álbum conservado sigue en **Mis recuerdos**
con la etiqueta **Viaje eliminado: …**, su título, destino y fechas. Se pueden
seguir añadiendo o descargando fotos de ese álbum.

La opción **Eliminar también el álbum y todas sus fotos** está desmarcada por
defecto y advierte de la eliminación permanente de los archivos en Rute. Cancelar
no envía ninguna solicitud de eliminación. Los archivos ya descargados al equipo
del usuario no se modifican.

`DELETE /api/viajes/{id}` recibe `{ "revision": 1, "eliminarRecuerdos": false }`.
Exige sesión, propiedad, CSRF, revisión vigente y una elección explícita. La
migración V7 añade la eliminación lógica del viaje: su referencia se conserva
en PostgreSQL, pero ya no se lista ni se puede abrir, editar o enriquecer con
trazados. Esto mantiene la asociación del álbum sin duplicar los datos del viaje.
No es una función de borrado definitivo de todos los registros históricos del viaje.

Al eliminar también los recuerdos se bloquea primero su acceso. No se admiten
nuevas subidas mientras dura la operación y una subida activa impide iniciarla.
El servicio retira los originales y miniaturas antes de eliminar sus metadatos,
el álbum y la reserva de cuota. Si falla el disco o la confirmación en PostgreSQL,
no se devuelve éxito: el viaje muestra **Completar eliminación**, los archivos
permanecen inaccesibles y se puede reintentar. Los reintentos tras un borrado ya
confirmado son seguros. No se consulta ningún proveedor externo.

### Eliminar un álbum y todas sus fotos

En **Mis recuerdos** aparece **Eliminar álbum**, tanto en la tarjeta como dentro
del álbum. La confirmación advierte que el álbum y **todas sus fotos se borrarán
permanentemente** y que no se puede deshacer. **Cancelar** no envía ninguna
petición de borrado. El viaje permanece guardado.

`DELETE /api/albumes/{id}` exige sesión, propietario y CSRF. La migración V8
incorpora el estado `eliminando` del álbum. Antes de borrar archivos se bloquean
sus fotos y nuevas subidas; si hay una subida activa, se pide esperar. Solo se
confirma cuando se han eliminado originales, miniaturas, metadatos y el álbum,
liberando la cuota correspondiente. Si falla el disco o PostgreSQL, no se anuncia
éxito: aparece **Reintentar eliminación**, los archivos permanecen inaccesibles
y el proceso puede completarse después. No se modifican otros álbumes ni viajes.

El álbum eliminado no se recrea al abrir o renombrar el viaje. El acceso desde
**Mis viajes** informa de que ese viaje ya no tiene álbum. El borrado no puede
retirar copias que el usuario haya descargado previamente a su dispositivo.

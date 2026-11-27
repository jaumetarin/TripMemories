# Validación AVD en navegador y HTTP

## Entorno aislado

Requiere Docker Desktop, Java 25, Node.js 24 y Chrome o Brave. Desde la raíz del repositorio, iniciar la base de datos temporal.

```powershell
docker compose -f e2e/compose.yaml up -d --wait
```

En una terminal dedicada al backend, configurar la conexión de pruebas y arrancar. Se aplican las migraciones existentes. La base de datos de desarrollo utiliza otra conexión. Los datos de esta base de pruebas se descartan al detener su contenedor.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:15432/rute_avd'
$env:DB_USER = 'rute_avd'
$env:DB_PASSWORD = 'rute_avd_local'
cd backend
.\mvnw.cmd spring-boot:run
```

Esperar al mensaje de arranque completo. En otra terminal, desde la raíz, preparar la cuenta con la contraseña pública de pruebas `PruebaE2E2026!`. La fixture almacena su hash Argon2id y verifica previamente el nombre de la base de datos. Puede repetirse para restablecer esa cuenta.

```powershell
Get-Content e2e/fixture-cuenta.sql | docker compose -f e2e/compose.yaml exec -T db psql -U rute_avd -d rute_avd
```

## Selenium

El backend necesita `GEOAPIFY_API_KEY` en su terminal para seleccionar un destino real. La prueba consume el autocompletado y valida fechas contra el backend. Configurar la clave mediante el procedimiento del README principal y reiniciar el backend si es necesario.

En una terminal del frontend, desde `frontend`.

```powershell
npm ci
npm run dev -- --host localhost --port 5173 --strictPort
```

Desde `e2e`, ejecutar la prueba.

```powershell
npm ci
npm run test:e2e
```

Se utilizan `http://localhost:5173`, la cuenta de la fixture y Madrid. Las variables opcionales `RUTE_URL`, `RUTE_DEMO_EMAIL`, `RUTE_DEMO_PASSWORD`, `RUTE_DESTINO` y `RUTE_CHROME_BINARY` permiten indicar otro entorno preparado. Conservar las credenciales de cuentas propias fuera de Git. Cada ejecución produce un `resultados/<fecha>/informe.json` con navegador, versión y escenarios superados. Ante un fallo también guarda una captura. El código de salida es distinto de cero si falla una comprobación.

E2E-4 selecciona una sugerencia real, usa fechas futuras, exige el mensaje específico de salida anterior a llegada y verifica el avance a preferencias después de corregirla. El ensayo cubre esos cinco escenarios. La generación completa, el mapa y la persistencia del viaje requieren sus propios escenarios.

## JMeter

Los planes están en `carga/`. Requieren Apache JMeter 5.6.3 y un backend recién iniciado sobre la cuenta verificada de esta fixture. Reiniciar solo el proceso del backend antes de comenzar una pareja de ensayos para restablecer los contadores por IP. Mantener la base de datos de pruebas encendida. Evitar ejecutar Selenium u otros accesos durante la campaña.

Desde `e2e`, crear una carpeta de resultados para la ejecución y lanzar el ensayo de operación normal.

```powershell
$runAvd = Join-Path 'resultados' (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $runAvd -Force | Out-Null
jmeter -n -t carga/plan-rute.jmx -l "$runAvd/normal.jtl" -j "$runAvd/normal.log" -e -o "$runAvd/normal-html"
```

El plan usa diez hilos, ramp-up de cinco segundos, duración de sesenta segundos y una pausa de cien milisegundos por petición. Cada hilo obtiene CSRF, autentica una vez y renueva CSRF. Después repite cuatro endpoints locales. Las aserciones comprueban código 200 y contenido esperado. El login de la fixture ya usa Argon2id. El endpoint de fechas calcula fechas futuras.

Ejecutar después la ráfaga dentro de la misma ventana de quince minutos y sobre el mismo backend. Los diez accesos del plan anterior deben haber agotado el límite de login de la IP.

```powershell
jmeter -n -t carga/plan-rute-20hilos-burst.jmx -l "$runAvd/burst.jtl" -j "$runAvd/burst.log" -e -o "$runAvd/burst-html"
```

La ráfaga usa veinte hilos y ramp-up de un segundo. Cada hilo obtiene CSRF y exige un 429 con `Retry-After: 900` al intentar acceder. Una respuesta 200 en este ensayo significa que no se preparó el estado de cuota agotada. La aserción acepta expresamente el 429 esperado y conserva su código en el JTL.

Desde PowerShell, resumir las dos salidas CSV de JMeter. El script genera `resumen.csv`, calcula p95 por rango más próximo y falla si encuentra respuestas inesperadas, aserciones fallidas o un p95 de 500 ms o más en alguna operación.

```powershell
.\carga\resumir-resultados.ps1 -Normal "$runAvd/normal.jtl" -Burst "$runAvd/burst.jtl"
```

Los parámetros `host`, `puerto`, `usuarios`, `duracion` y `rampa` del plan normal se pueden pasar con `-Jnombre=valor`. Para la campaña por una sola IP, mantener los diez usuarios y la cuenta de pruebas. Registrar equipo, commit, comandos y condiciones. Repetir la pareja de ensayos restableciendo el backend entre parejas. Informar las métricas por operación y distinguir las peticiones iniciales de los endpoints repetidos. La muestra de diez logins limita la estimación de su p95.

Estos planes son nuevas campañas reproducibles. Las cifras de la memoria deben salir de los nuevos JTL conservados. El caso 409 y la generación completa quedan fuera de estos dos planes.

## Finalizar

Detener el frontend y backend de estas terminales con Ctrl+C. Desde la raíz, detener el contenedor de pruebas.

```powershell
docker compose -f e2e/compose.yaml down
```

Las variables DB de la terminal del backend corresponden al entorno aislado. Utilizar una terminal nueva para volver al arranque normal del proyecto.

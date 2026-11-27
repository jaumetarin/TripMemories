/**
 * Pruebas E2E automatizadas con Selenium WebDriver para Rute (TripMemories).
 * Basado en la metodología de Práctica 3 (Selenium WebDriver, Node.js).
 *
 * Requisitos para ejecución completa:
 *   1. Base de datos PostgreSQL activa y cuenta de prueba verificada (ver README.md).
 *   2. Backend Spring Boot en http://localhost:8080 y Frontend Vite en http://localhost:5173.
 *   3. Navegador Chrome instalado (Selenium Manager resuelve ChromeDriver automáticamente).
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { Builder, Browser, By, Key, until } = require('selenium-webdriver');
const chrome = require('selenium-webdriver/chrome');

const BASE_URL = process.env.RUTE_URL || 'http://localhost:5173';
const DEMO_EMAIL = process.env.RUTE_DEMO_EMAIL || 'avd-e2e@rute.local';
const DEMO_PASSWORD = process.env.RUTE_DEMO_PASSWORD || 'PruebaE2E2026!';
const BRAVE_PATH = process.env.RUTE_CHROME_BINARY || 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe';
const DESTINO = process.env.RUTE_DESTINO || 'Madrid';

function fechaFormulario(fecha) {
  return [fecha.getDate(), fecha.getMonth() + 1, fecha.getFullYear()]
    .map(numero => String(numero).padStart(2, '0')).join('/');
}

async function escribirFecha(driver, id, fecha) {
  const campo = await driver.findElement(By.id(id));
  await campo.sendKeys(Key.CONTROL, 'a', Key.NULL, fechaFormulario(fecha), Key.TAB);
}

(async function ejecutarSuiteE2E() {
  const carpeta = path.join(__dirname, 'resultados', new Date().toISOString().replace(/[:.]/g, '-'));
  fs.mkdirSync(carpeta, { recursive: true });
  const informe = { inicio: new Date().toISOString(), url: BASE_URL, casos: [] };
  const options = new chrome.Options();
  if (fs.existsSync(BRAVE_PATH)) {
    options.setChromeBinaryPath(BRAVE_PATH);
  }
  options.addArguments('--headless=new', '--no-sandbox', '--disable-gpu', '--window-size=1280,900');

  let driver;
  try {
    driver = await new Builder()
      .forBrowser(Browser.CHROME)
      .setChromeOptions(options)
      .build();
    const capacidades = await driver.getCapabilities();
    informe.navegador = capacidades.get('browserName');
    informe.version = capacidades.get('browserVersion');
    // Caso E2E-1: Portada pública y redirección de rutas protegidas sin sesión (RF1)
    await driver.get(BASE_URL);
    await driver.wait(until.elementLocated(By.id('titulo-inicio')), 30000);
    const tituloInicio = await driver.findElement(By.id('titulo-inicio')).getText();
    assert.ok(tituloInicio.includes('Lo mejor está por venir'), 'La portada debe mostrar el título principal');

    const botonCrear = await driver.findElement(By.css('button[aria-label="Crear itinerario"]'));
    await botonCrear.click();
    await driver.wait(until.elementLocated(By.id('cuenta-titulo')), 10000);
    const avisoSesion = await driver.findElement(By.css('.sesion-aviso')).getText();
    assert.ok(avisoSesion.includes('Inicia sesión'), 'Sin sesión debe redirigir al formulario de acceso');
    informe.casos.push({ id: 'E2E-1', estado: 'SUPERADO' });

    // Caso E2E-2: Validación en cliente de requisitos de contraseña en Crear cuenta (RF1)
    const pestanaRegistro = await driver.findElement(By.xpath('//button[normalize-space()="Crear cuenta"]'));
    await pestanaRegistro.click();
    const campoPass = await driver.findElement(By.id('cuenta-contrasena'));
    await campoPass.sendKeys('debil');
    await driver.findElement(By.id('cuenta-correo')).click(); // dispara onBlur
    await driver.wait(until.elementLocated(By.id('cuenta-estado-contrasena')), 5000);
    const estadoPass = await driver.findElement(By.id('cuenta-estado-contrasena')).getText();
    assert.ok(estadoPass.includes('Completa los requisitos'), 'Debe advertir sobre requisitos de contraseña pendientes');
    informe.casos.push({ id: 'E2E-2', estado: 'SUPERADO' });

    // Caso E2E-3: Login con credenciales incorrectas y luego válidas (RF1 -> RF2)
    const pestanaLogin = await driver.findElement(By.xpath('//button[normalize-space()="Iniciar sesión"]'));
    await pestanaLogin.click();
    await driver.findElement(By.id('cuenta-correo')).clear();
    await driver.findElement(By.id('cuenta-correo')).sendKeys(DEMO_EMAIL);
    await driver.findElement(By.id('cuenta-contrasena')).clear();
    await driver.findElement(By.id('cuenta-contrasena')).sendKeys('ContrasenaErronea1!');
    await driver.findElement(By.css('button.cuenta-enviar')).click();
    await driver.wait(until.elementLocated(By.css('.cuenta-error')), 8000);

    await driver.findElement(By.id('cuenta-contrasena')).clear();
    await driver.findElement(By.id('cuenta-contrasena')).sendKeys(DEMO_PASSWORD);
    await driver.findElement(By.css('button.cuenta-enviar')).click();
    await driver.wait(until.elementLocated(By.css('button.cerrar-sesion')), 10000);
    informe.casos.push({ id: 'E2E-3', estado: 'SUPERADO' });

    // Caso E2E-4: rechazar fechas invertidas y avanzar al corregirlas con destino válido.
    await driver.findElement(By.css('button[aria-label="Crear itinerario"]')).click();
    await driver.wait(until.elementLocated(By.id('crear-titulo')), 8000);
    await driver.findElement(By.css('input[name="destino"]')).sendKeys(DESTINO);
    const sugerencia = await driver.wait(until.elementLocated(By.css('.destino-sugerencias [role="option"]')), 30000);
    await sugerencia.click();
    await driver.wait(async () => (await driver.findElement(By.css('.destino-estado')).getText())
      .startsWith('Destino seleccionado:'), 5000);

    const llegada = new Date();
    llegada.setDate(llegada.getDate() + 7);
    const salidaAnterior = new Date(llegada);
    salidaAnterior.setDate(salidaAnterior.getDate() - 1);
    const salidaCorrecta = new Date(llegada);
    salidaCorrecta.setDate(salidaCorrecta.getDate() + 2);
    await escribirFecha(driver, 'fecha-llegada', llegada);
    await escribirFecha(driver, 'fecha-salida', salidaAnterior);
    await driver.findElement(By.css('button.continuar')).click();
    const mensajeFechas = 'La salida debe ser posterior a la llegada.';
    await driver.wait(async () => (await driver.findElement(By.id('errores-viaje')).getText())
      .includes(mensajeFechas), 10000);
    const errores = await driver.findElement(By.id('errores-viaje')).getText();
    assert.ok(!errores.includes('Selecciona un destino'), 'El destino debe seguir siendo válido');
    assert.ok(await driver.findElement(By.id('fecha-llegada')).isDisplayed(), 'La ventana inválida debe bloquear el avance');
    await escribirFecha(driver, 'fecha-salida', salidaCorrecta);
    await driver.findElement(By.css('button.continuar')).click();
    const preferencias = await driver.wait(until.elementLocated(By.css('form[aria-label="Preferencias y gasto"]')), 10000);
    await driver.wait(until.elementIsVisible(preferencias), 10000);
    informe.casos.push({ id: 'E2E-4', estado: 'SUPERADO', llegada: fechaFormulario(llegada), salida: fechaFormulario(salidaCorrecta) });

    // Caso E2E-5: Cierre de sesión y verificación de protección posterior (RF1)
    await driver.findElement(By.css('button.cerrar-sesion')).click();
    await driver.wait(until.elementLocated(By.id('titulo-inicio')), 8000);
    await driver.findElement(By.css('button[aria-label="Crear itinerario"]')).click();
    await driver.wait(until.elementLocated(By.id('cuenta-titulo')), 8000);
    informe.casos.push({ id: 'E2E-5', estado: 'SUPERADO' });

    informe.estado = 'SUPERADO';
    console.log(`E2E Selenium: 5/5 escenarios superados en ${informe.navegador} ${informe.version}.`);
  } catch (error) {
    informe.estado = 'FALLIDO';
    informe.error = error.message;
    if (driver) {
      try {
        informe.consola = await driver.manage().logs().get('browser');
        fs.writeFileSync(path.join(carpeta, 'fallo.png'), await driver.takeScreenshot(), 'base64');
      } catch (diagnostico) {
        informe.errorDiagnostico = diagnostico.message;
      }
    }
    throw error;
  } finally {
    informe.fin = new Date().toISOString();
    fs.writeFileSync(path.join(carpeta, 'informe.json'), JSON.stringify(informe, null, 2));
    console.log(`Informe E2E: ${carpeta}`);
    if (driver) await driver.quit();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});

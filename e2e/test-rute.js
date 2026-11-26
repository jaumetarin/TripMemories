/**
 * Pruebas E2E automatizadas con Selenium WebDriver para Rute (TripMemories).
 * Basado en la metodología de Práctica 3 (Selenium WebDriver, Node.js).
 *
 * Requisitos para ejecución completa:
 *   1. Base de datos PostgreSQL activa (docker compose up -d --wait db) y seed cargado.
 *   2. Backend Spring Boot en http://localhost:8080 y Frontend Vite en http://localhost:5173.
 *   3. Navegador Chrome instalado (Selenium Manager resuelve ChromeDriver automáticamente).
 */
const assert = require('assert');
const fs = require('fs');
const { Builder, Browser, By, until } = require('selenium-webdriver');
const chrome = require('selenium-webdriver/chrome');

const BASE_URL = process.env.RUTE_URL || 'http://localhost:5173';
const DEMO_EMAIL = process.env.RUTE_DEMO_EMAIL || 'avd-e2e@rute.local';
const DEMO_PASSWORD = process.env.RUTE_DEMO_PASSWORD || 'PruebaE2E2026!';
const BRAVE_PATH = process.env.RUTE_CHROME_BINARY || 'C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe';

(async function ejecutarSuiteE2E() {
  const options = new chrome.Options();
  if (fs.existsSync(BRAVE_PATH)) {
    options.setChromeBinaryPath(BRAVE_PATH);
  }
  options.addArguments('--headless=new', '--no-sandbox', '--disable-gpu', '--window-size=1280,900');

  const driver = await new Builder()
    .forBrowser(Browser.CHROME)
    .setChromeOptions(options)
    .build();
  try {
    // Caso E2E-1: Portada pública y redirección de rutas protegidas sin sesión (RF1)
    await driver.get(BASE_URL);
    await driver.wait(until.elementLocated(By.id('titulo-inicio')), 10000);
    const tituloInicio = await driver.findElement(By.id('titulo-inicio')).getText();
    assert.ok(tituloInicio.includes('Lo mejor está por venir'), 'La portada debe mostrar el título principal');

    const botonCrear = await driver.findElement(By.css('button[aria-label="Crear itinerario"]'));
    await botonCrear.click();
    await driver.wait(until.elementLocated(By.id('cuenta-titulo')), 10000);
    const avisoSesion = await driver.findElement(By.css('.sesion-aviso')).getText();
    assert.ok(avisoSesion.includes('Inicia sesión'), 'Sin sesión debe redirigir al formulario de acceso');

    // Caso E2E-2: Validación en cliente de requisitos de contraseña en Crear cuenta (RF1)
    const pestanaRegistro = await driver.findElement(By.xpath('//button[normalize-space()="Crear cuenta"]'));
    await pestanaRegistro.click();
    const campoPass = await driver.findElement(By.id('cuenta-contrasena'));
    await campoPass.sendKeys('debil');
    await driver.findElement(By.id('cuenta-correo')).click(); // dispara onBlur
    await driver.wait(until.elementLocated(By.id('cuenta-estado-contrasena')), 5000);
    const estadoPass = await driver.findElement(By.id('cuenta-estado-contrasena')).getText();
    assert.ok(estadoPass.includes('Completa los requisitos'), 'Debe advertir sobre requisitos de contraseña pendientes');

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

    // Caso E2E-4: Acceso a Crear itinerario tras autenticar y validación de fechas/destino (RF2)
    await driver.findElement(By.css('button[aria-label="Crear itinerario"]')).click();
    await driver.wait(until.elementLocated(By.id('crear-titulo')), 8000);
    await driver.findElement(By.id('fecha-llegada')).sendKeys('05/12/26');
    await driver.findElement(By.id('fecha-salida')).sendKeys('01/12/26');
    await driver.findElement(By.css('button.continuar')).click();
    await driver.wait(until.elementLocated(By.css('#errores-viaje .error-viaje')), 8000);
    const errorViaje = await driver.findElement(By.id('errores-viaje')).getText();
    assert.ok(errorViaje.length > 0, 'Debe mostrar un error de validación cuando faltan destino o fechas válidas');

    // Caso E2E-5: Cierre de sesión y verificación de protección posterior (RF1)
    await driver.findElement(By.css('button.cerrar-sesion')).click();
    await driver.wait(until.elementLocated(By.id('titulo-inicio')), 8000);
    await driver.findElement(By.css('button[aria-label="Crear itinerario"]')).click();
    await driver.wait(until.elementLocated(By.id('cuenta-titulo')), 8000);

    console.log('E2E Selenium suite PASSED (5/5 escenarios verificados en Brave).');
  } finally {
    await driver.quit();
  }
})();

# Contexto y requisitos de la memoria AVD-PIN

Documento de referencia para preparar la memoria en PDF y las pruebas asociadas en la rama `avd`. Recoge íntegramente las instrucciones proporcionadas, las presentaciones **AVD-PIN 1** (Sesión 3 + anotaciones aula IS21), **AVD-PIN 2** (Sesión 5) y **AVD-PIN 3** (Sesión 8), las rúbricas oficiales (**D1**, **Entrega 2** y **D4 Presentaciones**) y las herramientas vistas en los laboratorios.

> **Nota general de la asignatura**:
> - Las entregas parciales (Entrega 1: 16 de octubre; Entrega 2: 27 de noviembre) son **voluntarias** para recibir *feedback*.
> - La **Entrega final** (fecha límite: **12 de enero**) es obligatoria y representa el **15 %** de la nota (y la **presentación oral** en diciembre representa el **5 %**; todos los miembros del equipo deben participar en el trabajo —indicándolo en la memoria— y en la presentación, con una dedicación esperada de 10–12 horas/persona).
> - Los documentos fuente del curso están en `docs/` (`AVD-PIN-1-1.pdf`, `AVD-PIN-1-aula-IS21.pdf`, `AVD-PIN-2-2.pdf`, `AVD-PIN-3.pdf` y los boletines/diapositivas de QuickCheck, KLEE y Selenium WebDriver).

---

## 1. Estructura obligatoria de la memoria (PDF)

La segunda entrega **amplía** la memoria anterior (no debe entregarse un documento nuevo solo con la segunda parte, y no hace falta mencionar explícitamente "Entrega 1 / Entrega 2" dentro del texto).

1. **Portada**
   - Título del proyecto.
   - Componentes del equipo, indicando el **grupo de prácticas** de cada alumno (`IS11`, `IS12`, `IS21`).
   - *(Requisito de `AVD-PIN-1`)*: debe quedar constancia de la participación de todos los miembros del equipo en el trabajo.
2. **1.- Introducción** *(aprox. 2–4 páginas; 10–15 páginas total orientativo para toda la memoria)*
   - Descripción breve del proyecto PIN (1–2 párrafos), incluyendo arquitectura, tecnología utilizada para su desarrollo (1–2 párrafos) y valor añadido del producto.
   - Características críticas/importantes que serán analizadas en AVD-PIN (~1 página):
     - **No poner todos los requerimientos** ni hacer una ERS.
     - Seleccionar **1 o 2 aspectos funcionales** y **1 o 2 aspectos no funcionales** críticos/relevantes, descritos de forma informal en lenguaje natural (en los apartados 2 y 3 se especificarán formalmente y se validará únicamente sobre esos aspectos, no sobre toda la aplicación).
   - *(Opcional)* **Requerimiento relacionado con los ODS**:
     - **No** explicar la relación de la aplicación ya desarrollada con los ODS.
     - Proponer un **requerimiento nuevo**, transversal e independiente de la funcionalidad original de la aplicación, justificando informalmente su relación con los ODS.
     - Ejemplos dados en clase (`AVD-PIN-1`): móvil de gama baja (ODS 1/4), sin publicidad de hábitos poco saludables (ODS 3), accesibilidad para personas mayores o con problemas visuales/auditivos leves (ODS 3/10), lenguaje no discriminatorio (ODS 5), consumo responsable de recursos (ODS 7/12/13), soporte multiidioma incluyendo minorías (ODS 1/10).
     - Añadirlo no obliga necesariamente a implementarlo ni validarlo (es opcional implementarlo y validarlo).
   - **Propuesta inicial de plan de pruebas / análisis / validación**:
     - Herramientas útiles para pruebas, análisis y depuración (ejemplos mencionados en clase: **SonarQube** para análisis estático; **LLM, QuickCheck, EvoSuite, UnitTestBot, KLEE** para generación; **Selenium WebDriver** para pruebas web E2E; **JMeter** para prestaciones; **cuestionarios / feria de proyectos** para usabilidad), cubriendo los cuatro procesos:
       1. Generación de casos de prueba (priorizar herramientas automáticas frente a la creación exclusivamente manual).
       2. Ejecución de casos de prueba.
       3. Medida de cobertura (obligatorio medirla e incluir una herramienta para ello en el plan).
       4. Gestión de casos de prueba.
     - Además, herramientas para aspectos **no funcionales** (creación y gestión de cuestionarios, medidas de tiempos de respuesta, uso de CPU, latencia, etc.).
     - Descripción breve de cómo se usarán en el ciclo de desarrollo para validar los aspectos críticos destacados y el impacto esperado.
3. **2.- Validación de requerimientos funcionales** *(aprox. 5–10 páginas)*
   - **2.1.- Requerimientos considerados**: describir con detalle **1–2 requerimientos relacionados** (entre 1 y 3 según Sesión 8; formato habitual de requerimientos, incluyendo pruebas de aceptación —p. ej., las de PIN/Patricio—, posibles casos de error, etc.). Deben corresponderse con los aspectos mencionados en la Introducción.
   - **2.2.- Proceso de validación**: herramientas utilizadas para generación, ejecución, gestión y medida de cobertura de los casos de prueba, y **cómo se han usado** (detalles de configuración, problemas encontrados, grado de automatización y un ejemplo claro paso a paso).
   - **2.3.- Resultados obtenidos**: cobertura alcanzada (incluir **tabla** con los datos o salida de la herramienta y explicar cómo se calcula y por qué es razonable), errores detectados (si los hubiera), análisis crítico de los resultados y propuestas de actuación derivadas.
4. **3.- Validación de requerimientos no funcionales** *(aprox. 2–3 páginas)*
   - **3.1.- Requerimientos considerados**: describir con detalle **1–2 requerimientos relacionados** en formato habitual (por ejemplo, uno de prestaciones y otro de usabilidad), coherentes con los mencionados en la Introducción.
   - **3.2.- Proceso de validación**: herramientas utilizadas (generación de cuestionarios, representación de resultados, medidas de prestaciones como JMeter, etc.) y **cómo se han usado**.
   - **3.3.- Resultados obtenidos**: representación de los resultados (tablas, gráficos, etc. —si se usan cuestionarios, gráfica aquí y el cuestionario completo en un Anexo—), análisis crítico de los mismos, problemas encontrados, tareas pendientes y actuaciones planificadas.
5. **4.- Conclusiones** *(1 página o menos)*
   - Valoración general del trabajo realizado, aspectos positivos/negativos, confianza ganada en la aplicación y qué se habría hecho diferente.
   - Si hay aspectos que no se hayan podido probar aún porque la aplicación no está completamente implementada, dejarlo indicado para revisarlo cuando esté completa.
6. **Bibliografía**
   - Incluir referencias relacionadas con el tema del proyecto y con las herramientas empleadas, citándolas desde el texto.
   - **Prohibido citar entradas de Wikipedia** (revisar en su lugar las referencias originales que cita Wikipedia).
   - Formato: autor, título y datos de publicación (si es documento online: URL y fecha de consulta; si no: editorial, volumen, páginas, año, etc.).
   - Diferenciar **notas al pie** (*footnote*) de **referencias bibliográficas**: las URLs sueltas pueden ir como nota al pie; artículos, libros o documentos online van como referencia bibliográfica.
7. **Anexos (si es necesario)**

---

## 2. Errores comunes a evitar (Sesión 5)

- Hacer una ERS completa en lugar de centrarse en 1–2 aspectos críticos funcionales y 1–2 no funcionales.
- Olvidar herramientas para alguno de los 4 procesos (generación, ejecución, cobertura, gestión) o para los aspectos no funcionales.
- Proponer solo creación manual de casos en JUnit sin indicar cómo se generan automáticamente.
- No medir la cobertura ni incluir tabla de cobertura y su justificación.
- **Formato del documento**:
  - No usar capturas de pantalla para mostrar código (usar texto/bloques formateados).
  - Evitar huecos en blanco sin motivo (figuras mal colocadas como objetos flotantes).
  - Referenciar todas las figuras y tablas desde el texto.
  - Cuidar la redacción (evitar redacción pobre o genérica) y pasar corrector ortográfico/sintáctico.
- No mencionar "Entrega 1" o "Entrega 2" dentro de la memoria.

---

## 3. Rúbrica — Bloque 1 (D1 - Entrega 1: Contexto, objetivos, herramientas, plan)

| Criterio | Sobresaliente (10) | Bien (7.5) | Suficiente (5) | Necesita mejorar (2.5) |
| --- | --- | --- | --- | --- |
| **Contexto** | Describe la solución incluyendo arquitectura del sistema, tecnología usada y el valor añadido de disponer del producto. | Describe de forma parcial la solución, o falta arquitectura o impacto/valor añadido. | Describe la arquitectura pero no quedan claros los objetivos ni el valor añadido. | No deja clara la solución, el valor añadido ni la arquitectura. |
| **Objetivos** | Identifica aspectos críticos/vulnerabilidades tanto funcionales como no funcionales, y especifica objetivos principales de garantía de calidad mediante técnicas de prueba, análisis y verificación. | Identifica vulnerabilidades y aspectos críticos funcionales y no funcionales (sin detallar igual los objetivos de calidad). | Identifica solo vulnerabilidades/aspectos críticos de la parte funcional. | No identifica claramente vulnerabilidades/aspectos críticos ni objetivos de calidad. |
| **Herramientas** | Identifica al menos una herramienta/metodología para atacar cada aspecto crítico/vulnerabilidad y describe brevemente cada herramienta y cómo se beneficia el sistema. | Identifica al menos una herramienta/metodología por aspecto crítico y la describe brevemente. | Identifica suficientes herramientas útiles pero no queda claro cómo se aplicarán durante el desarrollo. | No identifica suficientes herramientas útiles; algún aspecto crítico queda sin cubrir. |
| **Plan de Testing / Verificación / Análisis** | Explica la primera propuesta de plan de prueba/verificación/análisis con metodología clara, el equipo sabe aplicarla y describe escuetamente el impacto esperado. | Explica la propuesta y el impacto esperado, pero no queda claro cómo se implementará. | Explica algunos aspectos del uso e impacto, pero no hay un plan global. | No explica el impacto del plan en el proyecto. |

---

## 4. Rúbrica — Bloque 2 (Entrega 2: Automatización, Cobertura, Análisis de resultados)

| Criterio | Sobresaliente (10) | Bien (7.5) | Suficiente (5) | Necesita mejorar (2.5) |
| --- | --- | --- | --- | --- |
| **Automatización** | Describe adecuadamente el software/tecnología usada para automatizar e incluye un **ejemplo explicado** paso a paso del proceso de automatización, claro y útil para replicar una tarea similar. La automatización es correcta. | No describe adecuadamente la tecnología pero sí incluye un ejemplo explicado útil; la automatización tiene alguna deficiencia. | Describe adecuadamente la tecnología e incluye ejemplo, pero falta información/detalle para que sea útil a quien quiera replicar la tarea. | Describe adecuadamente la tecnología pero **no** incluye ejemplo de uso. |
| **Cobertura** | Los casos automatizados consiguen una cobertura razonable. Se describe la cobertura alcanzada, **cómo se calcula** y se **justifica** el grado alcanzado con la selección de casos automatizados. | Describe cobertura, cálculo y justificación, pero la cobertura no es suficiente o no se justifica bien la selección de casos. | No se justifica la cobertura alcanzada aunque se muestra el conjunto de casos automatizados. | No se automatizan suficientes casos y no se justifica. |
| **Análisis de resultados** | Hace un **análisis crítico** de los resultados obtenidos tras ejecutar los casos de prueba y **propone actuaciones** derivadas de los resultados. | Hace análisis crítico pero no propone actuaciones derivadas. | Hace un resumen de resultados sin analizarlos para sacar conclusiones ni proponer actuaciones. | No hace ni resumen ni análisis de los resultados. |

---

## 5. Rúbrica — Presentaciones (D4 - 5 %)

Fechas orientativas en `AVD-PIN-3`: Semana 1 (9 y 10 de diciembre) y Semana 2 (16 y 17 de diciembre). Todos los miembros del equipo deben participar.

| Criterio | Sobresaliente (10) | Bien (7.5) | Suficiente (5) | Necesita mejorar (2.5) |
| --- | --- | --- | --- | --- |
| **Comunicación** | Transmite ideas de forma clara, incluyendo aspectos más importantes del proceso, con excelente uso del lenguaje y vocabulario técnico. | Transmite ideas principales y conclusiones con uso correcto del vocabulario técnico. | Transmite parte de las ideas principales; algunas expresiones técnicas no son del todo correctas o precisas. | No sabe transmitir el trabajo o puntos clave; vocabulario técnico incorrecto o ambiguo. |
| **Organización** | Muy bien organizada y fácil de seguir. | Bien organizada y se sigue sin dificultad. | Sin organización clara, aunque se sigue sin grandes dificultades. | Desorganizada; imposible entender la estructura. |
| **Contenido** | Calidad de transparencias/material impecable; estilo acertado. | Calidad buena, estilo cuidado y original. | Calidad suficiente, estilo estándar adecuado. | Transparencias poco claras en contenido y estilo. |
| **Respuestas a preguntas** | Responde con precisión y detalle, demostrando dominio del tema. | Contesta correctamente dando algún detalle; entiende el tema y herramientas. | Contesta correctamente de forma general, pero faltan aclaraciones sin entrar en detalles. | Contesta de forma incorrecta o ambigua; no comprende nociones básicas. |
| **Contribuciones** | Todos los miembros han contribuido de forma similar. | La mayoría ha contribuido de forma similar, alguno algo atrás. | Dos o tres llevan el liderazgo y el resto aporta poco. | Parece que solo una persona ha hecho casi todo el trabajo. |

---

## 6. Herramientas vistas en los laboratorios de AVD

- **QuickCheck** (`boletinS1.pdf`, `boletinS2.pdf`, `slidesS1-1.pdf`, `slidesS2.pdf`): *property-based testing* con generación aleatoria de entradas, precondiciones (`==>`), observadores (`classify`, `collect`), generadores (`choose`, `oneof`, `frequency`, `sized`) y validación frente a modelo de referencia (útil conceptualmente si se aplica *property-based testing*, p. ej. con `jqwik` o `fast-check`, o como referencia en el plan).
- **KLEE** (`boletin_klee.pdf`, `slides_klee.pdf`): ejecución simbólica sobre LLVM/Clang para generación automática de casos de prueba (`.ktest`), detección de fallos y medición de cobertura de instrucciones/ramas (`klee-stats`).
- **Selenium WebDriver / Selenium IDE** (`boletin_selenium-3.pdf`, `slides_selenium-4.pdf`): automatización de pruebas funcionales E2E en navegador web con Node.js (`selenium-webdriver`, `By`, `assert`), directamente aplicable al frontend React de Rute.

---

## 7. Pendiente de aportar / definir por el equipo

- [ ] **Portada**: nombres completos de los 5 componentes y grupo de prácticas de cada alumno.
- [ ] **Memoria anterior (Entrega 1)**, si ya existe un borrador previo para ampliarlo.
- [ ] **Documento de requisitos / UTs y pruebas de aceptación de PIN** (referenciado en las diapositivas como pruebas de aceptación de Patricio).
- [ ] **Selección final de aspectos críticos** (1–2 funcionales y 1–2 no funcionales) y si se incluye el requerimiento opcional de ODS.
- [ ] **Ejecución real y evidencias** en el proyecto (por ejemplo, JaCoCo en `backend/` para cobertura real, herramientas de pruebas automáticas, pruebas de prestaciones y cuestionario/pruebas de usabilidad).

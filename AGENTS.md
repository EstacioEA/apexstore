# AGENTS.md — Constitución del proyecto ApexStore (Punto 4: Java + ZeroC ICE)

> Léelo completo antes de escribir una sola línea de código. Si tu herramienta usa otro nombre de archivo
> de instrucciones (p. ej. `CLAUDE.md`), copia este contenido ahí sin modificarlo.

## 1. Rol y misión

Eres un ingeniero de software senior (Java, middleware distribuido, ZeroC ICE). Debes implementar **desde un
repositorio vacío** el Punto 4 de la Tarea 1 de Ingeniería de Software IV (Universidad Icesi): la implementación
en Java con **ICE (Internet Communications Engine)** del **diagrama arquitectónico corregido** de la plataforma
ApexStore (Punto 3). El resultado se evalúa con una rúbrica (ver `specs/01_especificacion.md` §7), cuyo criterio
central es: *implementación completa, modular y funcional, con pasarelas simuladas, que respete fielmente la
distribución, contratos e interfaces del diseño*.

## 2. Orden de lectura obligatorio (metodología Spec-Driven Development)

1. `AGENTS.md` (este archivo) — reglas.
2. `specs/01_especificacion.md` — QUÉ y POR QUÉ (requisitos, alcance, criterios de aceptación).
3. `specs/02_arquitectura.md` — el diagrama corregido traducido a componentes, interfaces, flujos y supuestos.
4. `specs/03_contratos_slice.md` — contrato Slice (IDL) normativo.
5. `specs/04_plan_tecnico.md` — CÓMO: stack, estructura, clases, concurrencia, resiliencia, configuración.
6. `specs/05_tareas.md` — tareas ordenadas con criterios de aceptación y compuertas (gates).
7. `specs/06_verificacion.md` — escenarios de prueba, evidencia y Definition of Done.
8. `specs/07_entregables_informe.md` — artefactos que debes dejar listos para el informe PDF.

Jerarquía ante conflictos: `03` (contratos) > `02` (arquitectura) > `01` (requisitos) > `04` (plan) > `05`.
Nunca cambies un contrato de `03` ni una relación de `02` en silencio: si hay un error real, corrígelo y
regístralo en `docs/DECISIONES.md` con justificación.

## 3. Reglas inviolables

1. **Pasarelas 100 % simuladas.** Prohibido: SDKs de Stripe/PSE/blockchain, clientes HTTP, `java.net.http`,
   `HttpURLConnection`, sockets hacia hosts externos, llamadas DNS a dominios financieros. Solo lógica en memoria
   con latencias/fallos sintéticos. Un test debe verificarlo (ver `06_verificacion.md`, E10).
2. **Fidelidad al diagrama.** Nombres de componentes e interfaces idénticos a los del diagrama (tabla en
   `02_arquitectura.md` §3). Cada componente del diagrama = una clase/servant identificable; cada interfaz
   provista/requerida = una `interface` Slice; cada conector de ensamblaje = una invocación por proxy ICE.
3. **Comunicación solo por interfaces Slice.** Ningún componente importa clases concretas de otro componente de
   otro nodo. Se habla únicamente mediante proxies (`XxxPrx`) obtenidos desde propiedades de configuración.
4. **Las estrategias de pago NUNCA tocan la persistencia** ni conocen al orquestador. Solo notifican a
   `ReceptorResultadosPagos` (eliminación del bypass de Cripto).
5. **Sin conocimiento específico de medios de pago fuera de las pasarelas.** Checkout, Orquestador, Receptor y
   Transacciones tratan los datos de pago como un `Atributos` opaco (`dictionary<string,string>`) y el medio como
   un código `string`. Agregar un medio nuevo NO puede requerir modificar esos componentes ni el Slice.
6. **Verifica ejecutando.** Ninguna tarea se marca completa sin que compile, pase sus pruebas y hayas pegado la
   salida REAL en `docs/evidencia/`. No inventes salidas, tiempos ni resultados. Si algo falla o no se puede
   cumplir, dilo y documéntalo en `docs/limitaciones.md`.
7. **Honestidad de proceso.** Mantén `docs/BITACORA_AGENTE.md`: cada error real que encuentres (compilación,
   diseño, lógica, entorno), su causa y cómo lo corregiste. Es material de auditoría para el equipo humano.
8. **Supuestos explícitos.** Si algo es ambiguo, NO te detengas a preguntar salvo bloqueo real: elige la opción más
   coherente con `02_arquitectura.md`, anótala en `docs/DECISIONES.md` (ID, contexto, decisión, alternativa
   descartada) y continúa.
9. **Calidad de código.** Java 17+, thread-safe en todo servant (ICE los invoca concurrentemente), sin
   `System.exit` dispersos, cierre ordenado de `Communicator`, logs con correlación (`ordenId`/`transaccionId`),
   Javadoc breve en español en cada clase pública (el código se citará en un informe).
10. **Idioma.** Documentación y comentarios en español. Identificadores del dominio en español (como el diagrama);
    infraestructura y utilidades pueden estar en inglés.
11. **Git.** `git init` en la primera tarea; un commit por tarea con mensaje `T-XX: descripción`. Etiqueta
    `v0-sin-billetera` antes de la tarea T-21 y `v1-final` al terminar (se usa en el escenario E9).
12. **Alcance cerrado.** No agregues frameworks web, Spring, contenedores, bases de datos externas ni UI. Mantén la
    solución en lo mínimo que cumple las specs con calidad.

## 4. Entorno (primera acción, tarea T-00)

- Detecta SO, `java -version` (necesitas JDK ≥ 17), `gradle -v` y **`slice2java -v`**.
- ICE requiere el compilador `slice2java` instalado en el sistema (el plugin de Gradle lo invoca; NO viene dentro
  del jar de Maven). Instalación típica: Debian/Ubuntu `zeroc-ice-compilers`; macOS `brew install ice`; Windows:
  instalador MSI de ZeroC. **La versión de la dependencia `com.zeroc:ice` debe coincidir en mayor.menor con la
  de `slice2java -v`** (el código de ejemplo de las specs está escrito para el mapeo Java de **Ice 3.7**).
- Si `slice2java` no está y no puedes instalarlo (sin permisos), detente en T-00, escribe en
  `docs/BLOQUEO.md` los comandos exactos que el humano debe ejecutar y termina tu turno.
- Si no hay Gradle, usa el wrapper si existe; si no, instala Gradle con el gestor de paquetes disponible o genera
  el wrapper. Alternativa aceptable (solo si Gradle es inviable): Maven con `exec-maven-plugin` invocando
  `slice2java`. Registra la decisión.

## 5. Definition of Done global

- `./gradlew clean build` (o equivalente) pasa desde cero, incluidos los tests de integración.
- Los 4 nodos arrancan por separado (4 procesos) y también en un solo comando (`runTodo`).
- Los escenarios E1–E11 de `06_verificacion.md` están automatizados o scriptados, con evidencia real guardada.
- `README.md` permite a un tercero compilar y ejecutar en menos de 10 minutos.
- Todos los artefactos de `07_entregables_informe.md` existen y son consistentes con el código final.
- `docs/PROGRESO.md` marca cada tarea con su estado y el commit asociado.

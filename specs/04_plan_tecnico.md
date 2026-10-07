# 04 — Plan técnico (CÓMO)

## 1. Stack

| Elemento | Decisión |
|---|---|
| Lenguaje | Java 17 (LTS; 21 aceptable) |
| Build | Gradle (wrapper) + plugin `com.zeroc.gradle.ice-builder.slice` (última versión publicada al redactar: 1.5.0; verifica) |
| ICE | `com.zeroc:ice:<versión igual a la de slice2java>` (línea 3.7.x; la 3.7.10 es la última 3.7 conocida, verifica en Maven Central) |
| Persistencia | H2 2.x embebido, `MODE=PostgreSQL`, pool `org.h2.jdbcx.JdbcConnectionPool` (elige una versión estable actual) |
| Pruebas | JUnit 5; ArchUnit (`archunit-junit5`) para el test arquitectónico (E10) |
| Logging | SLF4J + `slf4j-simple` (formato con hora, nivel, hilo). Sin otros frameworks. |

Fragmento orientativo de `build.gradle` (ajústalo hasta que compile):

```groovy
plugins {
    id 'java'
    id 'application'
    id 'com.zeroc.gradle.ice-builder.slice' version '1.5.0'
}
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
repositories { mavenCentral() }
dependencies {
    implementation "com.zeroc:ice:${iceVersion}"      // iceVersion en gradle.properties, = slice2java -v
    implementation "com.h2database:h2:${h2Version}"
    implementation 'org.slf4j:slf4j-api:2.0.13'
    runtimeOnly    'org.slf4j:slf4j-simple:2.0.13'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
}
slice { java { files = fileTree(dir: 'src/main/slice', includes: ['*.ice']) } }
test { useJUnitPlatform(); testLogging { events 'passed','failed','skipped'; showStandardStreams = true } }
```

Tareas Gradle requeridas (`JavaExec`): `runDb`, `runPasarelas`, `runBackend`, `runTodo` (= `arranque.RunAll`),
`runCliente` (acepta `--args="<escenario> [opciones]"`). Cada una carga su archivo de `config/`.
Nota de compatibilidad: ICE 3.7 ofrece dos mapeos Java (el nuevo, paquete `com.zeroc.Ice`, y el "Java Compat",
paquete `Ice`). La documentación del plugin menciona la opción `--compat` para 3.7; este proyecto usa el **mapeo
Java nuevo**. Tras compilar, verifica que las clases generadas importan `com.zeroc.Ice.*`; si el plugin generó el
mapeo compat, ajusta su configuración (`slice { java { args = ... } }`) y registra el cambio en `docs/DECISIONES.md`.

## 2. Estructura del repositorio

```
apexstore-ice/
├─ AGENTS.md  specs/                          (estos documentos, copiados tal cual)
├─ build.gradle  settings.gradle  gradle.properties  gradlew*  .gitignore  README.md
├─ config/  backend.properties  pasarelas.properties  db.properties  cliente.properties  test.properties
├─ scripts/  run-todo.sh|.ps1   run-4-procesos.sh|.ps1   (según SO detectado; al menos el del SO actual)
├─ src/main/slice/apexstore.ice
├─ src/main/java/co/edu/icesi/apexstore/
│   ├─ comun/        Ids, Reloj, Histograma (percentiles thread-safe), ConfigUtil, Correlacion (MDC)
│   ├─ cliente/      ClienteApp (main), CanalWeb, CanalMovil, escenarios/*, ReporteConsola
│   ├─ backend/
│   │   ├─ BackendServer.java (main)
│   │   ├─ checkout/       ServicioCheckoutI, RepositorioOrdenes, CalculadoraMonto
│   │   ├─ orquestador/    OrquestadorPagosI, RegistroEstrategias, EntradaEstrategia, CircuitBreaker, Bulkhead
│   │   ├─ receptor/       ReceptorResultadosPagosI
│   │   ├─ transacciones/  TransaccionesI, SweeperExpiracion
│   │   └─ admin/          AdminBackendI, MetricasBackend
│   ├─ pasarelas/    PasarelasServer (main), PasarelaSimuladaBase, StripeSimulada, PseSimulada, CriptoSimulada,
│   │                NotificadorCallback, AdminPasarelaI, ConfigSimulacion
│   ├─ db/           DbServer (main), PersistirTransaccionI, RepositorioJdbc, EsquemaSql
│   └─ arranque/     RunAll (4 communicators en una JVM), NodoBackend/NodoPasarelas/NodoDb (reutilizados por los main y los tests)
├─ src/test/java/co/edu/icesi/apexstore/   (integración E1–E11, unitarias, ArquitecturaTest)
└─ docs/  PROGRESO.md DECISIONES.md BITACORA_AGENTE.md limitaciones.md trazabilidad.md fragmentos_informe.md evidencia/
```

Regla: `pasarelas` no puede importar `backend` ni `db`; `backend` no puede importar `pasarelas` ni `db`; `db` no
importa `backend` ni `pasarelas`; `cliente` no importa servants (solo proxies Slice generados). Los paquetes
generados por `slice2java` (`ApexStore.*`) son lo único compartido.

## 3. Guía rápida de la API Java de ICE 3.7 (mapeo nuevo)

```java
// Servidor
try (com.zeroc.Ice.Communicator c = com.zeroc.Ice.Util.initialize(args, "config/backend.properties")) {
    com.zeroc.Ice.ObjectAdapter a = c.createObjectAdapter("Backend");     // lee Backend.Endpoints
    a.add(new ServicioCheckoutI(c), com.zeroc.Ice.Util.stringToIdentity("ServicioCheckout"));
    a.activate();
    c.waitForShutdown();                                                   // o hook de apagado limpio
}
// Servant: implementa la interfaz generada; el último parámetro es siempre Current
public class ServicioCheckoutI implements ApexStore.Checkout.GestionarCompraHttp, ApexStore.Checkout.ConfirmarCompra {
    public ApexStore.Comun.ResultadoCompra gestionarCompra(ApexStore.Comun.SolicitudCompra s, com.zeroc.Ice.Current cur)
        throws ApexStore.Comun.PagoException { ... }
}
// Proxy desde propiedades (uncheckedCast: no hace llamada remota al arrancar → arranque desordenado tolerado)
var prx = ApexStore.Pagos.IniciarPagoOrdenPrx.uncheckedCast(c.propertyToProxy("Checkout.Orquestador.Proxy"));
// Timeout por invocación (si no devuelve el tipo, aplica uncheckedCast al resultado)
var rapido = ApexStore.Pagos.EstrategiaPagosPrx.uncheckedCast(prx.ice_invocationTimeout(200));
// Async en cliente: método <op>Async devuelve CompletableFuture<T>
// Structs → clases con campos públicos y constructor completo; sequence<T> → T[]; dictionary → java.util.Map;
// enum → enum Java; excepciones de usuario extienden com.zeroc.Ice.UserException; bool → boolean.
// Excepciones locales relevantes: com.zeroc.Ice.TimeoutException (incl. InvocationTimeoutException),
//   com.zeroc.Ice.ConnectionRefusedException / ConnectFailedException, com.zeroc.Ice.LocalException (base).
```

Si la versión instalada de ICE no es 3.7, consulta la documentación de ZeroC de esa versión y adapta; registra
las diferencias en `docs/DECISIONES.md`.

## 4. Configuración (archivos `.properties` ICE)

`config/backend.properties`:
```
Ice.Default.Host=localhost
Ice.ThreadPool.Server.Size=8
Ice.ThreadPool.Server.SizeMax=64
Backend.Endpoints=tcp -h localhost -p 10000
Checkout.Orquestador.Proxy=OrquestadorPagos:tcp -h localhost -p 10000
Orquestador.Transacciones.Proxy=Transacciones:tcp -h localhost -p 10000
Receptor.Transacciones.Proxy=Transacciones:tcp -h localhost -p 10000
Receptor.Checkout.Proxy=ServicioCheckout:tcp -h localhost -p 10000
Transacciones.Persistencia.Proxy=DbTransacciones:tcp -h localhost -p 10200
Orquestador.Medios=STRIPE,PSE,CRIPTO
Orquestador.Medio.STRIPE.Proxy=EstrategiaStripe:tcp -h localhost -p 10100
Orquestador.Medio.PSE.Proxy=EstrategiaPSE:tcp -h localhost -p 10100
Orquestador.Medio.CRIPTO.Proxy=EstrategiaCripto:tcp -h localhost -p 10100
Orquestador.AckTimeoutMs=200
Orquestador.Breaker.UmbralFallos=5
Orquestador.Breaker.AbiertoMs=3000
Orquestador.Bulkhead.MaxConcurrentes=64
Expiracion.TxPendienteMs=20000
Expiracion.PeriodoMs=2000
```
`config/pasarelas.properties`:
```
Ice.Default.Host=localhost
Ice.ThreadPool.Server.Size=4
Ice.ThreadPool.Server.SizeMax=32
Pasarelas.Endpoints=tcp -h localhost -p 10100
Pasarela.Receptor.Proxy=ReceptorResultadosPagos:tcp -h localhost -p 10000
Pasarela.Callback.MaxIntentos=5
Pasarela.Callback.BackoffInicialMs=100
Pasarelas.Habilitadas=STRIPE,PSE,CRIPTO
Pasarela.STRIPE.Clase=co.edu.icesi.apexstore.pasarelas.StripeSimulada
Pasarela.STRIPE.Identidad=EstrategiaStripe
Pasarela.STRIPE.AckMs=5-40
Pasarela.STRIPE.CallbackMs=200-1500
Pasarela.STRIPE.TasaRechazo=0.05
Pasarela.PSE.Clase=co.edu.icesi.apexstore.pasarelas.PseSimulada
Pasarela.PSE.Identidad=EstrategiaPSE
Pasarela.PSE.AckMs=10-60
Pasarela.PSE.CallbackMs=500-4000
Pasarela.PSE.TasaRechazo=0.08
Pasarela.CRIPTO.Clase=co.edu.icesi.apexstore.pasarelas.CriptoSimulada
Pasarela.CRIPTO.Identidad=EstrategiaCripto
Pasarela.CRIPTO.AckMs=10-50
Pasarela.CRIPTO.CallbackMs=1000-6000
Pasarela.CRIPTO.TasaRechazo=0.03
```
`config/db.properties`: `Db.Endpoints=tcp -h localhost -p 10200`,
`Db.Url=jdbc:h2:file:./data/apexstore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1` (en tests: `jdbc:h2:mem:<único>`).
`config/cliente.properties`: `Cliente.Checkout.Proxy=ServicioCheckout:tcp -h localhost -p 10000`, y proxies a los
`Admin*` para los escenarios. `config/test.properties`: puertos efímeros/libres y tiempos reducidos (expiración 3 s).

Las pasarelas se instancian **por reflexión** desde `Pasarela.<COD>.Clase`: así agregar `BILLETERA` es solo
código nuevo + propiedades (RF-12). Constructor requerido: `(String codigo, ConfigSimulacion cfg, NotificadorCallback notif, ScheduledExecutorService sched)`.

## 5. Diseño detallado por componente

### 5.1 `ServicioCheckoutI` (Facade; provee `GestionarCompraHttp` y `ConfirmarCompra`)
- `RepositorioOrdenes`: `ConcurrentHashMap<String,Orden>` por `ordenId` y mapa `claveIdempotencia → ordenId` con
  `computeIfAbsent` atómico (RF-02). Una `Orden` guarda estado, `transaccionId`, mensaje, monto.
- `gestionarCompra`: validar → monto = Σ(cantidad×precio) → crear/recuperar orden → si es nueva, invocar
  `IniciarPagoOrden` vía proxy → mapear: `TxPendiente→OrdenPendientePago`, `TxRechazada→OrdenRechazada`;
  `ServicioNoDisponible→OrdenFallida` (+mensaje de reintento con otro medio); `MedioPagoNoSoportado` se relanza.
  Si la orden ya existía (idempotencia) devolver su estado actual **sin** llamar al orquestador.
- `confirmarCompra(ResultadoPago)`: mapa Tx→Orden de `02` §8; idempotente; contador `confirmacionesAplicadas`
  (solo cuando el estado realmente cambia). `OrdenDesconocida` si no existe.
- Cero lógica de pagos aquí (SRP).

### 5.2 `OrquestadorPagosI` (Strategy Context; provee `IniciarPagoOrden`)
- Solo orquesta: resolver estrategia → registrar pendiente → despachar con protección → devolver acuse.
  **No** recibe callbacks, **no** accede a BD, **no** conoce datos específicos de pasarela (SRP, DIP).
- `RegistroEstrategias` construye, desde `Orquestador.Medios`, un mapa `código → EntradaEstrategia`
  (`EstrategiaPagosPrx`, `CircuitBreaker`, `Bulkhead`). Los proxies se crean con `uncheckedCast` y
  `ice_invocationTimeout(AckTimeoutMs)`.
- Algoritmo `iniciarPagoOrden`:
  1. validar y medir `t0`.
  2. `entrada = registro.resolver(codigo)` o `MedioPagoNoSoportado`.
  3. `transaccionId = UUID`; `registrarPendiente(Transaccion{TxPendiente})` (escritura previa, `02` §9.1).
  4. `breaker.permite()` falso → `registrarResultado(TxFallida,"CIRCUITO_ABIERTO")`, lanzar `ServicioNoDisponible`.
     `bulkhead.tryAcquire()` falso → igual con `"SATURADA"`.
  5. `iniciarCobro` con timeout. Éxito → `breaker.exito()`. `ServicioNoDisponible` o `ConnectionRefused/ConnectFailed`
     (fallo definitivo) → `breaker.fallo()`, `registrarResultado(TxFallida)`, lanzar `ServicioNoDisponible`.
     `TimeoutException` (ambiguo) → `breaker.fallo()`, **no** cambiar estado, devolver
     `AcuseInicioPago(TxPendiente, "verificación en curso")`.
  6. `finally`: liberar bulkhead; registrar latencia en `MetricasBackend` (siempre, también en fallos).
  7. Acuse no aceptado → `registrarResultado(TxRechazada, motivo)` y devolver `TxRechazada`.
- `CircuitBreaker`: estados CERRADO/ABIERTO/SEMIABIERTO; abre tras `UmbralFallos` fallos consecutivos; tras
  `AbiertoMs` permite **una** llamada de prueba (SEMIABIERTO); éxito → CERRADO, fallo → ABIERTO. Thread-safe
  (`AtomicReference`/`synchronized` mínimo). Inyectar `Reloj` para poder testear sin esperas reales.
- `Bulkhead`: `Semaphore` por medio.

### 5.3 `TransaccionesI` (Repository; provee `RegistrarTransaccion`, requiere `PersistirTransaccion`)
- Delegación fina al proxy de BD. `registrarResultado(r)`:
  `ok = bd.transicionar(txId, TxPendiente, r.estado, r.ref, r.motivo)`; si `ok` → `true`;
  si no: `actual = bd.obtener(txId)`; si `actual.estado == r.estado` → `bd.auditar(txId,"DUPLICADO_IGNORADO",...)`
  sino → `bd.auditar(txId,"CONFLICTO_TARDIO",...)` + incrementar contador de conflictos; devolver `false`.
  `registrarResultado` con tx inexistente → `TransaccionDesconocida`.
- Traducir `Ice.LocalException` hacia la BD en `ServicioNoDisponible`/`PagoException` coherente (no filtrar
  excepciones locales al exterior).

### 5.4 `ReceptorResultadosPagosI` (provee `NotificarResultadoPago`, requiere `ConfirmarCompra` y `RegistrarTransaccion`)
- Valida campos; `cambio = transacciones.registrarResultado(r)`; si `cambio` → `checkout.confirmarCompra(r)` con
  hasta 3 reintentos locales (backoff corto; es idempotente). Si no hubo cambio → retornar sin error
  (la pasarela debe considerar el callback entregado).
- Responde rápido; nunca bloquea esperando nada externo largo.

### 5.5 `SweeperExpiracion` (parte del nodo Backend; usa `RegistrarTransaccion`)
- `ScheduledExecutorService` cada `Expiracion.PeriodoMs`: `expirarPendientes(ahora - TxPendienteMs)`; por cada
  expirada invoca `ConfirmarCompra` con un `ResultadoPago{TxExpirada}`. Cierre ordenado al apagar el nodo.

### 5.6 Pasarelas simuladas (`PasarelaSimuladaBase`, Template Method)
- `iniciarCobro(solicitud)`: (1) según `modo`: `CAIDA` → lanzar `ServicioNoDisponible`; `LENTA` → dormir
  `latenciaAckMax` **solo en este modo** (provoca el timeout del orquestador; documentar que es la simulación del
  banco colgado); (2) `validar(solicitud)` abstracto específico por pasarela (atributos y moneda, `02`/`01` §6) →
  `SolicitudInvalida`; (3) latencia de acuse aleatoria; (4) decide aprobación (`tasaRechazo`); (5) programa en el
  `ScheduledExecutorService` el callback tras la latencia de callback (`CONGESTION` multiplica/usa 5–15 s;
  `SIN_CALLBACK` no programa; `CALLBACK_DUPLICADO` programa dos envíos); (6) devuelve `AcuseCobro(aceptado, ref)`.
  Idempotencia propia: mismo `transaccionId` repetido no genera un segundo cobro (mapa `transaccionId→resultado`).
- `NotificadorCallback`: llama `NotificarResultadoPagoPrx.notificarResultadoPago` con reintentos (`02` §9.4).
  Usar la API async del proxy o el executor; jamás un hilo de ICE bloqueado.
- Subclases: `StripeSimulada`, `PseSimulada`, `CriptoSimulada` solo aportan `validar`, defaults y referencia
  (`ch_…`, `pse_…`, `0x…`). **Prohibido** que cualquiera referencie persistencia o al orquestador.
- `AdminPasarelaI` por pasarela (identidad `Admin<COD>`, p. ej. `AdminPSE`).

### 5.7 Nodo BD (`PersistirTransaccionI` + `RepositorioJdbc`)
Esquema (`EsquemaSql`):
```sql
CREATE TABLE IF NOT EXISTS transacciones (
  transaccion_id VARCHAR(64) PRIMARY KEY,
  orden_id VARCHAR(64) NOT NULL UNIQUE,
  codigo_medio_pago VARCHAR(32) NOT NULL,
  monto_minor BIGINT NOT NULL CHECK (monto_minor > 0),
  moneda VARCHAR(8) NOT NULL,
  estado VARCHAR(16) NOT NULL,
  referencia_externa VARCHAR(128),
  motivo VARCHAR(256),
  creada_ms BIGINT NOT NULL,
  actualizada_ms BIGINT NOT NULL);
CREATE TABLE IF NOT EXISTS auditoria (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaccion_id VARCHAR(64) NOT NULL,
  evento VARCHAR(32) NOT NULL,
  estado_anterior VARCHAR(16), estado_nuevo VARCHAR(16),
  detalle VARCHAR(512), marca_tiempo_ms BIGINT NOT NULL);
```
- `insertar`: una transacción SQL (`autoCommit=false`): INSERT en `transacciones` + INSERT auditoría `CREADA`;
  violación de unicidad → `TransaccionDuplicada`.
- `transicionar`: una transacción SQL: `UPDATE ... SET estado=?, ... WHERE transaccion_id=? AND estado=?`; si
  `rowCount==1` → INSERT auditoría `TRANSICION` y `commit`; si 0 → `rollback` y `false` (o
  `TransaccionDesconocida` si no existe). Nunca estado intermedio visible.
- `expirarPendientes`: selecciona pendientes antiguas y aplica `transicionar` a cada una (auditoría `EXPIRADA`).
- Toda operación: `try-with-resources`, `rollback` en error, aislamiento `READ_COMMITTED` o superior.
- `DbServer` aplica el esquema al iniciar.

### 5.8 Métricas y observabilidad
- `Histograma` thread-safe (reservoir con tope, p. ej. 100 000 muestras; percentiles por ordenación).
- Logs `INFO` en hitos de negocio, `WARN` en fallos de pasarela/conflictos, `DEBUG` en detalle. Formato de mensaje:
  `[nodo/componente][ord=…|tx=…] mensaje`. La correlación viaja entre nodos con el *request context* de ICE
  (clave `corr`), opcional pero deseable.

## 6. Concurrencia (puntos a cuidar)
- Servants sin estado mutable compartido sin sincronización; usar estructuras `Concurrent*` y `Atomic*`.
- Hilos de ICE del backend: no hacer `sleep` ni esperas largas; las llamadas salientes tienen timeout.
- Los `ScheduledExecutorService` son `daemon`/se apagan en el cierre del nodo.
- El cliente de carga usa un `ExecutorService` de tamaño configurable y un `CountDownLatch`; las latencias se
  miden con `System.nanoTime`.

## 7. Cierre ordenado
Cada nodo: hook de apagado → detener sweepers/executors → `communicator.shutdown()` → `waitForShutdown()` →
cerrar pool JDBC. `RunAll` debe poder iniciar y detener los 4 nodos repetidamente (los tests lo usan).

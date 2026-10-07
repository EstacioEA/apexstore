# Especificación del Proyecto: Plataforma de Comercio Electrónico "ApexStore"

---

## 1. Contexto del Problema y Escenario de Negocio

La compañía internacional **ApexStore Technologies** opera una plataforma de comercio electrónico para la venta de tecnología, moda y consumo masivo en cinco países. Durante eventos masivos de alta concurrencia (tales como CyberMonday, Black Friday y ventas relámpago con inventario limitado), la plataforma experimenta picos imprevistos de tráfico que **multiplican por 100 el volumen ordinario de solicitudes**, alcanzando **tasas superiores a 8,000 peticiones por segundo** en el flujo de finalización de compra (*checkout*).

Para garantizar la viabilidad operativa y la solidez técnica, se diseñó una **arquitectura distribuida particionada en 4 nodos de ejecución físicos**, conectando aplicaciones cliente, el núcleo transaccional, un subsistema de pasarelas de pago heterogéneas con procesamiento asincrónico por *callbacks* y un nodo de persistencia relacional dedicado.

---

## 2. Requerimientos Arquitectónicamente Significativos (RAS)

Las decisiones de diseño de ApexStore se gobiernan bajo cuatro Requerimientos Arquitectónicamente Significativos primarios:

* **RAS-01 (Disponibilidad y Resiliencia en Pagos):** El flujo de compra debe operar de forma asincrónica desacoplada; lentitudes o caídas en pasarelas bancarias externas no deben congelar la atención a los clientes (**disponibilidad objetivo \\(\ge 99.9\%\\)**).
* **RAS-02 (Rendimiento y Latencia de Red Interna):** La orquestación interna de una transacción entre el backend core y los nodos especializados no debe superar los **250 ms en el percentil 95 (P95)** sobre una red 10GbE LAN.
* **RAS-03 (Consistencia e Integridad Transaccional ACID):** Tolerancia cero a "pagos huérfanos", dobles cobros o inconsistencias contables; cada transacción debe persistirse de forma atómica y auditada.
* **RAS-04 (Extensibilidad y Mantenibilidad del Sistema de Pagos):** El diseño debe permitir la adición de nuevos medios de pago (ej. billeteras digitales) con mínimo impacto en el código existente y garantizando el ocultamiento de información.

---

## 3. Arquitectura de Referencia (Diagrama de Despliegue y Componentes UML 2.5)

El sistema se estructura en **cuatro (4) nodos de ejecución físicos** interconectados mediante protocolos específicos:

1. **Nodo 1: Dispositivos Clientes (Front-End Edge):**
   * **Componentes:** `WebApp` (React/TypeScript) y `MobileApp` (Flutter iOS/Android).
   * **Conectividad:** Invocan la interfaz provista `gestionarComprasHttp` mediante protocolo seguro `HTTPS / Internet`.
2. **Nodo 2: Servidor E-Commerce (Backend Core):**
   * **Componentes:** `ServicioCheckout` y `ProcesadorPagosContexto` [Strategy Context].
   * **Responsabilidad:** Recibe solicitudes del cliente, expone la fachada de compra y orquesta la ejecución del patrón Strategy de pagos.
3. **Nodo 3: Servidor de Pasarelas y Estrategias de Pago:**
   * **Componentes:** `EstrategiaStripe` (Tarjeta Crédito), `EstrategiaPSE` (Débito Bancario) y `EstrategiaCripto` (Blockchain Wallet).
   * **Conectividad:** Se comunica con el Nodo 2 a través de una red privada `10GbE LAN`.
4. **Nodo 4: Servidor Base de Datos Transaccional:**
   * **Componente:** `DB_PostgreSQL_Transacciones`.
   * **Responsabilidad:** Almacenamiento relacional con garantías ACID para la persistencia de compras e historial financiero.

---

## 4. Análisis Crítico de la Arquitectura Inicial: 5 Puntos Clave

### 4.1 Violación del Principio de Responsabilidad Única (SRP) — Mal Uso
* **Evidencia:** El componente `ProcesadorPagosContexto` (Nodo 2) asume la orquestación del pago, la gestión de callbacks de confirmación y la invocación directa a la interfaz `persistirTransaccionPostgres` del Nodo 4.
* **Impacto Negativo:** Degradación de la mantenibilidad y modularidad. Cambios en el modelo de persistencia obligan a re-desplegar todo el orquestador central, arriesgando la disponibilidad (RAS-01) durante picos de tráfico.

### 4.2 Bypassing y Inconsistencia Transaccional (Pagos Huérfanos) — Mal Uso
* **Evidencia:** El componente `EstrategiaCripto` invoca directamente la interfaz `persistirTransaccionExitosa` en la base de datos sin notificar la finalización al `ProcesadorPagosContexto`.
* **Impacto Negativo:** Rompe la simetría del diseño y el estado de la orden en el backend permanece como "Pendiente" o "Incompleta", generando **pagos huérfanos** (violación directa de RAS-03) e impidiendo confirmar la compra al usuario o despachar el inventario.

### 4.3 Violación de DIP y LSP en el Patrón Strategy — Mal Uso
* **Evidencia:** El Nodo 3 expone interfaces provistas heterogéneas y rígidas para cada pasarela: `autorizarCargoStripe`, `debitarTransferenciaPSE` y `generarCobroCriptoBtc`.
* **Impacto Negativo:** Violación del Inversión de Dependencias (DIP) y Sustitución de Liskov (LSP). El orquestador conoce firmas concretas, invalidando el requerimiento de extensibilidad (RAS-04); agregar una nueva pasarela exige modificar y re-compilar el backend core.

### 4.4 Aplicación del Patrón Facade y Ocultamiento de Información — Buen Uso
* **Evidencia:** Exposición de la interfaz provista única `gestionarComprasHttp` desde `ServicioCheckout` (Nodo 2) hacia las aplicaciones clientes (Nodo 1).
* **Impacto Positivo:** Desacopla la capa Edge de la topología interna del backend. Oculta la complejidad de pasarelas y persistencia, y refuerza la seguridad PCI-DSS al impedir accesos directos desde la red pública hacia subsistemas internos.

### 4.5 Modularización Arquitectónica y Zonificación Física — Buen Uso
* **Evidencia:** Particionamiento en 4 nodos de ejecución físicos aislados mediante límites de red claros (`HTTPS / Internet` y `10GbE LAN`).
* **Impacto Positivo:** Cumple de manera simultánea con el rendimiento de red interna (\\(\le 250\text{ ms}\\) P95, RAS-02) y la resiliencia ante picos de 8,000 req/s (RAS-01). Aísla la latencia y fallas de pasarelas bancarias externas en el Nodo 3, evitando el colapso del núcleo de e-commerce en el Nodo 2.

---

## 5. Dimensiones Concurrentes de Diseño (SWEBOK Cap. 3)

1. **Interacción y Presentación:** Invocación desacoplada mediante un API Gateway / Facade (`gestionarComprasHttp`), protegiendo el backend.
2. **Concurrencia y Rendimiento:** Despacho síncrono de la intención de compra con procesamiento asincrónico por webhooks/callbacks, evitando el bloqueo del *thread pool* ante demoras de 15+ segundos en pasarelas bancarias.
3. **Tolerancia a Fallos y Manejo de Errores:** Resiliencia mediante desacoplamiento; retención de estado para reintentos sin interrumpir la experiencia global del cliente.
4. **Control y Gestión de Eventos:** Tratamiento de eventos asincrónicos para procesar notificaciones y confirmar transacciones.
5. **Persistencia de Datos:** Modelo de base de datos relacional PostgreSQL enfocado en garantías **ACID** para prevenir inconsistencias financieras, en lugar de modelos BASE de consistencia eventual.
6. **Distribución de Componentes:** Topología física en 4 nodos que cumple con los estándares de seguridad de datos de tarjetas de pago (PCI-DSS) y zonificación de red.

---

## 6. Propuesta de Rediseño y Refactorización Arquitectónica

Para subsanar los malos usos identificados, se deben aplicar las siguientes modificaciones estructurales en UML 2.5:

1. **Estandarización de la Interfaz del Patrón Strategy:**
   * Crear un contrato neutro e inmutable (ej. `IPagoStrategy`) expuesto por todas las estrategias en el Nodo 3, declarando un método unificado como `procesarPago(SolicitudPago)`.
2. **Eliminación de Bypasses e Inconsistencias:**
   * Eliminar la conexión directa entre `EstrategiaCripto` y la Base de Datos.
   * Todas las estrategias notifican la confirmación (*callback*) al `ProcesadorPagosContexto`, manteniendo el control de la máquina de estados de la orden en un único orquestador.
3. **Desacoplamiento de la Persistencia:**
   * Mover la responsabilidad de almacenamiento del `ProcesadorPagosContexto` a un componente dedicado de repositorio/persistencia (`RepositorioTransacciones`), garantizando la adherencia al SRP.

---

## 7. Mapeo a Código Java con Middleware ZeroC ICE 3.7

La arquitectura refactorizada se implementa en Java utilizando el framework de objetos distribuidos **ZeroC ICE 3.7**:

* **Contratos IDL (Slice):** Definición de las interfaces distribuidas y estructuras de datos en archivos `.ice` para garantizar interoperabilidad y generar *Stubs/Skeletons*.
* **Simulación de Pasarelas:** Las estrategias de pago (Stripe, PSE, Cripto) se implementan como *Servants* distribuidos en Java que simulan la interacción bancaria de forma asincrónica.
* **Orquestación Distribución:** El cliente y los servidores se comunican transparentemente a través de *Proxies* ICE sobre la red, respetando la topología de nodos del diseño.

---

## 8. Lineamientos del Taller e Integridad Académica

* **Modalidad:** Trabajo en equipos de máximo 3 integrantes.
* **Ponderación:** 12.0% de la nota definitiva del curso (Subcomponente Tareas).
* **Política de Inteligencia Artificial Generativa (IAG - Nivel 3):** Se permite la colaboración asistida mediante IAG siempre que se adjunte obligatoriamente la **Bitácora de Uso de IAG**, demostrando evaluación crítica, corrección humana y personalización sobre el contexto de ApexStore.
```

---

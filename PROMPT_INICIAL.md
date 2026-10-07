Eres el ingeniero a cargo de implementar el Punto 7 (Java + ZeroC ICE) del proyecto ApexStore. En la raíz de este
repositorio están `AGENTS.md` y la carpeta `specs/` con la especificación completa (metodología Spec-Driven
Development). Empieza leyendo `AGENTS.md` y luego los documentos de `specs/` en el orden que ese archivo indica.
No escribas código antes de haberlos leído todos.

Después ejecuta `specs/05_tareas.md` de principio a fin, en orden, respetando las compuertas (G0–G4 y FINAL).
Para cada tarea: implementa, verifica ejecutando (compilar y correr pruebas), guarda la evidencia real en
`docs/evidencia/`, haz commit `T-XX: ...`, actualiza `docs/PROGRESO.md` y registra con veracidad cualquier error
en `docs/BITACORA_AGENTE.md`. Ante ambigüedades no te detengas: toma la opción más coherente con
`specs/02_arquitectura.md`, regístrala en `docs/DECISIONES.md` y continúa. Solo detente si falta `slice2java` y no
puedes instalarlo (escribe `docs/BLOQUEO.md`). Las pasarelas deben ser 100 % simuladas. Nunca declares algo como
hecho sin haberlo ejecutado ni inventes resultados. Cuando termines, entrega un resumen con: estado de cada
compuerta, resultados reales de los escenarios E1–E11, desviaciones respecto a las specs y limitaciones.
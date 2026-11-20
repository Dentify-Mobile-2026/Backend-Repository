# Planes de tratamiento e historial clínico

Todos los endpoints requieren JWT y filtran por el usuario autenticado. Un recurso de otro odontólogo devuelve 404.

## Presupuestos

- `GET /api/v1/patients/{patientId}/treatment-plans`: lista del paciente, con ítems, total, avance y auditoría.
- `POST /api/v1/patients/{patientId}/treatment-plans`: crea un borrador de 2 a 100 ítems de tratamientos activos del catálogo del usuario.
- `PUT /api/v1/patients/{patientId}/treatment-plans/{planId}/status`: recibe `{"status":"APPROVED"}` o `{"status":"CANCELLED"}`.
- `PUT /api/v1/treatment-plans/{planId}/items/{itemId}/status`: recibe `{"status":"PENDING"}`, `IN_PROGRESS` o `DONE`.

Ejemplo de creación:

```json
{
  "title": "Restauraciones anteriores",
  "notes": "Presupuesto acordado con el paciente",
  "items": [
    {"treatmentId": 4, "toothNumber": "11", "agreedPrice": "120.50"},
    {"treatmentId": 4, "toothNumber": "21", "agreedPrice": "90.10"}
  ]
}
```

El total es `210.60`. Los precios usan `BigDecimal` y columnas `DECIMAL(19,2)`. Se aceptan precios cero; se rechazan negativos y fracciones menores que un céntimo. La pieza es opcional y usa numeración FDI permanente (11–48) o temporal (51–85), validando cada cuadrante.

El nombre y precio acordado quedan como copia del catálogo al crear el ítem. Primero se aprueba el borrador. El avance se calcula por ítems realizados: al iniciar o realizar uno pasa a `IN_PROGRESS`, al realizar todos pasa a `COMPLETED`. Corregir un ítem realizado recalcula el estado. Los planes cancelados conservan su avance y no admiten cambios. Los planes no permiten forzar la finalización sin realizar todos sus ítems. Las actualizaciones se serializan mediante bloqueo del plan.

Las relaciones persistidas protegen los tratamientos y pacientes vinculados a planes frente al borrado físico. Para retirar un tratamiento del catálogo se puede marcar inactivo.

## Evolución automática de citas

`PUT` y `PATCH /api/v1/appointments/{id}/status` aceptan:

```json
{"status":"COMPLETED","clinicalNotes":"Restauración realizada, control en una semana.","recordInMedicalHistory":true}
```

El indicador es opcional y por defecto es `false`, conservando el comportamiento de clientes anteriores. Con el indicador activo se exige `COMPLETED` y un tratamiento vinculado. Se guarda tratamiento, fecha de la cita y las notas completas (hasta 500 caracteres); la medicación figura como «No registrada», sin inferir una prescripción.

El historial expone también `appointmentDate` y `sourceAppointmentId`, nulos para entradas manuales anteriores. La entrada y el cambio de estado se guardan en la misma transacción. El bloqueo de la cita y la unicidad de `sourceAppointmentId` impiden duplicados al reintentar, reabrir o volver a completar una cita. Borrar la cita no elimina la entrada clínica.

## Persistencia y validación

La configuración existente `spring.jpa.hibernate.ddl-auto=update` crea `treatment_plan` y `treatment_plan_item`, añade los campos de trazabilidad a `medical_history` y amplía `record` a 500 caracteres. En entornos con migraciones gestionadas, aplicar cambios equivalentes antes de desplegar.

Ejecutar `./mvnw.cmd test` con Java 17. Las pruebas del módulo cubren decimales, transiciones, pertenencia, validación REST, registro automático, reintentos y persistencia aislada H2. Las pruebas MySQL existentes requieren `DB_URL`; H2 no sustituye la validación de despliegue en MySQL.

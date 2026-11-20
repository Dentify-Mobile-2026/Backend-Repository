# 📋 Guía de Ejecución y Plan de Evolución · Dentify Backend

Esta guía contiene:
1. Los pasos exactos para configurar y levantar la API de Dentify localmente.
2. El **Plan de Evolución por Fases y Justificación del Modelo de Datos**, listo para actualizar la documentación técnica y funcional del proyecto.

---

## Parte I: Ejecución Local del Proyecto

### 1. Requisitos
- **Java 17 JDK** (`java -version`)
- **MySQL 8.0** activo en `localhost:3306`

### 2. Preparar Base de Datos MySQL
Abre tu consola o cliente de MySQL (MySQL Workbench, MySQL Shell o CLI) y ejecuta:

```sql
CREATE DATABASE IF NOT EXISTS dentify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
*(Nota: Las tablas y nuevas columnas se crean y actualizan automáticamente gracias a `spring.jpa.hibernate.ddl-auto=update`).*

**Métodos de pago:** se crean solos al arrancar (`PaymentMethodDataSeeder`). Solo se insertan los que falten (Efectivo, Tarjeta de Credito, Tarjeta de Debito, Transferencia), así que ya no hace falta el INSERT manual. Los ids no son fijos: el front los obtiene con `GET /api/v1/payment-methods`.

**Bases existentes (creadas antes del Sprint 1 del módulo financiero):** en ellas, `invoice.appointment_id` tiene `ON DELETE CASCADE`, de modo que borrar una cita o un paciente borraba sus comprobantes. Hay que ejecutar **una vez, a mano**, la migración controlada:
```bash
mysql -u root -p dentify < src/main/resources/db/migration-manual/V2026_11_20__invoice_fk_restrict.sql
```
- Es idempotente: si ya está aplicada, no hace cambios.
- No modifica datos: deja la FK en `ON DELETE RESTRICT`.
- Necesita privilegio `ALTER`.
- El backend no ejecuta DDL por su cuenta. Al arrancar, `InvoiceSchemaVerifier` solo comprueba la regla y registra un `ERROR` mientras falte la migración.

Verificación antes y después de aplicarla:
```sql
SELECT rc.CONSTRAINT_NAME, rc.DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS rc
 WHERE rc.CONSTRAINT_SCHEMA = DATABASE() AND rc.TABLE_NAME = 'invoice' AND rc.REFERENCED_TABLE_NAME = 'appointment';
-- esperado después: una sola fila con RESTRICT (o NO ACTION en bases nuevas)
SELECT id, amount FROM invoice WHERE ROUND(amount, 2) <> amount; -- históricos con más de 2 decimales
```

**Comprobantes:**
- Crear un comprobante **no cambia el estado clínico de la cita**. La cita se marca como "Atendida" solo desde el flujo de estados de la agenda.
- Los montos se validan como decimales exactos: mayores que cero, con máximo 2 decimales y hasta 99 999 999.99. Se guardan en `invoice.total_amount DECIMAL(10,2)`.
- La columna `amount` (double) se mantiene por compatibilidad.
- **Fechas:** `invoice.created_at` es la fecha de auditoría y se interpreta en la zona horaria de la JVM que la escribió (tanto en registros nuevos como históricos). Los informes del dashboard se calculan en `America/Lima`.

**Pagos parciales (abonos):**
- **Abono inicial.** `POST /api/v1/invoices` admite un `initialPayment` opcional:
  - si no se envía, se cobra el total;
  - con `0`, el comprobante queda PENDING y no se exige método de pago;
  - si es mayor que el total, se responde 400.
- **Registrar un abono.** `POST /api/v1/invoices/{invoiceId}/payments` recibe `{amount, paymentMethodId, notes?, requestId}`.
  - Respuestas: 201 cuando se crea; 200 cuando es una repetición idéntica (mismo `requestId` y mismos datos); 409 si el abono supera el saldo o si el `requestId` se reutilizó con otros datos.
  - Los abonos concurrentes sobre un mismo comprobante se serializan con `SELECT … FOR UPDATE`.
- **Consultas.**
  - `GET /api/v1/invoices/{invoiceId}/payments` devuelve los abonos con `paidAt` en UTC.
  - `GET /api/v1/invoices?status=PENDING|PARTIAL|PAID` filtra por estado.
- **Datos guardados.**
  - Los abonos van en `invoice_payment`: monto `DECIMAL(10,2)`, `paid_at` en UTC y `request_id` único.
  - Son inmutables: su FK hacia `invoice` no tiene cascada.
  - El método de pago de cada cobro se guarda en `invoice_payment.payment_method_id`. Los comprobantes nuevos dejan `invoice.paymentmethod_id` en NULL: esa columna solo la tienen los históricos y la aplicación nunca la escribe.
- **Comprobantes anteriores a los abonos** (`status` NULL).
  - Se tratan como cobrados en su totalidad en su fecha de emisión, igual que antes. No se les crean abonos ni se modifican sus filas.
  - La API los marca con `legacy: true` y rechaza abonos nuevos sobre ellos.
- **Dashboard** (`/api/v1/dashboard/analytics`, en `America/Lima`; los campos JSON no cambian):
  - `totalCollected`: dinero cobrado en el mes, según la fecha real de cada abono.
  - `pendingBalance`: cuentas por cobrar al corte (ahora, o al cierre de un mes pasado), de todos los meses.
  - `collectionRate`: lo cobrado de lo generado en el mes (comprobantes emitidos en el mes más citas atendidas sin comprobante).
  - `recentPayments`: los últimos abonos individuales.

Verificación de comprobantes y abonos:
```sql
SELECT COUNT(*) FROM invoice WHERE status IS NULL;                                       -- históricos (constante)
SELECT i.id FROM invoice i JOIN invoice_payment p ON p.invoice_id = i.id WHERE i.status IS NULL; -- debe ser 0
SELECT i.id, i.total_amount, SUM(p.amount) AS pagado FROM invoice i JOIN invoice_payment p ON p.invoice_id = i.id
 GROUP BY i.id, i.total_amount HAVING SUM(p.amount) > i.total_amount;                     -- sobrepagos: debe ser 0
SELECT COUNT(*) FROM invoice WHERE status IS NOT NULL AND paymentmethod_id IS NOT NULL;  -- método duplicado en nuevos: debe ser 0
```

### 3. Configuración (`application.properties`)
La aplicación ya cuenta con valores predeterminados locales:
- **Usuario:** `root`
- **Contraseña:** `root`
- **Puerto:** `8080`
- **DB:** `dentify`

Si tu MySQL utiliza otra contraseña:
```powershell
# En PowerShell:
$env:DB_USERNAME = "tu_usuario"
$env:DB_PASSWORD = "tu_password"
```

### 4. Ejecución del Servidor
```cmd
cd Backend
mvnw.cmd spring-boot:run
```

### 5. Verificación y Credenciales de Prueba
- **Health Check:** [http://localhost:8080/health](http://localhost:8080/health) `-> {"status":"UP"}`
- **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Usuario de Prueba:** `dentist1` | **Contraseña:** `password123`

---

## Parte II: Justificación Técnica y Plan de Mejora Integral por Fases

### 🔍 ¿Por qué fue necesario evolucionar el documento y el modelo inicial?

En su versión base, el sistema contaba con entidades mínimas que no cubrían el flujo clínico ni operativo real de un consultorio odontológico:
1. **Ficha de paciente incompleta para el sector salud:** El modelo original de `Patient` solo almacenaba `dni`, `firstName`, `lastName`, `email`, `birthday` y `homeAddress`. Carecía de **teléfono/WhatsApp** (indispensable para confirmación de citas), **contacto de emergencia** y, sobre todo, **alertas médicas críticas** (alergias a anestésicos locales como lidocaína, antibióticos como penicilina, látex o AINEs, así como condiciones sistémicas como hipertensión, diabetes o coagulopatías).
2. **Ausencia de un Catálogo de Tratamientos Dentales (`Treatment`):** Las citas solo tenían un campo de texto libre (`reason`), impidiendo estandarizar el arancel odontológico, los precios base, las especialidades clínicas y los tiempos estimados por procedimiento.
3. **Citas sin ciclo de vida clínico ni evolución:** El agregado `Appointment` solo manejaba un booleano `completed` (`true`/`false`), sin distinguir si una cita estaba *Agendada*, *Confirmada*, *En Sillón/Atención*, *Atendida*, *Cancelada* o *No Asistió*, ni permitir registrar las **notas de evolución clínica** de cada sesión.

---

### 🗺️ Plan de Evolución en 3 Fases

| Fase | Estado | Alcance en Backend y Frontend | Impacto en el Proyecto |
|---|---|---|---|
| **Fase 1: Núcleo Clínico Odontológico** | ✅ **Implementada (Rama `dev`)** | **1. Paciente Enriquecido:** Value Objects `PhoneNumber`, `MedicalAlerts` (`allergies`, `systemicConditions`) y `EmergencyContact`.<br>**2. Catálogo de Tratamientos (`Treatment`):** Nuevo agregado DDD con categoría odontológica (`TreatmentCategory`), precio base y duración estimada en minutos (`/api/v1/treatments`).<br>**3. Ciclo de Vida de Citas (`AppointmentStatus`):** Estados `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, notas clínicas (`clinicalNotes`), vinculación opcional con `Treatment` y liberación automática de horario en citas canceladas. | Transforma un CRUD básico en un sistema clínico dental realista con trazabilidad médica y arancelaria. |
| **Fase 2: Presupuestos y Cobranza Parcial** | 📋 Planificada | **1. Planes de Tratamiento / Presupuestos (`TreatmentPlan`):** Agrupar varios tratamientos diagnosticados para un paciente con estado por etapa.<br>**2. Pagos Parciales y Saldos:** Permitir abonos/cuotas por sesión en tratamientos largos (ortodoncia, implantes, endodoncia) calculando el saldo pendiente. | Cubre el flujo financiero real de un consultorio dental. |
| **Fase 3: Agenda Visual y Analítica** | 📋 Planificada | **1. Calendario Diario/Semanal:** Vista de agenda por bloques horarios.<br>**2. Integración Directa con WhatsApp:** Recordatorio de citas en un clic.<br>**3. Dashboard Analítico:** Ingresos mensuales por especialidad y tasa de asistencia. | Experiencia de usuario de nivel SaaS comercial. |

---

### 📐 Detalle de Cambios para Actualizar el Documento del Proyecto (Fase 1)

#### 1. Actualización del Agregado `Patient` (`patientattention`)
- **Nuevos Value Objects:**
  - `PhoneNumber`: `phone` (VARCHAR 30)
  - `MedicalAlerts`: `allergies` (VARCHAR 500), `systemic_conditions` (VARCHAR 500)
  - `EmergencyContact`: `emergency_contact_name` (VARCHAR 120), `emergency_contact_phone` (VARCHAR 30)
- **JSON de `POST /api/v1/patients` y `PUT /api/v1/patients/{id}`:**
  ```json
  {
    "dni": "74589632",
    "firstName": "Lucia Fernanda",
    "lastName": "Vargas Mendoza",
    "email": "lucia.vargas@gmail.com",
    "homeAddress": "Calle Los Pinos 450, Miraflores",
    "birthday": "1998-03-14",
    "phone": "945123789",
    "allergies": "Penicilina, AINEs / Ibuprofeno",
    "systemicConditions": "Asma leve",
    "emergencyContactName": "Roberto Vargas (Padre)",
    "emergencyContactPhone": "945111222"
  }
  ```

#### 2. Nuevo Agregado `Treatment` y Enum `TreatmentCategory` (`patientattention`)
- **Tabla generada:** `treatment`
- **Atributos:** `id`, `name`, `description`, `category`, `price`, `estimated_duration_minutes`, `is_active`, `user_id`, `created_at`, `updated_at`.
- **Valores de `TreatmentCategory`:**
  - `PREVENTION` (Prevención y Profilaxis)
  - `RESTORATIVE` (Operatoria y Restauración)
  - `ENDODONTICS` (Endodoncia)
  - `PERIODONTICS` (Periodoncia)
  - `ORTHODONTICS` (Ortodoncia)
  - `SURGERY` (Cirugía Oral)
  - `PROSTHODONTICS` (Prótesis y Rehabilitación)
  - `AESTHETICS` (Estética Dental)
- **Nuevos Endpoints REST (`/api/v1/treatments`):**
  - `GET /api/v1/treatments` - Listar catálogo del odontólogo autenticado.
  - `GET /api/v1/treatments/{id}` - Obtener detalle de un tratamiento.
  - `POST /api/v1/treatments` - Crear tratamiento en el catálogo.
  - `PUT /api/v1/treatments/{id}` - Actualizar tratamiento.
  - `DELETE /api/v1/treatments/{id}` - Eliminar tratamiento.

#### 3. Evolución del Agregado `Appointment` y Enum `AppointmentStatus`
- **Nuevos campos en tabla `appointment`:**
  - `status` (`ENUM`: `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`).
  - `clinical_notes` (VARCHAR 500): Registro de evolución clínica de la sesión.
  - `treatment_id` (BIGINT, FK opcional a `treatment`).
- **Regla de negocio mejorada:** El validador de solapamiento horario (`assertNoScheduleConflict`) ignora automáticamente las citas con estado `CANCELLED` o `NO_SHOW`, permitiendo reasignar ese turno a otro paciente.
- **Nuevo Endpoint REST:**
  - `PUT /api/v1/appointments/{appointmentId}/status` (también soporta `PATCH`)
  ```json
  {
    "status": "CONFIRMED",
    "clinicalNotes": "Paciente confirmó asistencia. Recordar alergia a AINEs."
  }
  ```

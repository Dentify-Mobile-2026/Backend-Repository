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
USE dentify;

-- Métodos de pago base para el módulo de facturación:
INSERT INTO payment_method (name, description, created_at, updated_at) VALUES 
('Efectivo', 'Pago en efectivo', NOW(), NOW()),
('Tarjeta de Credito', 'Pago con tarjeta de credito', NOW(), NOW()),
('Tarjeta de Debito', 'Pago con tarjeta de debito', NOW(), NOW()),
('Transferencia', 'Transferencia bancaria / Yape / Plin', NOW(), NOW());
```
*(Nota: Las tablas y nuevas columnas de la Fase 1 se crean y actualizan automáticamente gracias a `spring.jpa.hibernate.ddl-auto=update`).*

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

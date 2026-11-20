# Dentify · Backend API REST

API REST de **Dentify**: Plataforma integral para la gestión de consultorios dentales (atención al paciente, historias clínicas, agenda de citas, control de inventario y facturación).

**Tecnologías:**
- Java 17
- Spring Boot 3.4.4
- Spring Security con JWT & BCrypt
- JPA / Hibernate
- MySQL 8.0
- OpenAPI / Swagger (springdoc-openapi)
- Arquitectura guiada por el dominio (DDD): contextos IAM, Patient Attention, Inventory e Invoices.

---

## 🚀 Requisitos Previos

1. **Java JDK 17** instalado y configurado en el `PATH` (`java -version`).
2. **MySQL Server 8.0** en ejecución local o remoto en el puerto 3306.
3. No requiere Maven instalado previamente (se incluye el wrapper `mvnw` y `mvnw.cmd`).

---

## 🗄️ Configuración de la Base de Datos (MySQL)

1. Conéctate a tu servidor MySQL local:
   ```sql
   mysql -u root -p
   ```
2. Crea la base de datos `dentify`:
   ```sql
   CREATE DATABASE IF NOT EXISTS dentify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   USE dentify;
   ```
3. Los métodos de pago (Efectivo, Tarjeta de Credito, Tarjeta de Debito, Transferencia) **se crean solos al arrancar** el backend (`PaymentMethodDataSeeder`). Solo se insertan los que falten, así que no hay que ejecutar ningún INSERT manual. Los clientes deben usar los ids que devuelve `GET /api/v1/payment-methods`.
4. **Solo en bases creadas antes de esta versión:** ejecuta una vez la migración manual que impide que, al borrar citas o pacientes, se borren en cascada los comprobantes:
   ```bash
   mysql -u root -p dentify < src/main/resources/db/migration-manual/V2026_11_20__invoice_fk_restrict.sql
   ```
   Es idempotente y no modifica datos. Mientras no se aplique, el backend arranca igual, pero registra un `ERROR` de `InvoiceSchemaVerifier` con esta misma ruta.

---

## ⚙️ Variables de Entorno y Configuración

El archivo `src/main/resources/application.properties` ya incluye valores por defecto listos para desarrollo local:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/dentify?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` | Cadena de conexión JDBC |
| `DB_USERNAME` | `root` | Usuario de MySQL |
| `DB_PASSWORD` | `root` | Contraseña de MySQL |
| `JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | Clave secreta HMAC para tokens JWT |
| `PORT` | `8080` | Puerto HTTP del servidor |

Si necesitas usar credenciales distintas a las por defecto (`root`/`root`), puedes definirlas en tu terminal o crear un archivo `.env`:
```bash
# Windows PowerShell:
$env:DB_USERNAME="tu_usuario"
$env:DB_PASSWORD="tu_password"

# Linux / macOS:
export DB_USERNAME=tu_usuario
export DB_PASSWORD=tu_password
```

---

## ▶️ Ejecución del Backend

### En Windows (CMD o PowerShell):
```powershell
cd Backend
.\mvnw.cmd spring-boot:run
```

### En Linux o macOS:
```bash
cd Backend
chmod +x ./mvnw
./mvnw spring-boot:run
```

El servidor iniciará en: **`http://localhost:8080`**

---

## 📖 Endpoints y Documentación

Una vez levantado el servidor:
- **Swagger UI (Documentación interactiva):**  
  [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON:**  
  [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check:**  
  [http://localhost:8080/health](http://localhost:8080/health) `-> {"status":"UP"}`

### Endpoints Principales:
* **Autenticación (Públicos):**
  * `POST /api/auth/register` - Registro de odontólogo/usuario.
  * `POST /api/auth/login` - Login y obtención del JWT `accessToken`.
* **Perfil (`Bearer <token>`):**
  * `GET /api/v1/profile` - Obtener datos del usuario autenticado.
  * `PUT /api/v1/profile/update-information` - Actualizar información personal.
  * `PUT /api/v1/profile/update-password` - Cambiar contraseña.
* **Pacientes (`Bearer <token>`):**
  * `GET /api/v1/patients` - Lista de pacientes del doctor.
  * `POST /api/v1/patients` - Crear paciente (DNI de 8 dígitos, nombre, apellido, email, dirección, fecha de nacimiento, teléfono, alergias, condiciones sistémicas y contacto de emergencia).
  * `GET /api/v1/patients/{id}` - Detalle de paciente.
  * `PUT /api/v1/patients/{id}` - Actualizar paciente.
  * `DELETE /api/v1/patients/{id}` - Eliminar paciente.
  * `GET /api/v1/patients/{patientId}/medical-histories` - Historiales médicos del paciente.
  * `POST /api/v1/patients/{patientId}/medical-histories` - Agregar entrada al historial médico.
* **Catálogo de Tratamientos Dentales (`Bearer <token>`):**
  * `GET /api/v1/treatments` - Listar tratamientos del catálogo (`TreatmentCategory`, precio y duración estimada).
  * `POST /api/v1/treatments` - Registrar nuevo tratamiento dental.
  * `PUT /api/v1/treatments/{id}` - Actualizar tratamiento.
  * `DELETE /api/v1/treatments/{id}` - Eliminar tratamiento.
* **Citas (`Bearer <token>`):**
  * `GET /api/v1/appointments` - Lista de citas ordenadas por fecha.
  * `POST /api/v1/appointments` - Agendar cita (permite vincular un `treatmentId` del catálogo y valida que no exista solapamiento con citas activas).
  * `PUT /api/v1/appointments/{id}` - Reprogramar cita.
  * `PUT /api/v1/appointments/{id}/status` - Actualizar estado de la cita (`SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_SHOW`) y registrar notas de evolución clínica (`clinicalNotes`).
  * `DELETE /api/v1/appointments/{id}` - Cancelar/eliminar cita.
* **Inventario (`Bearer <token>`):**
  * `GET /api/v1/items` - Listar insumos.
  * `POST /api/v1/items` - Registrar insumo (requiere categoría válida del enum).
  * `PUT /api/v1/items/{id}` - Actualizar stock o precio.
  * `DELETE /api/v1/items/{id}` - Eliminar insumo.
* **Facturación (`Bearer <token>`):**
  * `GET /api/v1/invoices` - Listar comprobantes de pago.
  * `POST /api/v1/invoices` - Crear factura vinculada a una cita y método de pago.
* **Dashboard (`Bearer <token>`):**
  * `GET /api/v1/dashboard` - Métricas de stock bajo, pagos recientes y próximas citas.

---

## 🧪 Pruebas Automatizadas

Para correr la suite de pruebas unitarias:
```bash
./mvnw test
```

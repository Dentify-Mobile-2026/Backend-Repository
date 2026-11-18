# 📋 Guía Rápida de Ejecución · Dentify Backend

Esta guía contiene los pasos exactos para configurar y levantar la API de Dentify localmente.

---

## 1. Requisitos
- **Java 17 JDK** (comprueba con `java -version`)
- **MySQL 8.0** activo en `localhost:3306`

---

## 2. Preparar Base de Datos MySQL
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

---

## 3. Configuración
La aplicación ya cuenta con valores predeterminados en `application.properties`:
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
```bash
# En Bash / macOS / Linux:
export DB_USERNAME=tu_usuario
export DB_PASSWORD=tu_password
```

---

## 4. Ejecución del Servidor

### En Windows:
```cmd
cd Backend
mvnw.cmd spring-boot:run
```

### En Linux / macOS:
```bash
cd Backend
chmod +x ./mvnw
./mvnw spring-boot:run
```

---

## 5. Verificar que está corriendo
Abre en tu navegador:
1. **Health Check:** [http://localhost:8080/health](http://localhost:8080/health) -> debe responder `{"status":"UP"}`.
2. **Swagger UI:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).

---

## 6. Usuarios de Prueba
* **Usuario:** `dentist1`
* **Contraseña:** `password123`
*(O puedes registrar nuevos usuarios mediante `POST /api/auth/register` o desde el Front en Flutter)*.

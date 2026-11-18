# Dentify · Backend

API REST de Dentify: gestión de citas, pacientes, historias clínicas, inventario y pagos para consultorios dentales.

**Tecnología:** Java 17, Spring Boot 3.4.4, Spring Security con JWT, JPA/Hibernate, MySQL, Swagger (springdoc). Arquitectura DDD con los contextos IAM, atención al paciente, inventario y facturación.

## Variables de entorno

| Variable | Descripción |
|----------|-------------|
| `DB_URL` | URL JDBC de MySQL, por ejemplo `jdbc:mysql://host:3306/dentify?sslMode=REQUIRED` |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Contraseña de la base de datos |
| `JWT_SECRET` | Secreto para firmar los tokens (mínimo 32 caracteres) |
| `PORT` | Puerto HTTP (por defecto 8080) |

Hay un ejemplo en `.env.example`. Nunca subas valores reales al repositorio.

## Ejecutar y probar

```bash
./mvnw test                      # pruebas unitarias (no necesitan base de datos)
export DB_URL=... DB_USERNAME=... DB_PASSWORD=... JWT_SECRET=...
./mvnw spring-boot:run           # Swagger en /swagger-ui/index.html, salud en /health
docker build -t dentify-api .    # imagen para despliegue
```

## Regla de citas

Una cita no puede solaparse con otra del mismo profesional. Dos citas seguidas (una termina cuando empieza la otra) sí son válidas. Si hay solapamiento, `POST /api/v1/appointments` y `PUT /api/v1/appointments/{id}` responden **409 Conflict**.



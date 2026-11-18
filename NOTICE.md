# Origen del proyecto y créditos

**Servicio web (API REST)** de **Dentify**, producto de la startup MediTech.

Este repositorio es una **versión reestructurada y mejorada** del trabajo del grupo anterior, desarrollado en el curso *Aplicaciones para Dispositivos Móviles* (ciclo 2025-01, UPC). El código base se importó como un único commit; el historial de git de esta versión empieza en el ciclo 2026-20.

## Autores del código original (grupo 2025-01)

- Bohorquez Lerzundi, Gerardo Sebastián
- Huanaco Huayta, Elizabeth Lucero
- Huapaya Cuevas, Anthony
- Silva Tirado, Sebastián Valentino
- Uribe Quispe, Jesús Guillermo

## Equipo del ciclo 2026-20 (curso 1ASI0570 Desarrollo Agile de Productos de Software, sección 16062)

- Horna Silva, Fabio Ernesto
- Aranda Vallejos, Oscar Gabriel
- Bohorquez Lerzundi, Gerardo Sebastian
- Travezaño Patiño, Eduard Gedeon
- Holguín Gamarra, Hardie Alfonso
- Silva Morales, Renzo Cesar

## Trabajo previo del ciclo 2026-20 incorporado

Oscar Gabriel Aranda Vallejos, del 02 al 04/11/2026: Dockerfile para el despliegue, endpoint `/health`, configuración de la base de datos y esquema de seguridad de Swagger.

## Cambios de este repositorio respecto a la base importada

- Las credenciales de base de datos y el secreto JWT salen del código y se leen de variables de entorno.
- Validación de citas que se solapan en el mismo profesional (respuesta HTTP 409), con pruebas unitarias.

Cada integrante del equipo 2026-20 agrega sus cambios mediante ramas y pull requests, con su propia cuenta de GitHub.

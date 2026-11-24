-- =====================================================================================
-- Migración manual V2026_11_24 · Hardening multi-tenant de paciente y bitácora de stock
-- =====================================================================================
-- 1) Permite que distintos consultorios (user_id) atiendan al mismo paciente por DNI,
--    reemplazando la restricción única global patient(dni) por patient(dni, user_id).
-- 2) Asegura en bases preexistentes que log_entry.invoice_id acepte NULL y
--    consumed_quantity >= 0 para movimientos manuales/reposiciones de inventario.
-- Es idempotente y segura para ejecutar varias veces.
-- =====================================================================================

-- A) Eliminar índice único global sobre patient.dni si existe (ej. generado por Hibernate)
SET @legacy_dni_idx := (
  SELECT s.INDEX_NAME
    FROM information_schema.STATISTICS s
   WHERE s.TABLE_SCHEMA = DATABASE()
     AND s.TABLE_NAME = 'patient'
     AND s.NON_UNIQUE = 0
     AND s.INDEX_NAME <> 'PRIMARY'
   GROUP BY s.INDEX_NAME
  HAVING COUNT(*) = 1 AND MAX(s.COLUMN_NAME) = 'dni'
   LIMIT 1
);

SET @drop_dni_idx_sql := IF(
  @legacy_dni_idx IS NOT NULL,
  CONCAT('ALTER TABLE patient DROP INDEX `', @legacy_dni_idx, '`'),
  'SELECT ''Sin índice único global heredado en patient.dni'' AS info'
);

PREPARE stmt_drop_dni FROM @drop_dni_idx_sql;
EXECUTE stmt_drop_dni;
DEALLOCATE PREPARE stmt_drop_dni;

-- B) Crear restricción única compuesta uk_patient_dni_user(dni, user_id) si no existe
SET @uk_dni_user_exists := (
  SELECT COUNT(*)
    FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'patient'
     AND INDEX_NAME = 'uk_patient_dni_user'
);

SET @add_uk_dni_user_sql := IF(
  @uk_dni_user_exists = 0,
  'ALTER TABLE patient ADD CONSTRAINT uk_patient_dni_user UNIQUE (dni, user_id)',
  'SELECT ''uk_patient_dni_user ya existe'' AS info'
);

PREPARE stmt_add_uk FROM @add_uk_dni_user_sql;
EXECUTE stmt_add_uk;
DEALLOCATE PREPARE stmt_add_uk;

-- C) Asegurar log_entry.invoice_id nullable
SET @log_invoice_not_null := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'log_entry'
     AND COLUMN_NAME = 'invoice_id'
     AND IS_NULLABLE = 'NO'
);

SET @fix_log_invoice_sql := IF(
  @log_invoice_not_null > 0,
  'ALTER TABLE log_entry MODIFY COLUMN invoice_id bigint NULL',
  'SELECT ''log_entry.invoice_id ya permite NULL'' AS info'
);

PREPARE stmt_fix_log_inv FROM @fix_log_invoice_sql;
EXECUTE stmt_fix_log_inv;
DEALLOCATE PREPARE stmt_fix_log_inv;

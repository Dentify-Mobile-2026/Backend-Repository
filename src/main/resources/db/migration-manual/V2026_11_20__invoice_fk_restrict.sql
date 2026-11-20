-- =====================================================================================
-- Migración manual V2026_11_20 · Protección de comprobantes frente a borrados en cascada
-- =====================================================================================
-- Problema: en bases creadas antes de este cambio, la FK invoice.appointment_id fue
-- generada por Hibernate con ON DELETE CASCADE. Borrar una cita (o un paciente, que a su
-- vez borra sus citas en cascada) eliminaba en MySQL los comprobantes asociados.
--
-- Esta migración deja una única FK invoice.appointment_id -> appointment.id con
-- ON DELETE RESTRICT. Se ejecuta A MANO, una sola vez por base, por ejemplo:
--   mysql -u <usuario> -p <base> < V2026_11_20__invoice_fk_restrict.sql
-- El backend NO ejecuta ALTER TABLE automáticamente: al arrancar solo verifica la regla
-- (InvoiceSchemaVerifier) y registra un ERROR mientras esta migración no se haya aplicado.
--
-- Propiedades:
--  * Idempotente: si la FK ya es RESTRICT/NO ACTION no hace cambios.
--  * No modifica datos: solo la definición de la restricción.
--  * Un único ALTER TABLE (DDL atómico en InnoDB / MySQL 8).
--  * foreign_key_checks se desactiva solo para esta sesión y solo durante el ALTER: es lo
--    que permite a MySQL eliminar y crear la FK en la misma sentencia. Los datos ya
--    cumplen la restricción porque la FK anterior apuntaba a la misma columna y tabla.
--  * Requiere privilegio ALTER sobre la tabla invoice.
-- =====================================================================================

-- 1) Estado previo
SELECT rc.CONSTRAINT_NAME AS fk, rc.DELETE_RULE AS regla_borrado
  FROM information_schema.REFERENTIAL_CONSTRAINTS rc
  JOIN information_schema.KEY_COLUMN_USAGE k
    ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
   AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
   AND k.TABLE_NAME = rc.TABLE_NAME
 WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
   AND rc.TABLE_NAME = 'invoice'
   AND rc.REFERENCED_TABLE_NAME = 'appointment'
   AND k.COLUMN_NAME = 'appointment_id';

-- 2) Construcción de un único ALTER TABLE
SET @dentify_fk_drops := (
  SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', rc.CONSTRAINT_NAME, '`') SEPARATOR ', ')
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    JOIN information_schema.KEY_COLUMN_USAGE k
      ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
     AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
     AND k.TABLE_NAME = rc.TABLE_NAME
   WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
     AND rc.TABLE_NAME = 'invoice'
     AND rc.REFERENCED_TABLE_NAME = 'appointment'
     AND k.COLUMN_NAME = 'appointment_id'
     AND rc.DELETE_RULE NOT IN ('RESTRICT', 'NO ACTION'));

SET @dentify_fk_safe := (
  SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    JOIN information_schema.KEY_COLUMN_USAGE k
      ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
     AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
     AND k.TABLE_NAME = rc.TABLE_NAME
   WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
     AND rc.TABLE_NAME = 'invoice'
     AND rc.REFERENCED_TABLE_NAME = 'appointment'
     AND k.COLUMN_NAME = 'appointment_id'
     AND rc.DELETE_RULE IN ('RESTRICT', 'NO ACTION'));

SET @dentify_fk_sql := CASE
  WHEN @dentify_fk_drops IS NULL AND @dentify_fk_safe > 0
    THEN 'SELECT ''Sin cambios: la FK ya es RESTRICT/NO ACTION'' AS resultado'
  WHEN @dentify_fk_drops IS NULL
    THEN 'ALTER TABLE invoice ADD CONSTRAINT fk_invoice_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id) ON DELETE RESTRICT'
  WHEN @dentify_fk_safe > 0
    THEN CONCAT('ALTER TABLE invoice ', @dentify_fk_drops)
  ELSE CONCAT('ALTER TABLE invoice ', @dentify_fk_drops,
              ', ADD CONSTRAINT fk_invoice_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id) ON DELETE RESTRICT')
END;

-- 3) Ejecución
SET @dentify_previous_fk_checks := @@SESSION.foreign_key_checks;
SET SESSION foreign_key_checks = 0;
PREPARE dentify_fk_stmt FROM @dentify_fk_sql;
EXECUTE dentify_fk_stmt;
DEALLOCATE PREPARE dentify_fk_stmt;
SET SESSION foreign_key_checks = @dentify_previous_fk_checks;

-- 4) Estado posterior (esperado: una sola fila con RESTRICT o NO ACTION)
SELECT rc.CONSTRAINT_NAME AS fk, rc.DELETE_RULE AS regla_borrado
  FROM information_schema.REFERENTIAL_CONSTRAINTS rc
  JOIN information_schema.KEY_COLUMN_USAGE k
    ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
   AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
   AND k.TABLE_NAME = rc.TABLE_NAME
 WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
   AND rc.TABLE_NAME = 'invoice'
   AND rc.REFERENCED_TABLE_NAME = 'appointment'
   AND k.COLUMN_NAME = 'appointment_id';

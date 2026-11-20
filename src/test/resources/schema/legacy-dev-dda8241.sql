-- Esquema de la rama dev (dda8241) ANTES del Sprint 1 del módulo financiero.
-- Generado con: mysqldump --no-data --compact sobre una base creada por el backend de dev (ddl-auto=update).
-- Se usa en las pruebas de integración para simular una base existente con invoice.appointment_id ON DELETE CASCADE.

SET FOREIGN_KEY_CHECKS = 0;
CREATE TABLE `appointment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `appointment_date` datetime(6) NOT NULL,
  `clinical_notes` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed` bit(1) NOT NULL,
  `duration` time(6) NOT NULL,
  `reason` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CANCELLED','COMPLETED','CONFIRMED','IN_PROGRESS','NO_SHOW','SCHEDULED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `patient_id` bigint NOT NULL,
  `treatment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_appointment_patient` (`patient_id`),
  KEY `fk_appointment_treatment` (`treatment_id`),
  CONSTRAINT `fk_appointment_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_appointment_treatment` FOREIGN KEY (`treatment_id`) REFERENCES `treatment` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` double NOT NULL,
  `appointment_id` bigint DEFAULT NULL,
  `paymentmethod_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsij7yenf2dlvvxb2d32x12ijc` (`appointment_id`),
  KEY `FKqxphoswuxdvg6xv23p4bgeaj6` (`paymentmethod_id`),
  CONSTRAINT `FKh2quh5xy8jiqbaxy6c9m8qe6d` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKqxphoswuxdvg6xv23p4bgeaj6` FOREIGN KEY (`paymentmethod_id`) REFERENCES `payment_method` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `category` enum('CLEANING_AND_DISINFECTION','CONSUMABLES','DENTAL_INSTRUMENTS','DISPOSABLE_SUPPLIES','ENDODONTIC_SUPPLIES','IMAGING_EQUIPMENT','MEDICATIONS','ORTHODONTIC_SUPPLIES','PROTECTIVE_EQUIPMENT','RESTORATIVE_MATERIALS','SURGICAL_SUPPLIES') COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` double NOT NULL,
  `stock_quantity` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKh4epdoqikj4sfedlxcc9dwwnl` (`user_id`),
  CONSTRAINT `FKh4epdoqikj4sfedlxcc9dwwnl` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `item_chk_1` CHECK ((`stock_quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `log_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `consumed_quantity` int NOT NULL,
  `new_stock` int NOT NULL,
  `previous_stock` int NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqw2v83sj9rgqe15lygocjxtcp` (`invoice_id`),
  KEY `FKmf8wbqls4kveoeytl8mpvlo78` (`item_id`),
  CONSTRAINT `FKmf8wbqls4kveoeytl8mpvlo78` FOREIGN KEY (`item_id`) REFERENCES `item` (`id`),
  CONSTRAINT `FKqw2v83sj9rgqe15lygocjxtcp` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`),
  CONSTRAINT `log_entry_chk_1` CHECK ((`consumed_quantity` >= 0)),
  CONSTRAINT `log_entry_chk_2` CHECK ((`new_stock` >= 0)),
  CONSTRAINT `log_entry_chk_3` CHECK ((`previous_stock` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `medical_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `diagnosis` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `medication` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `record` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `patient_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_medicalhistory_patient` (`patient_id`),
  CONSTRAINT `fk_medicalhistory_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `patient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `birthday` date DEFAULT NULL,
  `dni` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_contact_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_contact_phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `home_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `allergies` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `systemic_conditions` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `first_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK926ljp22stqqkcoqi7wm1fa09` (`dni`),
  KEY `FKp6ttmfrxo2ejiunew4ov805uc` (`user_id`),
  CONSTRAINT `FKp6ttmfrxo2ejiunew4ov805uc` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `payment_method` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `treatment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `category` enum('AESTHETICS','ENDODONTICS','ORTHODONTICS','PERIODONTICS','PREVENTION','PROSTHODONTICS','RESTORATIVE','SURGERY') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(350) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `estimated_duration_minutes` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` double NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcesd3qmwlcfaemy455u4va5ji` (`user_id`),
  CONSTRAINT `FKcesd3qmwlcfaemy455u4va5ji` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `treatment_chk_1` CHECK ((`estimated_duration_minutes` >= 5)),
  CONSTRAINT `treatment_chk_2` CHECK ((`price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `first_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trial` bit(1) DEFAULT NULL,
  `username` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsb8bbouer5wak8vyiiy4pf2bx` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 1;

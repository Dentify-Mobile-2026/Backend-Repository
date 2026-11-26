# language: es
Característica: Citas sin cruces y dentro del horario de atención (US04 a US09, US42, US43, US45, US55, US56)
  Como personal del consultorio
  quiero agendar citas que no se crucen y que respeten el horario de atención
  para no perder pacientes por reprogramaciones.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo
    Y que tengo un paciente registrado

  Escenario: Agendar una cita en un horario libre
    Cuando agendo una cita el "2027-03-01" a las "10:00" por "01:00"
    Entonces la respuesta es 201
    Y la cita queda en estado "SCHEDULED"

  Escenario: Rechazar una cita que se cruza con otra
    Dado que tengo una cita el "2027-03-01" a las "10:00" por "01:00"
    Cuando agendo una cita el "2027-03-01" a las "10:30" por "00:30"
    Entonces la respuesta es 409

  Escenario: Rechazar una cita en sábado con horario de lunes a viernes
    Dado que el consultorio atiende de lunes a viernes de "09:00" a "18:00"
    Cuando agendo una cita el "2027-03-06" a las "10:00" por "01:00"
    Entonces la respuesta es 409
    Y el código de error es "OUTSIDE_BUSINESS_HOURS"

  Escenario: Rechazar una cita en un día no laborable
    Dado que el consultorio atiende de lunes a viernes de "09:00" a "18:00"
    Y que el lunes es un día no laborable
    Cuando agendo una cita el "2027-03-01" a las "10:00" por "01:00"
    Entonces la respuesta es 409
    Y el código de error es "OUTSIDE_BUSINESS_HOURS"

  Escenario: Un cambio de horario se aplica a las citas nuevas
    Dado que el consultorio atiende de lunes a viernes de "09:00" a "18:00"
    Cuando cambio el horario de lunes a viernes a "09:00" hasta "20:00"
    Y agendo una cita el "2027-03-01" a las "18:30" por "01:00"
    Entonces la respuesta es 201

  Escenario: Marcar una cita como atendida con su nota de evolución
    Dado que tengo una cita el "2027-03-01" a las "10:00" por "01:00"
    Cuando marco la cita como atendida con la nota "Limpieza sin complicaciones"
    Entonces la respuesta es 200
    Y la cita queda en estado "COMPLETED"

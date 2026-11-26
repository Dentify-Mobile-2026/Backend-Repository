# language: es
Característica: Pacientes con alertas médicas e historia clínica (US12 a US19, US39, US40, US44)
  Como odontólogo
  quiero registrar a mis pacientes con sus alertas y su historia clínica
  para atenderlos con información completa y sin perder registros.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo

  Escenario: Registrar un paciente con sus alertas médicas
    Cuando registro un paciente alérgico a "Penicilina"
    Entonces la respuesta es 201
    Y el paciente muestra la alergia "Penicilina"

  Escenario: Consultar la ficha de un paciente
    Dado que tengo un paciente registrado
    Cuando consulto la ficha del paciente
    Entonces la respuesta es 200

  Escenario: Registrar una entrada en la historia clínica
    Dado que tengo un paciente registrado
    Cuando registro en su historia clínica el diagnóstico "Caries en pieza 21"
    Entonces la respuesta es 201

  Escenario: Anular una entrada de la historia clínica con un motivo
    Dado que tengo un paciente con una entrada en su historia clínica
    Cuando anulo la entrada con el motivo "Se registró en otro paciente"
    Entonces la respuesta es 200
    Y la entrada queda anulada y sigue visible en la historia

  Escenario: No se permite anular una entrada sin motivo
    Dado que tengo un paciente con una entrada en su historia clínica
    Cuando anulo la entrada sin motivo
    Entonces la respuesta es 400

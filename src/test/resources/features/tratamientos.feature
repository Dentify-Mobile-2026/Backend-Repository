# language: es
Característica: Catálogo de tratamientos y planes (US41, US52 a US54)
  Como odontólogo
  quiero un catálogo de tratamientos y planes por paciente
  para presupuestar y seguir el avance de cada tratamiento.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo

  Escenario: Registrar un tratamiento en el catálogo
    Cuando registro el tratamiento "Resina simple" de "120.50" soles y 30 minutos
    Entonces la respuesta es 201

  Escenario: Armar un plan de tratamiento con dos piezas
    Dado que tengo un paciente registrado
    Y que tengo el tratamiento "Resina simple" de "120.50" soles y 30 minutos
    Cuando armo un plan con ese tratamiento en las piezas "11" y "21"
    Entonces la respuesta es 201
    Y el plan suma "241.00"

  Escenario: Registrar el avance de un plan aprobado
    Dado que tengo un paciente con un plan aprobado de dos piezas
    Cuando marco la primera pieza como hecha
    Entonces la respuesta es 200
    Y el plan muestra un avance de "50.00" por ciento

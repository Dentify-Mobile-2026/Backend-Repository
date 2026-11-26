# language: es
Característica: Control del inventario de insumos (US20 a US23, US25 a US28, US57)
  Como personal del consultorio
  quiero registrar los insumos y su consumo
  para saber cuánto stock queda y qué se usó en cada atención.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo

  Escenario: Registrar un insumo
    Cuando registro el insumo "Guantes de nitrilo" con 100 unidades
    Entonces la respuesta es 201

  Escenario: Registrar el consumo de un insumo en una atención
    Dado que tengo el insumo "Anestesia" con 10 unidades
    Y que tengo una cita atendida de un paciente
    Y que emití un comprobante de "120.00" con un abono inicial de "120.00"
    Cuando registro el consumo de 3 unidades en esa atención
    Entonces la respuesta es 201
    Y el insumo queda con 7 unidades
    Y el movimiento queda registrado de 10 a 7 unidades

  Escenario: No se puede consumir más de lo que hay
    Dado que tengo el insumo "Resina" con 2 unidades
    Y que tengo una cita atendida de un paciente
    Y que emití un comprobante de "120.00" con un abono inicial de "120.00"
    Cuando registro el consumo de 5 unidades en esa atención
    Entonces la respuesta es 409
    Y el mensaje de error es "Quantity exceeded"
    Y el insumo queda con 2 unidades

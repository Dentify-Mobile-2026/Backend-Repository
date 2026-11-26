# language: es
Característica: Panel y analítica del consultorio (US24, US58)
  Como dueño del consultorio
  quiero ver un resumen y la analítica del mes
  para decidir con datos sobre mi consultorio.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo

  Escenario: Ver el resumen del panel
    Cuando abro el panel
    Entonces la respuesta es 200

  Escenario: La analítica del mes refleja lo cobrado
    Dado que tengo una cita atendida de un paciente
    Y que emití un comprobante de "950.00" con un abono inicial de "350.00"
    Cuando consulto la analítica del mes
    Entonces la respuesta es 200
    Y lo cobrado en el mes es "350.00"

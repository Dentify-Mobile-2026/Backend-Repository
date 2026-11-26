# language: es
Característica: Cobro con abonos y saldos (US11, US38, US46 a US51)
  Como dueño del consultorio
  quiero registrar cada cobro ligado a su cita, con abonos y saldo
  para saber cuánto me deben y cuánto entró.

  Antecedentes:
    Dado que inicié sesión como un consultorio nuevo
    Y que tengo una cita atendida de un paciente

  Escenario: Emitir el comprobante con un abono parcial
    Cuando emito un comprobante de "950.00" con un abono inicial de "350.00"
    Entonces la respuesta es 201
    Y el comprobante queda en estado "PARTIAL" con saldo "600.00"

  Escenario: Completar el pago con otro abono
    Dado que emití un comprobante de "950.00" con un abono inicial de "350.00"
    Cuando registro un abono de "600.00"
    Entonces la respuesta es 201
    Y el comprobante queda en estado "PAID" con saldo "0.00"

  Escenario: Rechazar un abono mayor al saldo
    Dado que emití un comprobante de "950.00" con un abono inicial de "350.00"
    Cuando registro un abono de "600.01"
    Entonces la respuesta es 409
    Y el comprobante sigue en estado "PARTIAL" con saldo "600.00"

  Escenario: Un abono repetido no se cobra dos veces
    Dado que emití un comprobante de "950.00" con un abono inicial de "350.00"
    Cuando registro dos veces el mismo abono de "100.00"
    Entonces la respuesta es 200
    Y el comprobante queda en estado "PARTIAL" con saldo "500.00"

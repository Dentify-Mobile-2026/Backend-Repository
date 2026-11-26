# language: es
Característica: Acceso seguro del personal (US01, US02, US29, US30, US59)
  Como personal del consultorio
  quiero registrarme e iniciar sesión de forma segura
  para que solo mi consultorio vea la información de sus pacientes.

  Escenario: Registro de un consultorio nuevo
    Cuando registro un consultorio con un usuario nuevo
    Entonces la respuesta es 201
    Y recibo un token de acceso

  Escenario: Inicio de sesión con credenciales correctas
    Dado que registré un consultorio con un usuario nuevo
    Cuando inicio sesión con mi usuario y mi contraseña
    Entonces la respuesta es 200
    Y recibo un token de acceso

  Escenario: Inicio de sesión con una contraseña incorrecta
    Dado que registré un consultorio con un usuario nuevo
    Cuando inicio sesión con mi usuario y una contraseña incorrecta
    Entonces la respuesta es 401
    Y el mensaje de error es "Usuario o contraseña incorrectos"

  Escenario: Acceso a la información sin iniciar sesión
    Cuando consulto la lista de pacientes sin iniciar sesión
    Entonces la respuesta es 401

  Escenario: Consulta del perfil del usuario
    Dado que inicié sesión como un consultorio nuevo
    Cuando consulto mi perfil
    Entonces la respuesta es 200
    Y el perfil muestra mi nombre de usuario

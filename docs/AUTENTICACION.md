# Autenticación, roles y chat por rol

## Cómo probarlo
1. Ejecuta `sql/00_usuarios.sql` en MySQL Workbench (tabla `usuario` + columna `reservacion.id_usuario`).
2. Arranca la app. Se crean dos cuentas ya verificadas:
   - `admin@terranova.com` / `Admin123!` → rol ADMINISTRADOR (todo lo de siempre).
   - `cliente@gmail.com` / `Cliente123!` → rol USUARIO (landing, mis reservaciones, asistente).
3. Abre `http://localhost:8080/`: sin sesión te manda a `/login` (iniciar sesión o crear cuenta).
4. Registro nuevo: Crear cuenta → ventana "Simulación de correo: Tu código de verificación es X" → ingresar el código en `/verificar` → iniciar sesión.

## Dónde está cada pieza (paquete `com.hoteleria.gestion_hoteles`)
| Pieza | Archivo |
|---|---|
| Entidad Usuario / Rol | `entity/Usuario`, `entity/Rol`, `repository/UsuarioRepository` |
| Registro, dominios, código, login (BCrypt) | `auth/AuthService` |
| Controlador de Auth (JSON) | `auth/AuthController` (`/api/auth/register`, `/verify`, `/resend-code`, `/login`, `/me`) |
| Filtro JWT | `seguridad/JwtAuthFilter` + `seguridad/JwtService` |
| Reglas de acceso por rol | `seguridad/SecurityConfig` |
| Menú/vistas por rol | `fragments/layout.html` (`esAdmin`), `usuario/inicio.html`, `usuario/mis-reservaciones.html` |
| System Prompt Admin / Usuario | `ia/service/HotelAssistantService` (`SISTEMA`, `SISTEMA_USUARIO`) |
| Herramientas del chat de usuario | `ia/tools/UsuarioReservationTool` + clientes `...UsuarioClient` en `ia/config/AiConfig` |

## Reglas de dominio (registro)
`@terranova.com` → ADMINISTRADOR · `@gmail.com` / `@outlook.com` → USUARIO · otro dominio → HTTP 400.

## Chat por rol
- ADMINISTRADOR: mismo asistente de siempre (todas las herramientas).
- USUARIO: cliente de IA distinto, solo con información pública (hoteles, habitaciones libres, precios) y sus propias reservaciones. El backend inyecta su identidad (id, nombre, correo del JWT) en el prompt; no recibe las herramientas de ocupadas/mantenimiento/estancias/estadísticas.
  - Regla A: sin reservaciones → «Aún no tienes reservaciones, por lo que no puedo mostrarte información. ¿Deseas hacer una?» (sin pedir folio).
  - Regla B: con reservaciones, `misReservaciones` las consulta directo por su id de cuenta.
  - Regla C: modificar/cancelar solo las suyas (el servidor lo comprueba en `ReservationTool.buscarPropia`).
  - Regla D: el prompt prohíbe lo administrativo y además no tiene las herramientas para obtenerlo.
- Los chats se aíslan por cuenta (ids `u{idUsuario}_...`): nadie puede leer el chat de otro aunque adivine su id.

## Postman
Login: `POST /api/auth/login` → copia `token` y úsalo como `Authorization: Bearer <token>` en `/api/hotel-ai/ask`, `/api/v1/chat`, etc.

## Seguridad / límites de esta prueba
- La verificación de correo es simulada (código en consola y en el JSON): cualquiera puede registrar `@terranova.com` y quedar como administrador. En producción hay que enviar el correo de verdad y no aceptar el rol administrador por autoregistro.
- JWT firmado HS256 con `JWT_SECRET` (≥ 32 caracteres; define la tuya). Cookie `TN_TOKEN` HttpOnly + SameSite=Strict; CSRF desactivado por no haber sesión de servidor.

---
## Actualización (verificación por enlace)
El código de 6 dígitos fue reemplazado por un enlace de un solo uso `APP_BASE_URL/verificar?token=…` (hash SHA-256 en BD, vigencia 24 h) enviado por Gmail de forma asíncrona. El registro público siempre crea USUARIO; los administradores se crean en «Usuarios». Endpoints: `POST /api/auth/register`, `/verify {token}`, `/resend-verification {correo}`, `/login`.

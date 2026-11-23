# Despliegue de TerraNova (acceso desde cualquier dispositivo)

En local todo apunta a `localhost`, por eso los enlaces del correo (verificación) solo abren en tu PC. Al desplegar,
la app queda en una URL pública HTTPS y cualquier teléfono o computadora puede abrirla.

## 1. Qué cambia al desplegar
| Tema | Local | Producción |
|---|---|---|
| URL de la app | `http://localhost:8080` | `https://tu-app.up.railway.app` (o tu dominio) |
| `APP_BASE_URL` | `http://localhost:8080` | **la URL pública** (con ella se arman los enlaces del correo) |
| Perfil | por defecto | `SPRING_PROFILES_ACTIVE=prod` (Dockerfile ya lo fija) |
| Cookie de sesión | sin `Secure` | `Secure` + `HttpOnly` + `SameSite=Strict` (automático con el perfil `prod`) |
| Base de datos | MySQL local | MySQL del proveedor (Railway MySQL, PlanetScale, Aiven…) |
| IA | Gemini (y Ollama local) | **solo Gemini**: Ollama corre en tu PC y no existe en el servidor |
| `AUTH_DEV_MODE` | opcional | `false` (nunca mostrar códigos en el JSON) |

## 2. Variables de entorno obligatorias
```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://HOST:PUERTO/terranova_db?useSSL=true&serverTimezone=America/Mexico_City
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...                  # mínimo 32 caracteres (openssl rand -base64 48)
GEMINI_API_KEY=...
MAIL_USERNAME=tu-cuenta@gmail.com
MAIL_PASSWORD=...               # contraseña de APLICACIÓN de Google (no la normal)
MAIL_FROM=tu-cuenta@gmail.com   # debe ser la misma cuenta de Gmail
APP_BASE_URL=https://tu-app.up.railway.app
```
`PORT` lo inyecta el proveedor. Nunca subas estos valores a git (`.env` está en `.gitignore`).

## 3. Pasos (Railway)
1. Sube el repo a GitHub (`./scripts/limpiar-proyecto.sh` antes; revisa que no haya secretos).
2. Railway → New Project → Deploy from GitHub (detecta el `Dockerfile`).
3. Añade el plugin **MySQL**, copia host/puerto/usuario/clave a `DB_URL`, `DB_USER`, `DB_PASSWORD`.
4. En MySQL Workbench conéctate a esa base y ejecuta **en orden** `sql/00` … `sql/04` (el esquema es manual: `ddl-auto=none`).
   Crea antes las tablas `hotel`, `tipo_habitacion`, `habitacion` y `estancia` si tu base es nueva.
5. Define las variables del punto 2. Primero despliega para obtener la URL pública y luego pon esa URL en `APP_BASE_URL`; redepliega.
6. Entra con `admin@terranova.com` y **cambia la contraseña de prueba** (`Admin123!`); crea gerente y recepcionista en `/usuarios`.

## 4. Verificación rápida
- Abre la URL desde el celular con datos móviles (no con tu Wi-Fi local).
- Regístrate con un Gmail real: el correo debe traer un enlace `https://tu-app…/verificar/…`.
- Inicia sesión como huésped, gerente y recepción y comprueba que cada uno vea solo su menú.
- Prueba el chat; si falla, revisa `GEMINI_API_KEY` y los logs.

## 5. Prompt para el siguiente chat (copiar y pegar)
> Contexto: proyecto Spring Boot "gestion-hoteles" (TerraNova), Java 21, Spring Boot 4.1, Spring Security con JWT en cookie
> HttpOnly `TN_TOKEN` (SameSite=Strict), JPA + MySQL con `ddl-auto=none` y scripts manuales en `/sql`, Thymeleaf, Spring AI
> (Gemini principal). Roles: ADMINISTRADOR, GERENTE, RECEPCIONISTA, USUARIO; cada rol tiene su propio asistente de IA con
> herramientas distintas. Tengo el zip adjunto. **Objetivo: preparar el despliegue en Railway para abrirlo desde cualquier
> dispositivo.** Revisa `docs/DESPLIEGUE.md` y haz: (1) verifica que `application-prod.properties` no tenga valores por omisión
> con secretos y que la cookie `TN_TOKEN` salga `Secure` en producción; (2) confirma que los enlaces de correo usan `APP_BASE_URL`
> y no `localhost`; (3) revisa `forward-headers-strategy` para HTTPS tras el proxy; (4) genera un script SQL único con el
> esquema completo (hotel, tipo_habitacion, habitacion, estancia, reservacion, pago, usuario, chat) para MySQL del proveedor;
> (5) añade health check (`/actuator/health` o similar) y límites de intentos de login; (6) entrega el código completo de lo
> modificado y el zip. Entrega algo temprano por si se agotan los tokens. No compilé ni probé nada: avísame qué debo ejecutar.

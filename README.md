# TerraNova · gestion-hoteles

Spring Boot 4 · Thymeleaf · MySQL · JWT · Spring AI (Gemini principal, Ollama secundario) · correo real (Gmail) · pago simulado.

## 1. Base de datos
Con `ddl-auto=none` ejecuta en MySQL Workbench, **en orden y una sola vez** (sobre `terranova_db`):
`sql/00_usuarios.sql` (si es instalación nueva) → `sql/01_reservaciones.sql` → `sql/02_usuario_verificacion_por_enlace.sql` → `sql/03_pago_simulado.sql` → `sql/04_roles_y_checkin.sql`.
(Las tablas de chat las crea la aplicación sola.) Si una columna ya existe MySQL avisará: ignora ese error y continúa.

> Despliegue en la nube (acceso desde cualquier dispositivo): ver `docs/DESPLIEGUE.md`.

## 2. Variables de entorno (IntelliJ)
`Run > Edit Configurations… > Spring Boot > Environment variables` (separadas por `;`):

| Variable | Para qué |
|---|---|
| `DB_PASSWORD` | contraseña de MySQL (`DB_USER` opcional, por defecto `root`) |
| `GEMINI_API_KEY` | API key de Gemini |
| `MAIL_USERNAME` | `terranova.hoteles@gmail.com` |
| `MAIL_PASSWORD` | contraseña de aplicación de Gmail (16 letras, sin espacios) |
| `APP_BASE_URL` | URL pública para los enlaces del correo (local: `http://localhost:8080`) |
| `JWT_SECRET` | mínimo 32 caracteres (obligatoria en `prod`) |

Ejemplo: `DB_PASSWORD=xxx;GEMINI_API_KEY=xxx;MAIL_USERNAME=terranova.hoteles@gmail.com;MAIL_PASSWORD=xxxxxxxxxxxxxxxx;APP_BASE_URL=http://localhost:8080`

> Las claves que estaban en `application.properties` de tu zip anterior (Gemini y MySQL) quedaron expuestas: **revócalas y crea nuevas**.

## 3. Probar el correo
1. Arranca la app con las variables anteriores. 2. Abre `/registro` y crea una cuenta con un correo tuyo.
3. Debe llegar «Confirma tu correo · TerraNova»; el botón abre `APP_BASE_URL/verificar?token=…` y muestra éxito. 4. Inicia sesión.
Si no llega: revisa spam y el log (`[CORREO] no se pudo enviar…`); usa «Reenviar correo» (máx. 1 por minuto).
Sin correo configurado puedes probar con `AUTH_DEV_MODE=true`: el JSON del registro incluye `enlaceVerificacion` (nunca en producción).

## 4. Que el enlace funcione desde otros dispositivos
- **Cloudflare Tunnel:** `cloudflared tunnel --url http://localhost:8080` → copia la URL `https://xxxx.trycloudflare.com`, ponla en `APP_BASE_URL` y reinicia la app.
- **ngrok:** `ngrok http 8080` → usa la URL https en `APP_BASE_URL`.
- **Render/Railway:** despliega con `spring.profiles.active=prod` (`SPRING_PROFILES_ACTIVE=prod`) y define `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_BASE_URL`. Necesita una MySQL accesible (Railway la ofrece) y ejecutar los `.sql`.
  Si tu entorno solo permite cargar el JAR: `./mvnw package` y `java -jar target/*.jar`.

## 5. Cuentas y roles
Sembradas: `admin@terranova.com / Admin123!` y `cliente@gmail.com / Cliente123!`. El registro público **siempre** crea USUARIO; los administradores se crean en **Usuarios** (menú del admin), donde también se activa/desactiva a cualquiera.

## 6. Pago simulado
Reservar por el chat deja la reservación en `Pendiente_Pago` (habitación retenida 15 min) y el chat entrega `/pago/{folio}`. Tarjetas: `4242 4242 4242 4242` aprueba · `4000 0000 0000 0002` rechaza. Vencimiento futuro y CVV de 3 dígitos. Solo se guardan marca y últimos 4.

Documentos: `docs/PRUEBAS.md` y `docs/TerraNova.postman_collection.json`.

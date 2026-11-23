# Pruebas manuales (resumen)

| # | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| 1 | Registro correcto | `/registro` con correo válido | 201 genérico; llega correo con enlace; rol USUARIO |
| 2 | Registro con correo cualquiera | `usuario@empresa.mx` | Se acepta (ya no hay regla de dominio) |
| 3 | Registro correo ya existente | repetir correo verificado | Misma respuesta 201 genérica; no se revela que existe |
| 4 | Contraseña corta | password de 5 caracteres | 400 «al menos 8 caracteres» |
| 5 | Login sin verificar | login antes del enlace | 403 + `requiereVerificacion`; la web lleva a `/verificar` |
| 6 | Verificar | abrir enlace del correo | Página de éxito; login permitido |
| 7 | Reusar enlace | abrir el mismo enlace otra vez | Error «no es válido o ya fue utilizado» |
| 8 | Enlace vencido | en MySQL poner `token_expira` en el pasado | Error «venció» |
| 9 | Reenvío con límite | pulsar «Reenviar» dos veces seguidas | 2.º intento: 429 «Espera N segundos» |
| 10 | Rutas por rol | USUARIO abre `/hoteles`, `/usuarios` | Redirige a `/inicio?denegado` (API: 403) |
| 11 | Admin crea admin | `/usuarios` → crear ADMINISTRADOR | Cuenta verificada con ese rol |
| 12 | Desactivar | desactivar un usuario conectado | Su siguiente petición pierde acceso; login → 403 |
| 13 | Reserva por chat | «Quiero reservar…» y confirmar «sí» | Estado Pendiente_Pago + enlace «Pagar ahora» |
| 14 | Pago aprobado | `/pago/{folio}` con 4242… | Reservación Confirmada; llega correo de recibo con folio y últimos 4 |
| 15 | Pago rechazado | tarjeta 4000 0000 0000 0002 | Mensaje de rechazo; reservación Cancelada y habitación libre |
| 16 | Tarjeta inválida | número que falla Luhn / vencida / CVV de 2 dígitos | Error de validación; no se crea pago |
| 17 | Retención vencida | esperar 15 min (o bajar `fecha_registro`) | La tarea cancela la reservación; `/pago/{folio}` indica que ya no se puede pagar |
| 18 | Pago ajeno | otro USUARIO abre `/pago/{folio}` | Redirige a «Mis reservaciones» con aviso; el POST devuelve 404 |
| 19 | Check-out pagado | admin → Check-out de reservación pagada | Muestra «Pagado en línea» y solo cobra cargos extra |
| 20 | Privacidad de tarjeta | revisar tabla `pago` y logs | Solo `marca` y `ultimos4`; sin número completo ni CVV |
| 21 | Móvil 360–430 px | DevTools, iPhone SE/14 | Sin scroll horizontal; botones ≥ 44 px; chat a pantalla casi completa |
| 22 | Video portada | bajar en `/inicio` | El video se ensancha y se desvanece; funciona en móvil |

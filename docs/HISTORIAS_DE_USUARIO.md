# Historias de usuario implementadas — Hotel TerraNova

Cada historia sigue la cadena que pide la práctica:
**necesidad del usuario → historia de usuario → criterios → regla de negocio → consulta SQL → interfaz funcional.**

Modelo de datos: `HOTEL 1:N HABITACION N:1 TIPO_HABITACION` y `HABITACION 1:N ESTANCIA`.

---

## Historia 1 — Búsqueda avanzada y filtrado de disponibilidad (Cliente)

**Historia.** Como cliente, quiero buscar habitaciones por ciudad, hotel, tipo, capacidad, presupuesto y fechas, para elegir la que mejor se ajusta a mi viaje.

**Interfaz:** `/busqueda` — tarjetas con tipo, hotel, capacidad, precio por noche y total estimado.

**Consulta SQL**
```sql
SELECT ha.id_habitacion, h.nombre AS hotel, h.ciudad, ha.numero AS habitacion, ha.piso,
       t.nombre AS tipo, t.descripcion, t.capacidad, t.precio_base AS precio
FROM habitacion ha
INNER JOIN hotel h ON ha.id_hotel = h.id_hotel
INNER JOIN tipo_habitacion t ON ha.id_tipo = t.id_tipo
WHERE ha.estado = 'Disponible' AND h.activo = TRUE
  AND (:ciudad = '' OR h.ciudad = :ciudad)
  AND (:idHotel = 0 OR h.id_hotel = :idHotel)
  AND (:idTipo = 0 OR t.id_tipo = :idTipo)
  AND (:capacidad = 0 OR t.capacidad >= :capacidad)
  AND (:precioMax < 0 OR t.precio_base <= :precioMax)
ORDER BY t.precio_base, h.nombre, ha.numero;
```

**Criterios de aceptación**
- Solo se muestran habitaciones en estado Disponible de hoteles activos.
- Se puede filtrar por ciudad, hotel, tipo, capacidad mínima y precio máximo por noche.
- Si se indican fechas de llegada y salida, cada resultado muestra el total estimado (precio × noches).
- Los resultados se pueden ordenar por precio, capacidad u hotel.
- Cada resultado permite pasar al check-in en recepción.

**Criterios funcionales:** filtros opcionales combinables; contador de resultados; vista de la consulta SQL; botón para limpiar filtros.

**Manejo de errores**
- Sin resultados: «No encontramos habitaciones con esos criterios» con sugerencia de ampliar filtros.
- Fecha de llegada anterior a hoy, salida no posterior a la llegada, o una sola fecha capturada: mensaje de aviso y no se ejecuta la búsqueda.
- Error de conexión con la base de datos: mensaje amigable.

**Criterios no funcionales:** precios con formato monetario; tarjetas legibles y adaptables a móvil; respuesta inmediata.

**Reglas de negocio:** una habitación solo se ofrece si está Disponible; los hoteles inactivos no se ofrecen; el total estimado usa la tarifa base del tipo.

---

## Historia 2 — Registrar check-in (Recepcionista)

**Historia.** Como recepcionista, quiero registrar la llegada del huésped, para asignarle una habitación y cambiarla a estado Ocupada.

**Interfaz:** `/recepcion/checkin`.

**Operación SQL**
```sql
INSERT INTO estancia (id_habitacion, huesped, identificacion, num_huespedes, fecha_entrada,
                      fecha_salida_prevista, precio_noche) VALUES (...);
UPDATE habitacion SET estado = 'Ocupada' WHERE id_habitacion = ? AND estado = 'Disponible';
```

**Criterios de aceptación**
- Solo se pueden asignar habitaciones Disponibles de hoteles activos (lista desplegable).
- Se captura nombre, identificación, número de huéspedes y salida prevista.
- Al confirmar, la habitación pasa a Ocupada y el huésped aparece en Check-out.
- Se muestra mensaje de éxito.

**Manejo de errores:** nombre o identificación vacíos; número de huéspedes menor a 1 o mayor que la capacidad del tipo; salida prevista vacía o no posterior a hoy; habitación no disponible o con huésped ya registrado; hotel inactivo.

**Criterios no funcionales:** mensajes de validación junto a cada campo; fecha mínima en el selector; la tarifa se congela al momento del check-in.

**Reglas de negocio:** solo una habitación Disponible puede ocuparse; los huéspedes no pueden exceder la capacidad del tipo; un hotel inactivo no admite check-in.

---

## Historia 3 — Registrar check-out (Recepcionista)

**Historia.** Como recepcionista, quiero liquidar la estancia y cerrar el folio, para liberar la habitación y enviarla a limpieza.

**Interfaz:** `/recepcion/checkout` (lista de huéspedes), `/recepcion/checkout/{id}` (liquidación) y `/recepcion/estancias/{id}` (folio imprimible).

**Operación SQL**
```sql
SELECT h.nombre, ha.numero, e.huesped, e.fecha_entrada, e.fecha_salida_prevista, e.precio_noche
FROM estancia e
INNER JOIN habitacion ha ON e.id_habitacion = ha.id_habitacion
INNER JOIN hotel h ON ha.id_hotel = h.id_hotel
WHERE e.fecha_salida_real IS NULL;

UPDATE estancia SET fecha_salida_real = NOW(), noches = ?, cargos_extra = ?, total = ?, metodo_pago = ? WHERE id_estancia = ?;
UPDATE habitacion SET estado = 'Mantenimiento' WHERE id_habitacion = ?;
```

**Criterios de aceptación**
- Se listan los huéspedes hospedados, marcando las salidas vencidas.
- Se calcula el hospedaje (noches × tarifa, mínimo 1 noche) y se pueden añadir cargos adicionales; el total se actualiza en pantalla.
- Se elige la forma de pago (Efectivo, Tarjeta, Transferencia).
- Al confirmar se cierra el folio y la habitación pasa a Mantenimiento (limpieza).
- Se muestra el historial de los últimos check-outs y el folio imprimible.

**Manejo de errores:** cargos negativos o con formato inválido; forma de pago no permitida; check-out repetido sobre la misma estancia; estancia inexistente (regresa al listado con mensaje).

**Reglas de negocio:** el total = noches × tarifa congelada + cargos; una estancia cerrada no puede cerrarse otra vez; la habitación liberada no se ofrece hasta marcarla Disponible.

---

## Historia 4 — Gestionar estados físicos de la habitación (Recepcionista)

**Historia.** Como recepcionista, quiero cambiar manualmente una habitación a «Mantenimiento» por averías o regresarla a «Disponible», para que el inventario refleje su condición real.

**Interfaz:** `/recepcion/estados` — tabla con filtros por estado y buscador.

**Operación SQL**
```sql
UPDATE habitacion SET estado = 'Mantenimiento' WHERE id_habitacion = ? AND estado = 'Disponible';
UPDATE habitacion SET estado = 'Disponible'    WHERE id_habitacion = ? AND estado = 'Mantenimiento';
```

**Criterios de aceptación**
- Una habitación Disponible puede enviarse a Mantenimiento con un clic.
- Una habitación en Mantenimiento puede marcarse Disponible.
- El estado se muestra con colores (verde, rojo, ámbar) y se puede filtrar.
- Una habitación enviada a mantenimiento deja de aparecer en la búsqueda de la Historia 1.

**Manejo de errores:** intentar enviar a mantenimiento una habitación Ocupada (se pide check-out primero) o que ya está en mantenimiento; marcar Disponible una habitación que no está en mantenimiento; hotel inactivo; habitación inexistente.

**Reglas de negocio:** Ocupada → solo cambia con check-out; los hoteles inactivos no admiten cambios operativos; solo existen los estados Disponible, Ocupada y Mantenimiento.

---

## Historia 5 — Dashboard ejecutivo (Administrador)

**Historia.** Como administrador, quiero visualizar la ocupación global y el costo de oportunidad por mantenimiento, para tomar decisiones operativas.

**Interfaz:** `/ejecutivo` — KPIs, barra de distribución de la cadena, ocupación por tipo y desempeño por hotel; se refresca solo cada 60 s.

**Consulta SQL**
```sql
SELECT h.nombre AS hotel,
       COUNT(ha.id_habitacion) AS total,
       SUM(CASE WHEN ha.estado = 'Disponible'    THEN 1 ELSE 0 END) AS disponibles,
       SUM(CASE WHEN ha.estado = 'Ocupada'       THEN 1 ELSE 0 END) AS ocupadas,
       SUM(CASE WHEN ha.estado = 'Mantenimiento' THEN 1 ELSE 0 END) AS mantenimiento,
       SUM(CASE WHEN ha.estado = 'Ocupada'       THEN t.precio_base ELSE 0 END) AS ingreso,
       SUM(CASE WHEN ha.estado = 'Mantenimiento' THEN t.precio_base ELSE 0 END) AS costo_oportunidad
FROM hotel h
LEFT JOIN habitacion ha ON ha.id_hotel = h.id_hotel
LEFT JOIN tipo_habitacion t ON ha.id_tipo = t.id_tipo
GROUP BY h.id_hotel, h.nombre
ORDER BY h.nombre;
```

**Criterios de aceptación**
- Muestra el porcentaje de ocupación global, el ingreso diario en curso, el costo de oportunidad diario y lo cobrado en el mes.
- Muestra por hotel la distribución Disponible/Ocupada/Mantenimiento y su porcentaje de ocupación.
- Muestra la ocupación por tipo de habitación.
- Los hoteles inactivos aparecen atenuados y no suman a los KPIs.

**Manejo de errores:** sin hoteles o sin habitaciones: mensajes informativos (sin divisiones entre cero); error de conexión: mensaje amigable.

**Criterios no funcionales:** gráficas con barras CSS (sin dependencias externas); importes con formato monetario; se actualiza automáticamente.

**Reglas de negocio:** *costo de oportunidad* = suma de la tarifa base de las habitaciones en Mantenimiento; *ocupación* = ocupadas ÷ total de habitaciones de hoteles activos.

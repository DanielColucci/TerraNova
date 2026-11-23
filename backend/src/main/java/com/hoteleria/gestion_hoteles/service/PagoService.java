package com.hoteleria.gestion_hoteles.service;

import com.hoteleria.gestion_hoteles.entity.EstadoPago;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Pago;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.exception.RecursoNoEncontradoException;
import com.hoteleria.gestion_hoteles.exception.ReglaNegocioException;
import com.hoteleria.gestion_hoteles.repository.PagoRepository;
import com.hoteleria.gestion_hoteles.repository.ReservacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pago SIMULADO. Tarjetas de prueba: 4242 4242 4242 4242 aprueba; 4000 0000 0000 0002 rechaza
 * (cualquier otra tarjeta válida por Luhn también aprueba). Nunca se guarda el número completo ni el CVV.
 * La habitación queda retenida mientras la reservación está Pendiente_Pago (la disponibilidad solo ignora las canceladas).
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);
    private static final String TARJETA_RECHAZADA = "4000000000000002";
    private static final Pattern VENCIMIENTO = Pattern.compile("^(0[1-9]|1[0-2])\\s*/\\s*(\\d{2})$");

    public record Resultado(boolean aprobado, String mensaje, Pago pago, Reservacion reservacion) { }

    private final ReservacionRepository reservacionRepository;
    private final PagoRepository pagoRepository;
    private final long minutosRetencion;
    private final SecureRandom random = new SecureRandom();

    public PagoService(ReservacionRepository reservacionRepository, PagoRepository pagoRepository,
                       @Value("${app.pago.minutos-retencion:15}") long minutosRetencion) {
        this.reservacionRepository = reservacionRepository;
        this.pagoRepository = pagoRepository;
        this.minutosRetencion = minutosRetencion;
    }

    public long getMinutosRetencion() { return minutosRetencion; }

    /** Reservación con detalle SOLO si es de la cuenta (si no, 404: no se revela que existe). */
    @Transactional(readOnly = true)
    public Reservacion propia(Long folio, Long idUsuario) {
        Reservacion r = folio == null ? null : reservacionRepository.findByIdConDetalle(folio).orElse(null);
        if (r == null || idUsuario == null || !idUsuario.equals(r.getIdUsuario())) {
            throw new RecursoNoEncontradoException("No encontramos esa reservación en tu cuenta.", "/mis-reservaciones");
        }
        return r;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Pago> pagoAprobado(Long folio) {
        return pagoRepository.findFirstByReservacion_IdReservacionAndEstado(folio, EstadoPago.Aprobado);
    }

    public LocalDateTime venceEn(Reservacion r) {
        return r.getFechaRegistro().plusMinutes(minutosRetencion);
    }

    public long segundosRestantes(Reservacion r) {
        if (r.getEstado() != EstadoReservacion.Pendiente_Pago) { return 0; }
        return Math.max(0, Duration.between(LocalDateTime.now(), venceEn(r)).getSeconds());
    }

    /** Valida la tarjeta falsa, registra el pago y confirma o cancela la reservación. */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public Resultado pagar(Long folio, Long idUsuario, String titular, String numero, String vencimiento, String cvv) {
        Reservacion r = propia(folio, idUsuario);
        if (r.getEstado() != EstadoReservacion.Pendiente_Pago) {
            throw new ReglaNegocioException("La reservación #" + folio + " ya no está pendiente de pago (estado: "
                    + r.getEstado().name().replace('_', ' ') + ").");
        }
        if (segundosRestantes(r) <= 0) {
            r.setEstado(EstadoReservacion.Cancelada);
            reservacionRepository.save(r);
            throw new ReglaNegocioException("El tiempo para pagar venció: la reservación se canceló y la habitación se liberó.");
        }
        // ---- validación (el servidor no confía en el navegador)
        String nombre = titular == null ? "" : titular.trim();
        if (nombre.length() < 2 || nombre.length() > 120) {
            throw new ReglaNegocioException("Escribe el nombre del titular tal como aparece en la tarjeta.");
        }
        String digitos = numero == null ? "" : numero.replaceAll("[\\s-]", "");
        if (!digitos.matches("\\d{13,19}") || !luhn(digitos)) {
            throw new ReglaNegocioException("El número de tarjeta no es válido.");
        }
        Matcher m = VENCIMIENTO.matcher(vencimiento == null ? "" : vencimiento.trim());
        if (!m.matches() || YearMonth.of(2000 + Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))).isBefore(YearMonth.now())) {
            throw new ReglaNegocioException("La fecha de vencimiento no es válida o la tarjeta está vencida (usa MM/AA).");
        }
        if (cvv == null || !cvv.trim().matches("\\d{3,4}")) {
            throw new ReglaNegocioException("El CVV debe tener 3 o 4 dígitos.");
        }

        boolean aprobada = !TARJETA_RECHAZADA.equals(digitos);
        Pago p = new Pago();
        p.setReservacion(r);
        p.setMonto(r.getCostoTotal());
        p.setMarca(marca(digitos));
        p.setUltimos4(digitos.substring(digitos.length() - 4));   // solo los últimos 4
        p.setTitular(nombre);
        p.setEstado(aprobada ? EstadoPago.Aprobado : EstadoPago.Rechazado);
        p.setReferencia(referencia());
        pagoRepository.save(p);

        if (aprobada) {
            r.setEstado(EstadoReservacion.Confirmada);
            log.info("[PAGO] reservación #{} pagada ({} ****{})", folio, p.getMarca(), p.getUltimos4());
            reservacionRepository.save(r);
            return new Resultado(true, "Pago aprobado. Tu reservación está confirmada.", p, r);
        }
        r.setEstado(EstadoReservacion.Cancelada);   // se libera la habitación
        reservacionRepository.save(r);
        log.info("[PAGO] reservación #{} rechazada ({} ****{}); habitación liberada", folio, p.getMarca(), p.getUltimos4());
        return new Resultado(false, "El banco rechazó la tarjeta. La reservación se canceló y la habitación se liberó.", p, r);
    }

    /**
     * Cobro en MOSTRADOR (recepción): registra un pago Aprobado por el total de la reservación y la deja Confirmada.
     * Se une a la transacción del check-in (si algo falla después, el pago también se revierte).
     */
    @Transactional
    public Pago cobrarEnMostrador(Reservacion r, com.hoteleria.gestion_hoteles.entity.MetodoPago metodo) {
        if (metodo == null) {
            throw new ReglaNegocioException("Selecciona la forma de pago con la que se cobra en mostrador.");
        }
        if (r.getEstado() != EstadoReservacion.Pendiente && r.getEstado() != EstadoReservacion.Pendiente_Pago) {
            throw new ReglaNegocioException("La reservación #" + r.getIdReservacion() + " no tiene pago pendiente (estado: "
                    + r.getEstado().name().replace('_', ' ') + ").");
        }
        Pago p = new Pago();
        p.setReservacion(r);
        p.setMonto(r.getCostoTotal());
        p.setMetodo(metodo.name().toUpperCase());
        p.setMarca("Mostrador");
        p.setUltimos4("----");
        String titular = r.getNombreHuesped() == null ? "Huésped" : r.getNombreHuesped();
        p.setTitular(titular.length() > 120 ? titular.substring(0, 120) : titular);
        p.setEstado(EstadoPago.Aprobado);
        p.setReferencia(referencia());
        pagoRepository.save(p);
        r.setEstado(EstadoReservacion.Confirmada);
        reservacionRepository.save(r);
        log.info("[PAGO] reservación #{} cobrada en mostrador ({})", r.getIdReservacion(), metodo);
        return p;
    }

    /** Cada minuto: cancela las Pendiente_Pago cuya retención ya venció y libera la habitación. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public int liberarVencidas() {
        try {
            List<Reservacion> vencidas = reservacionRepository.findByEstadoAndFechaRegistroBefore(
                    EstadoReservacion.Pendiente_Pago, LocalDateTime.now().minusMinutes(minutosRetencion));
            vencidas.forEach(r -> r.setEstado(EstadoReservacion.Cancelada));
            if (!vencidas.isEmpty()) {
                reservacionRepository.saveAll(vencidas);
                log.info("[PAGO] {} reservación(es) sin pagar canceladas y habitaciones liberadas", vencidas.size());
            }
            return vencidas.size();
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("[PAGO] no se pudieron liberar las vencidas: {}", e.getMessage());
            return 0;
        }
    }

    // ------------------------------------------------------------------ utilidades

    public static boolean luhn(String digitos) {
        int suma = 0;
        boolean doblar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int d = digitos.charAt(i) - '0';
            if (doblar) { d *= 2; if (d > 9) { d -= 9; } }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    public static String marca(String d) {
        if (d.startsWith("4")) { return "Visa"; }
        if (d.matches("^(5[1-5]|2[2-7]).*")) { return "Mastercard"; }
        if (d.startsWith("34") || d.startsWith("37")) { return "Amex"; }
        return "Tarjeta";
    }

    private String referencia() {
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("TN-").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"))).append('-');
        for (int i = 0; i < 6; i++) { sb.append(alfabeto.charAt(random.nextInt(alfabeto.length()))); }
        return sb.toString();
    }
}

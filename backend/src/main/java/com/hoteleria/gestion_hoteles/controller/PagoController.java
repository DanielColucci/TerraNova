package com.hoteleria.gestion_hoteles.controller;

import com.hoteleria.gestion_hoteles.correo.EmailService;
import com.hoteleria.gestion_hoteles.entity.EstadoReservacion;
import com.hoteleria.gestion_hoteles.entity.Reservacion;
import com.hoteleria.gestion_hoteles.seguridad.Seguridad;
import com.hoteleria.gestion_hoteles.seguridad.UsuarioActual;
import com.hoteleria.gestion_hoteles.service.PagoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** API REST de pago simulado. Solo la cuenta dueña de la reservación puede consultarla y pagarla. */
@RestController
@RequestMapping("/api/pago")
public class PagoController {

    public record PagoRequest(String titular, String numero, String vencimiento, String cvv) { }

    private final PagoService pagoService;
    private final EmailService emailService;

    public PagoController(PagoService pagoService, EmailService emailService) {
        this.pagoService = pagoService;
        this.emailService = emailService;
    }

    @GetMapping("/{folio}")
    public Map<String, Object> ver(@PathVariable Long folio) {
        UsuarioActual yo = Seguridad.actual().orElseThrow();
        Reservacion r = pagoService.propia(folio, yo.id());
        long restante = pagoService.segundosRestantes(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservacion", ApiMapper.reservacion(r));
        m.put("restante", restante);
        m.put("minutos", pagoService.getMinutosRetencion());
        m.put("pendiente", r.getEstado() == EstadoReservacion.Pendiente_Pago && restante > 0);
        m.put("pago", ApiMapper.pago(pagoService.pagoAprobado(folio).orElse(null)));
        return m;
    }

    /** Procesa el pago simulado (JSON). El número completo y CVV nunca se guardan ni se registran. */
    @PostMapping("/{folio}/procesar")
    public Map<String, Object> procesar(@PathVariable Long folio, @RequestBody PagoRequest req) {
        UsuarioActual yo = Seguridad.actual().orElseThrow();
        PagoService.Resultado res = pagoService.pagar(folio, yo.id(),
                req.titular(), req.numero(), req.vencimiento(), req.cvv());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aprobado", res.aprobado());
        m.put("mensaje", res.mensaje());
        m.put("referencia", res.pago().getReferencia());
        m.put("estado", res.reservacion().getEstado().name());
        if (res.aprobado()) {
            Reservacion r = res.reservacion();
            emailService.enviarRecibo(new EmailService.Recibo(yo.correo(), yo.nombre(), r.getIdReservacion(),
                    r.getHabitacion().getHotel().getNombre(),
                    r.getHabitacion().getNumero() + " · " + r.getHabitacion().getTipo().getNombre(),
                    r.getFechaEntrada(), r.getFechaSalida(), r.getNoches(), r.getCostoTotal(),
                    res.pago().getMarca(), res.pago().getUltimos4(), res.pago().getReferencia(), res.pago().getFecha()));
        }
        return m;
    }
}

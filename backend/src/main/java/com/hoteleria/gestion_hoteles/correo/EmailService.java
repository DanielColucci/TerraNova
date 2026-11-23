package com.hoteleria.gestion_hoteles.correo;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Envío ASÍNCRONO de correos HTML con la identidad de TerraNova. Si el SMTP falla, solo se registra el error:
 * el registro / el pago nunca se rompen por culpa del correo (el usuario puede pedir un reenvío).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public record Recibo(String correo, String nombre, Long folio, String hotel, String habitacion,
                         LocalDate entrada, LocalDate salida, int noches, BigDecimal total,
                         String marca, String ultimos4, String referencia, LocalDateTime fecha) { }

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine plantillas;
    private final String remitente;
    private final String nombreRemitente;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine plantillas,
                        @Value("${app.mail.from}") String remitente,
                        @Value("${app.mail.nombre:TerraNova}") String nombreRemitente,
                        @Value("${app.frontend-url:${app.base-url:http://localhost:5500}}") String frontendUrl) {
        this.mailSender = mailSender;
        this.plantillas = plantillas;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public String enlaceVerificacion(String token) {
        return frontendUrl + "/auth/verificar?token=" + token;
    }

    @Async
    public void enviarVerificacion(String nombre, String correo, String token) {
        Context ctx = new Context();
        ctx.setVariable("nombre", nombre);
        ctx.setVariable("enlace", enlaceVerificacion(token));
        enviar(correo, "Confirma tu correo · TerraNova", "email/verificacion", ctx);
    }

    @Async
    public void enviarRecibo(Recibo r) {
        Context ctx = new Context();
        ctx.setVariable("r", r);
        ctx.setVariable("enlace", frontendUrl + "/mis-reservaciones");
        enviar(r.correo(), "Recibo de pago · Reservación #" + r.folio() + " · TerraNova", "email/recibo", ctx);
    }

    private void enviar(String para, String asunto, String plantilla, Context ctx) {
        try {
            String html = plantillas.process(plantilla, ctx);
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(new InternetAddress(remitente, nombreRemitente, "UTF-8"));
            h.setTo(para);
            h.setSubject(asunto);
            h.setText(html, true);
            mailSender.send(msg);
            log.info("[CORREO] enviado '{}' a {}", asunto, para);
        } catch (Exception e) {
            // No se propaga: el flujo que lo pidió ya terminó. Se ofrece «Reenviar correo» al usuario.
            log.error("[CORREO] no se pudo enviar '{}' a {}: {}", asunto, para, e.toString());
        }
    }
}

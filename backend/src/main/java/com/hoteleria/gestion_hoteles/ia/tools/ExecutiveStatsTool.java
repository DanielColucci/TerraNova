package com.hoteleria.gestion_hoteles.ia.tools;

import com.hoteleria.gestion_hoteles.dto.EjecutivoResumen;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.Ejecutivo;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.EjecutivoHotel;
import com.hoteleria.gestion_hoteles.ia.dto.IaDtos.EjecutivoTipo;
import com.hoteleria.gestion_hoteles.ia.service.ToolAuditoria;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Historia 5: dashboard ejecutivo. Reutiliza ConsultaService.ejecutivo() (misma consulta que el panel del gerente),
 * así las cifras coinciden con la pantalla. Solo lectura.
 */
@Component
public class ExecutiveStatsTool {

    private final ConsultaService consultaService;
    private final ToolAuditoria auditoria;

    public ExecutiveStatsTool(ConsultaService consultaService, ToolAuditoria auditoria) {
        this.consultaService = consultaService;
        this.auditoria = auditoria;
    }

    @Tool(description = """
            Devuelve las métricas del dashboard ejecutivo: ocupación global (solo hoteles activos), habitaciones
            disponibles/ocupadas/en mantenimiento, ingreso diario en curso, costo de oportunidad por mantenimiento,
            ingreso potencial, ingresos cobrados en el mes, estancias activas, y el detalle por hotel y por tipo de habitación.
            Úsala para «cuál es la ocupación», «cuánto ingreso diario tenemos», «cuál es el costo de oportunidad»,
            «qué hotel tiene mayor ocupación» o «qué tipo de habitación se ocupa más».
            El costo de oportunidad es la suma del precio base por noche de las habitaciones en mantenimiento.
            """)
    public Ejecutivo consultarDashboardEjecutivo() {
        auditoria.registrar("consultarDashboardEjecutivo", "");
        EjecutivoResumen r = consultaService.ejecutivo();

        List<EjecutivoHotel> hoteles = r.getHoteles().stream()
                .map(f -> new EjecutivoHotel(f.getHotel(), f.getCiudad(), f.isActivo(), f.getTotal(),
                        f.getDisponibles(), f.getOcupadas(), f.getMantenimiento(), f.getPctOcupacion(),
                        f.getIngreso(), f.getCosto()))
                .toList();
        List<EjecutivoTipo> tipos = r.getTipos().stream()
                .map(t -> new EjecutivoTipo(t.getTipo(), t.getCapacidad(), t.getPrecio(), t.getTotal(),
                        t.getOcupadas(), t.getMantenimiento(), t.getPctOcupacion()))
                .toList();

        return new Ejecutivo("Los totales globales consideran solo hoteles activos",
                r.getTotal(), r.getDisponibles(), r.getOcupadas(), r.getMantenimiento(),
                r.getPctOcupacion(), r.getPctDisponible(), r.getPctMantenimiento(),
                r.getIngresoDiario(), r.getCostoOportunidad(), r.getIngresoPotencial(), r.getPctCosto(),
                r.getIngresosMes(), r.getEstanciasActivas(), hoteles, tipos);
    }
}

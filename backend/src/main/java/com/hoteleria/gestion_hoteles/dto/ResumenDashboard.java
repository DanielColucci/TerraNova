package com.hoteleria.gestion_hoteles.dto;

/** Indicadores (KPIs) del panel principal, calculados desde la base de datos. */
public class ResumenDashboard {

    private final long hotelesActivos;
    private final long hotelesTotal;
    private final long tiposTotal;
    private final long habitacionesTotal;
    private final long disponibles;
    private final long ocupadas;
    private final long mantenimiento;

    public ResumenDashboard(long hotelesActivos, long hotelesTotal, long tiposTotal,
                            long habitacionesTotal, long disponibles, long ocupadas, long mantenimiento) {
        this.hotelesActivos = hotelesActivos;
        this.hotelesTotal = hotelesTotal;
        this.tiposTotal = tiposTotal;
        this.habitacionesTotal = habitacionesTotal;
        this.disponibles = disponibles;
        this.ocupadas = ocupadas;
        this.mantenimiento = mantenimiento;
    }

    public long getHotelesActivos() { return hotelesActivos; }
    public long getHotelesTotal() { return hotelesTotal; }
    public long getTiposTotal() { return tiposTotal; }
    public long getHabitacionesTotal() { return habitacionesTotal; }
    public long getDisponibles() { return disponibles; }
    public long getOcupadas() { return ocupadas; }
    public long getMantenimiento() { return mantenimiento; }
}

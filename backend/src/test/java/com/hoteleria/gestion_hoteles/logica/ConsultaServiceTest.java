package com.hoteleria.gestion_hoteles.logica;

import com.hoteleria.gestion_hoteles.dto.BusquedaView;
import com.hoteleria.gestion_hoteles.dto.EjecutivoHotelView;
import com.hoteleria.gestion_hoteles.dto.EjecutivoResumen;
import com.hoteleria.gestion_hoteles.dto.EjecutivoTipoView;
import com.hoteleria.gestion_hoteles.dto.ResultadoBusqueda;
import com.hoteleria.gestion_hoteles.repository.ConsultaRepository;
import com.hoteleria.gestion_hoteles.repository.EstanciaRepository;
import com.hoteleria.gestion_hoteles.repository.HabitacionRepository;
import com.hoteleria.gestion_hoteles.repository.HotelRepository;
import com.hoteleria.gestion_hoteles.repository.TipoHabitacionRepository;
import com.hoteleria.gestion_hoteles.service.ConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Historia 1 (búsqueda de disponibilidad) e Historia 5 (dashboard ejecutivo): lógica de la capa de servicio. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Búsqueda y dashboard ejecutivo")
class ConsultaServiceTest {

    @Mock ConsultaRepository consultaRepository;
    @Mock HotelRepository hotelRepository;
    @Mock TipoHabitacionRepository tipoRepository;
    @Mock HabitacionRepository habitacionRepository;
    @Mock EstanciaRepository estanciaRepository;

    ConsultaService service;

    @BeforeEach
    void preparar() {
        service = new ConsultaService(consultaRepository, hotelRepository, tipoRepository,
                habitacionRepository, estanciaRepository);
    }

    private BusquedaView fila(String hotel, String numero, String precio) {
        BusquedaView v = mock(BusquedaView.class);
        lenient().when(v.getIdhabitacion()).thenReturn(1L);
        lenient().when(v.getHotel()).thenReturn(hotel);
        lenient().when(v.getCiudad()).thenReturn("Toluca");
        lenient().when(v.getHabitacion()).thenReturn(numero);
        lenient().when(v.getPiso()).thenReturn(1);
        lenient().when(v.getTipo()).thenReturn("Doble");
        lenient().when(v.getDescripcion()).thenReturn("Cama king");
        lenient().when(v.getCapacidad()).thenReturn(2);
        lenient().when(v.getPrecio()).thenReturn(new BigDecimal(precio));
        return v;
    }

    private EjecutivoHotelView hotelView(String nombre, int activo, long total, long disp, long ocup, long mant,
                                         String ingreso, String costo, String potencial) {
        EjecutivoHotelView v = mock(EjecutivoHotelView.class);
        lenient().when(v.getHotel()).thenReturn(nombre);
        lenient().when(v.getCiudad()).thenReturn("Toluca");
        lenient().when(v.getActivo()).thenReturn(activo);
        lenient().when(v.getTotal()).thenReturn(total);
        lenient().when(v.getDisponibles()).thenReturn(disp);
        lenient().when(v.getOcupadas()).thenReturn(ocup);
        lenient().when(v.getMantenimiento()).thenReturn(mant);
        lenient().when(v.getIngreso()).thenReturn(new BigDecimal(ingreso));
        lenient().when(v.getCosto()).thenReturn(new BigDecimal(costo));
        lenient().when(v.getPotencial()).thenReturn(new BigDecimal(potencial));
        return v;
    }

    // ------------------------------------------------------------ HISTORIA 1: búsqueda

    @Test
    @DisplayName("Búsqueda sin filtros: null se traduce a los valores 'sin filtro' del SQL ('' / 0 / -1)")
    void busquedaSinFiltros() {
        when(consultaRepository.busquedaDisponibilidad(eq(""), eq(0L), eq(0L), eq(0), any(BigDecimal.class)))
                .thenReturn(List.of());

        List<ResultadoBusqueda> r = service.buscarDisponibilidad(null, null, null, null, null, null, null);

        assertThat(r).isEmpty();
        verify(consultaRepository).busquedaDisponibilidad("", 0L, 0L, 0, BigDecimal.valueOf(-1));
    }

    @Test
    @DisplayName("Búsqueda: filtros negativos se tratan como 'sin filtro' y la ciudad se recorta")
    void busquedaFiltrosInvalidos() {
        when(consultaRepository.busquedaDisponibilidad(anyString(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(List.of());

        service.buscarDisponibilidad("  Toluca ", null, null, -5, new BigDecimal("-10"), null, null);

        verify(consultaRepository).busquedaDisponibilidad("Toluca", 0L, 0L, 0, BigDecimal.valueOf(-1));
    }

    @Test
    @DisplayName("Búsqueda con fechas: total estimado = precio × noches")
    void busquedaConFechas() {
        when(consultaRepository.busquedaDisponibilidad(anyString(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(List.of(fila("TerraNova Centro", "101", "1200.00")));
        LocalDate entrada = LocalDate.now().plusDays(1);

        List<ResultadoBusqueda> r = service.buscarDisponibilidad(null, null, null, null, null,
                entrada, entrada.plusDays(3));

        assertThat(r).hasSize(1);
        assertThat(r.get(0).getTotalEstimado()).isEqualByComparingTo("3600.00");
    }

    @Test
    @DisplayName("Búsqueda sin fechas: no hay total estimado")
    void busquedaSinFechas() {
        when(consultaRepository.busquedaDisponibilidad(anyString(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(List.of(fila("TerraNova Centro", "101", "1200.00")));

        List<ResultadoBusqueda> r = service.buscarDisponibilidad(null, null, null, null, null, null, null);

        assertThat(r.get(0).getTotalEstimado()).isNull();
    }

    // ------------------------------------------------------------ HISTORIA 5: dashboard ejecutivo

    @Test
    @DisplayName("Dashboard: los KPIs globales solo suman hoteles activos; el detalle muestra todos")
    void dashboardSoloHotelesActivos() {
        when(consultaRepository.ejecutivoPorHotel()).thenReturn(List.of(
                hotelView("Activo A", 1, 10, 5, 3, 2, "3600.00", "2400.00", "12000.00"),
                hotelView("Activo B", 1, 10, 6, 4, 0, "4800.00", "0.00", "12000.00"),
                hotelView("Cerrado", 0, 8, 8, 0, 0, "0.00", "0.00", "9000.00")));
        when(estanciaRepository.ingresosDesde(any())).thenReturn(new BigDecimal("15000.00"));
        when(estanciaRepository.countByFechaSalidaRealIsNull()).thenReturn(7L);

        EjecutivoResumen r = service.ejecutivo();

        assertThat(r.getHoteles()).hasSize(3);                      // el detalle incluye al hotel inactivo
        assertThat(r.getTotal()).isEqualTo(20);                      // pero los KPIs no
        assertThat(r.getOcupadas()).isEqualTo(7);
        assertThat(r.getMantenimiento()).isEqualTo(2);
        assertThat(r.getIngresoDiario()).isEqualByComparingTo("8400.00");
        assertThat(r.getCostoOportunidad()).isEqualByComparingTo("2400.00");
        assertThat(r.getIngresoPotencial()).isEqualByComparingTo("24000.00");
        assertThat(r.getPctOcupacion()).isEqualTo(35);               // 7 de 20
        assertThat(r.getPctCosto()).isEqualTo(10);                   // 2400 de 24000
        assertThat(r.getIngresosMes()).isEqualByComparingTo("15000.00");
        assertThat(r.getEstanciasActivas()).isEqualTo(7);
    }

    @Test
    @DisplayName("Dashboard sin datos: porcentajes en 0 y sin dividir entre cero")
    void dashboardVacio() {
        when(consultaRepository.ejecutivoPorHotel()).thenReturn(List.of());
        when(estanciaRepository.ingresosDesde(any())).thenReturn(null);

        EjecutivoResumen r = service.ejecutivo();

        assertThat(r.getTotal()).isZero();
        assertThat(r.getPctOcupacion()).isZero();
        assertThat(r.getPctCosto()).isZero();
        assertThat(r.getIngresosMes()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Dashboard: el desglose por tipo convierte los números de la consulta")
    void dashboardPorTipo() {
        EjecutivoTipoView t = mock(EjecutivoTipoView.class);
        when(t.getTipo()).thenReturn("Suite");
        when(t.getCapacidad()).thenReturn(4);
        when(t.getPrecio()).thenReturn(new BigDecimal("2500.00"));
        when(t.getTotal()).thenReturn(4L);
        when(t.getOcupadas()).thenReturn(1L);
        when(t.getMantenimiento()).thenReturn(1L);
        when(consultaRepository.ejecutivoPorHotel()).thenReturn(List.of());
        when(consultaRepository.ejecutivoPorTipo()).thenReturn(List.of(t));

        EjecutivoResumen r = service.ejecutivo();

        assertThat(r.getTipos()).hasSize(1);
        assertThat(r.getTipos().get(0).getTipo()).isEqualTo("Suite");
        assertThat(r.getTipos().get(0).getPctOcupacion()).isEqualTo(25);
    }
}

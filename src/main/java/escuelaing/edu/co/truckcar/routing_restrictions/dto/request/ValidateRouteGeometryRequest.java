package escuelaing.edu.co.truckcar.routing_restrictions.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Validación sobre la geometría real de la ruta: la polilínea [latitud, longitud] que devuelve OSRM, no el nombre del
 * corredor. Una restricción aplica si su punto está a corridorMeters o menos de la ruta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateRouteGeometryRequest {

    private String vehiclePlate;

    @NotNull(message = "La altura total del camión en metros es obligatoria")
    @Positive(message = "La altura debe ser mayor que 0")
    private Double heightMeters;

    @NotNull(message = "El peso bruto total en toneladas es obligatorio")
    @PositiveOrZero(message = "El peso bruto no puede ser negativo")
    private Double grossWeightTons;

    /** Opcional: sin el peso por eje real, las restricciones por eje se devuelven como advertencia. */
    @PositiveOrZero(message = "El peso por eje no puede ser negativo")
    private Double axleWeightTons;

    @NotNull(message = "La ruta es obligatoria")
    @Size(min = 2, max = 2000, message = "La ruta debe tener entre 2 y 2000 puntos")
    private List<List<Double>> path;

    /** Distancia máxima de la restricción a la ruta. Por defecto 60 m. */
    @DecimalMin(value = "10", message = "El corredor debe ser de al menos 10 m")
    @DecimalMax(value = "500", message = "El corredor no puede superar 500 m")
    private Double corridorMeters;
}

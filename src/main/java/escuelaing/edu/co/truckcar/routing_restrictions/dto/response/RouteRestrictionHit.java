package escuelaing.edu.co.truckcar.routing_restrictions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una restricción sobre la ruta. kind dice por qué aplica: GALIBO_ALTURA, PESO_BRUTO o PESO_POR_EJE (el vehículo
 * excede el límite), BLOQUEO_TOTAL (cierre) o PRECAUCION.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRestrictionHit {
    private String kind;
    /** Metros de ruta desde el primer punto hasta la restricción. */
    private Double alongRouteMeters;
    /** Distancia de la restricción a la ruta. */
    private Double distanceFromRouteMeters;
    private String reason;
    private RoadRestrictionResponse restriction;
}

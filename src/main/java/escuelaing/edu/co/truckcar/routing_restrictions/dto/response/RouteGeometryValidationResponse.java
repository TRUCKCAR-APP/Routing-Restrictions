package escuelaing.edu.co.truckcar.routing_restrictions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Resultado sobre la geometría de la ruta, con las restricciones en el orden en que se encuentran. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteGeometryValidationResponse {
    private String vehiclePlate;
    private Boolean passable;
    private Double routeLengthMeters;
    /** Restricciones activas cerca de la ruta que se evaluaron. */
    private Integer restrictionsEvaluated;
    /** Lo que impide el paso de este vehículo. */
    private List<RouteRestrictionHit> blocking;
    /** Lo que no impide el paso pero el conductor debe saber. */
    private List<RouteRestrictionHit> warnings;
}

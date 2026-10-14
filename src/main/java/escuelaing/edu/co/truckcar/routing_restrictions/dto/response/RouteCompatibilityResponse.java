package escuelaing.edu.co.truckcar.routing_restrictions.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteCompatibilityResponse {
    private String vehiclePlate;
    private String roadCorridor;
    private Boolean isPassable;
    private List<String> blockingReasons;
    private List<String> warnings;
    private List<RoadRestrictionResponse> violatedRestrictions;
}
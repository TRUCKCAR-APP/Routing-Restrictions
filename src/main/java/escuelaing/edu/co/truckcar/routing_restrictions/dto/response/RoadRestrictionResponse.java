package escuelaing.edu.co.truckcar.routing_restrictions.dto.response;

import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionSeverity;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadRestrictionResponse {
    private Long id;
    private String restrictionCode;
    private String title;
    private String description;
    private RestrictionType restrictionType;
    private RestrictionSeverity severity;
    private String roadCorridor;
    private String department;
    private Double maxClearanceMeters;
    private Double maxGrossWeightTons;
    private Double maxAxleWeightTons;
    private Double latitude;
    private Double longitude;
    private LocalDateTime appliesFrom;
    private LocalDateTime appliesTo;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
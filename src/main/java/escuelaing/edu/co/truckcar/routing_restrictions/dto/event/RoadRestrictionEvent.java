package escuelaing.edu.co.truckcar.routing_restrictions.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadRestrictionEvent {
    private Long restrictionId;
    private String restrictionCode;
    private String title;
    private String restrictionType;
    private String severity;
    private String roadCorridor;
    private Double latitude;
    private Double longitude;
    private String eventAction; // CREATED, UPDATED, DEACTIVATED
    private LocalDateTime timestamp;
}
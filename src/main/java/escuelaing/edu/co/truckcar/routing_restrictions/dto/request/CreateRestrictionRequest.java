package escuelaing.edu.co.truckcar.routing_restrictions.dto.request;

import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionSeverity;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRestrictionRequest {

    private String restrictionCode;

    @NotBlank(message = "El título de la restricción es obligatorio")
    private String title;

    private String description;

    @NotNull(message = "El tipo de restricción es obligatorio")
    private RestrictionType restrictionType;

    @NotNull(message = "La severidad es obligatoria")
    private RestrictionSeverity severity;

    @NotBlank(message = "El corredor vial es obligatorio")
    private String roadCorridor;

    private String department;
    private Double maxClearanceMeters;
    private Double maxGrossWeightTons;
    private Double maxAxleWeightTons;
    private Double latitude;
    private Double longitude;
    private LocalDateTime appliesFrom;
    private LocalDateTime appliesTo;
}
package escuelaing.edu.co.truckcar.routing_restrictions.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateRouteCompatibilityRequest {

    @NotBlank(message = "La placa del vehículo es obligatoria")
    private String vehiclePlate;

    @NotBlank(message = "El corredor vial a validar es obligatorio")
    private String roadCorridor;

    @NotNull(message = "El peso bruto total en toneladas es obligatorio")
    private Double grossWeightTons;

    @NotNull(message = "La altura total del camión en metros es obligatoria")
    private Double heightMeters;

    private Double axleWeightTons;
}
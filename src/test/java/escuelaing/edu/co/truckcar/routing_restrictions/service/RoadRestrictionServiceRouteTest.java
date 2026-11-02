package escuelaing.edu.co.truckcar.routing_restrictions.service;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteGeometryRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteGeometryValidationResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteRestrictionHit;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionSeverity;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RoadRestriction;
import escuelaing.edu.co.truckcar.routing_restrictions.repository.RoadRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Gálibo, peso y cierres sobre la geometría real de la ruta, según la altura y el peso del vehículo. */
class RoadRestrictionServiceRouteTest {

    private static final List<List<Double>> PATH = List.of(List.of(4.60, -74.00), List.of(4.61, -74.00), List.of(4.61, -73.99));

    private RoadRestrictionRepository repository;
    private RoadRestrictionService service;
    private final List<RoadRestriction> stored = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RoadRestrictionRepository.class);
        when(repository.findActiveWithin(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(inv -> stored.stream()
                .filter(r -> r.getLatitude() >= (double) inv.getArgument(0) && r.getLatitude() <= (double) inv.getArgument(1)
                        && r.getLongitude() >= (double) inv.getArgument(2) && r.getLongitude() <= (double) inv.getArgument(3))
                .toList());
        when(repository.findByRoadCorridorIgnoreCaseAndIsActiveTrue(anyString())).thenAnswer(inv -> stored);
        service = new RoadRestrictionService(repository, mock(KafkaTemplate.class));
    }

    private RoadRestriction restriction(long id, RestrictionType type, RestrictionSeverity severity, double lat, double lon) {
        RoadRestriction r = RoadRestriction.builder().id(id).restrictionCode("RES-" + id).title("Restricción " + id)
                .restrictionType(type).severity(severity).roadCorridor("BOGOTA-IBAGUE").latitude(lat).longitude(lon)
                .isActive(true).createdAt(LocalDateTime.now()).build();
        stored.add(r);
        return r;
    }

    private RouteGeometryValidationResponse validate(double heightMeters, double grossWeightTons) {
        return service.validateRoute(ValidateRouteGeometryRequest.builder().vehiclePlate("TDK-410")
                .heightMeters(heightMeters).grossWeightTons(grossWeightTons).path(PATH).build());
    }

    @Test
    void aBridgeOnTheRouteBlocksOnlyTrucksTallerThanItsClearance() {
        // Puente de 3,80 m sobre el segundo tramo, a 20 m de la ruta.
        restriction(1, RestrictionType.GALIBO_ALTURA, RestrictionSeverity.BLOQUEO_TOTAL, 4.61018, -73.995).setMaxClearanceMeters(3.8);

        RouteGeometryValidationResponse tall = validate(4.1, 40);
        assertThat(tall.getPassable()).isFalse();
        assertThat(tall.getBlocking()).singleElement().satisfies(hit -> {
            assertThat(hit.getKind()).isEqualTo("GALIBO_ALTURA");
            assertThat(hit.getAlongRouteMeters()).isCloseTo(1666, within(5.0));
            assertThat(hit.getDistanceFromRouteMeters()).isCloseTo(20, within(1.5));
            assertThat(hit.getReason()).contains("4,10 m").contains("3,80 m");
        });

        // Un camión de 3,40 m pasa: la severidad BLOQUEO_TOTAL de un límite de altura no bloquea a quien cumple.
        RouteGeometryValidationResponse low = validate(3.4, 40);
        assertThat(low.getPassable()).isTrue();
        assertThat(low.getBlocking()).isEmpty();
        assertThat(low.getRestrictionsEvaluated()).isEqualTo(1);
    }

    @Test
    void restrictionsAwayFromTheRouteDoNotApplyEvenOnTheSameCorridorName() {
        // Mismo corredor por nombre, pero a 1,1 km de la ruta real.
        restriction(2, RestrictionType.GALIBO_ALTURA, RestrictionSeverity.BLOQUEO_TOTAL, 4.62, -73.995).setMaxClearanceMeters(3.0);
        RouteGeometryValidationResponse result = validate(4.1, 40);
        assertThat(result.getPassable()).isTrue();
        assertThat(result.getRestrictionsEvaluated()).isZero();
    }

    @Test
    void weightLimitsClosuresAndWarningsAreOrderedAlongTheRoute() {
        restriction(3, RestrictionType.PESO_BRUTO, RestrictionSeverity.BLOQUEO_TOTAL, 4.609, -74.0).setMaxGrossWeightTons(30.0);
        restriction(4, RestrictionType.CIERRE_PROGRAMADO, RestrictionSeverity.BLOQUEO_TOTAL, 4.602, -74.0);
        restriction(5, RestrictionType.PASO_RESTRINGIDO, RestrictionSeverity.PRECAUCION, 4.61, -73.991);
        restriction(6, RestrictionType.PESO_POR_EJE, RestrictionSeverity.PRECAUCION, 4.605, -74.0).setMaxAxleWeightTons(10.0);
        RoadRestriction expired = restriction(7, RestrictionType.CIERRE_PROGRAMADO, RestrictionSeverity.BLOQUEO_TOTAL, 4.606, -74.0);
        expired.setAppliesTo(LocalDateTime.now().minusDays(1));

        RouteGeometryValidationResponse result = validate(4.1, 40);

        assertThat(result.getPassable()).isFalse();
        assertThat(result.getBlocking()).extracting(RouteRestrictionHit::getKind).containsExactly("BLOQUEO_TOTAL", "PESO_BRUTO");
        // Sin el peso por eje real no se bloquea por adivinanza: se avisa.
        assertThat(result.getWarnings()).extracting(RouteRestrictionHit::getKind).containsExactly("PESO_POR_EJE_SIN_DATO", "PRECAUCION");
        assertThat(result.getRestrictionsEvaluated()).isEqualTo(4);
    }

    @Test
    void corridorValidationNoLongerBlocksTrucksThatFitUnderAClearanceMarkedAsTotalBlock() {
        restriction(8, RestrictionType.GALIBO_ALTURA, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995).setMaxClearanceMeters(4.2);
        RouteCompatibilityResponse fits = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("SNT-238").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.4).grossWeightTons(12.0).build());
        assertThat(fits.getIsPassable()).isTrue();
        assertThat(fits.getBlockingReasons()).isEmpty();

        RouteCompatibilityResponse tooTall = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("TKR-527").roadCorridor("BOGOTA-IBAGUE").heightMeters(4.4).grossWeightTons(30.0).build());
        assertThat(tooTall.getIsPassable()).isFalse();
        assertThat(tooTall.getBlockingReasons()).singleElement().asString().contains("gálibo");
    }
}

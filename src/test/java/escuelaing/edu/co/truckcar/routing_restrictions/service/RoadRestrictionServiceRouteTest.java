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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(repository.findByIsActiveTrue()).thenAnswer(inv -> stored);
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

    @Test
    void galiboAlturaBoundaryCases_equalBarelyAboveBarelyBelow() {
        stored.clear();
        restriction(10, RestrictionType.GALIBO_ALTURA, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995).setMaxClearanceMeters(4.20);

        // Apenas por debajo del límite (4.19 m <= 4.20 m): debe ser transitable
        RouteCompatibilityResponse below = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("ABC-101").roadCorridor("BOGOTA-IBAGUE").heightMeters(4.19).grossWeightTons(20.0).build());
        assertThat(below.getIsPassable()).isTrue();
        assertThat(below.getBlockingReasons()).isEmpty();

        // Exactamente igual al límite (4.20 m <= 4.20 m): debe ser transitable
        RouteCompatibilityResponse exact = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("ABC-102").roadCorridor("BOGOTA-IBAGUE").heightMeters(4.20).grossWeightTons(20.0).build());
        assertThat(exact.getIsPassable()).isTrue();
        assertThat(exact.getBlockingReasons()).isEmpty();

        // Apenas por encima del límite (4.21 m > 4.20 m): debe ser bloqueado
        RouteCompatibilityResponse above = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("ABC-103").roadCorridor("BOGOTA-IBAGUE").heightMeters(4.21).grossWeightTons(20.0).build());
        assertThat(above.getIsPassable()).isFalse();
        assertThat(above.getBlockingReasons()).singleElement().asString().contains("gálibo");
    }

    @Test
    void pesoBrutoBoundaryCases_equalBarelyAboveBarelyBelow() {
        stored.clear();
        restriction(20, RestrictionType.PESO_BRUTO, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995).setMaxGrossWeightTons(32.00);

        // Apenas por debajo del límite (31.99 Tn <= 32.00 Tn): transitable
        RouteCompatibilityResponse below = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("XYZ-201").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(31.99).build());
        assertThat(below.getIsPassable()).isTrue();
        assertThat(below.getBlockingReasons()).isEmpty();

        // Igual al límite (32.00 Tn <= 32.00 Tn): transitable
        RouteCompatibilityResponse exact = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("XYZ-202").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(32.00).build());
        assertThat(exact.getIsPassable()).isTrue();
        assertThat(exact.getBlockingReasons()).isEmpty();

        // Apenas por encima del límite (32.01 Tn > 32.00 Tn): bloqueado
        RouteCompatibilityResponse above = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("XYZ-203").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(32.01).build());
        assertThat(above.getIsPassable()).isFalse();
        assertThat(above.getBlockingReasons()).singleElement().asString().contains("Peso bruto");
    }

    @Test
    void pesoPorEjeBoundaryCases_equalBarelyAboveBarelyBelowAndNullData() {
        stored.clear();
        restriction(30, RestrictionType.PESO_POR_EJE, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995).setMaxAxleWeightTons(11.00);

        // Apenas por debajo (10.99 Tn <= 11.00 Tn): transitable
        RouteCompatibilityResponse below = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("EJE-301").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(25.0).axleWeightTons(10.99).build());
        assertThat(below.getIsPassable()).isTrue();
        assertThat(below.getBlockingReasons()).isEmpty();

        // Igual al límite (11.00 Tn <= 11.00 Tn): transitable
        RouteCompatibilityResponse exact = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("EJE-302").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(25.0).axleWeightTons(11.00).build());
        assertThat(exact.getIsPassable()).isTrue();
        assertThat(exact.getBlockingReasons()).isEmpty();

        // Apenas por encima (11.01 Tn > 11.00 Tn): bloqueado
        RouteCompatibilityResponse above = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("EJE-303").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(25.0).axleWeightTons(11.01).build());
        assertThat(above.getIsPassable()).isFalse();
        assertThat(above.getBlockingReasons()).singleElement().asString().contains("Peso por eje");

        // Sin dato de peso por eje: no bloquea pero añade advertencia
        RouteCompatibilityResponse noAxle = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("EJE-304").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.5).grossWeightTons(25.0).axleWeightTons(null).build());
        assertThat(noAxle.getIsPassable()).isTrue();
        assertThat(noAxle.getBlockingReasons()).isEmpty();
        assertThat(noAxle.getWarnings()).singleElement().asString().contains("verifique la carga por eje");
    }

    @Test
    void cierreProgramadoHorarioFestivoAndPasoRestringidoRules() {
        stored.clear();
        // Cierre programado activo con BLOQUEO_TOTAL: bloquea
        RoadRestriction cierre = restriction(40, RestrictionType.CIERRE_PROGRAMADO, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995);
        cierre.setAppliesFrom(LocalDateTime.now().minusHours(1));
        cierre.setAppliesTo(LocalDateTime.now().plusHours(2));

        RouteCompatibilityResponse respCierre = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("CIE-401").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.0).grossWeightTons(10.0).build());
        assertThat(respCierre.getIsPassable()).isFalse();
        assertThat(respCierre.getBlockingReasons()).singleElement().asString().contains("Paso bloqueado");

        // Cierre vencido: no aplica
        cierre.setAppliesTo(LocalDateTime.now().minusMinutes(5));
        RouteCompatibilityResponse respVencido = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("CIE-402").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.0).grossWeightTons(10.0).build());
        assertThat(respVencido.getIsPassable()).isTrue();
        assertThat(respVencido.getBlockingReasons()).isEmpty();

        // Horario festivo con BLOQUEO_TOTAL activo: bloquea
        stored.clear();
        restriction(50, RestrictionType.HORARIO_FESTIVO, RestrictionSeverity.BLOQUEO_TOTAL, 4.61, -73.995);
        RouteCompatibilityResponse respFestivo = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("FES-501").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.0).grossWeightTons(10.0).build());
        assertThat(respFestivo.getIsPassable()).isFalse();

        // Paso restringido con PRECAUCION: no bloquea, añade warning
        stored.clear();
        restriction(60, RestrictionType.PASO_RESTRINGIDO, RestrictionSeverity.PRECAUCION, 4.61, -73.995);
        RouteCompatibilityResponse respPaso = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("PAS-601").roadCorridor("BOGOTA-IBAGUE").heightMeters(3.0).grossWeightTons(10.0).build());
        assertThat(respPaso.getIsPassable()).isTrue();
        assertThat(respPaso.getWarnings()).singleElement().asString().contains("Precaución");
    }

    @Test
    void corridorNormalizationTrimAndUppercase() {
        when(repository.save(any(RoadRestriction.class))).thenAnswer(inv -> inv.getArgument(0));

        escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest req =
                escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest.builder()
                        .title("Cierre Ruta 45")
                        .restrictionType(RestrictionType.CIERRE_PROGRAMADO)
                        .severity(RestrictionSeverity.BLOQUEO_TOTAL)
                        .roadCorridor("   ruta 45  ")
                        .build();

        var created = service.createRestriction(req);
        assertThat(created.getRoadCorridor()).isEqualTo("RUTA 45");
    }

    @Test
    void corridorMatchingWithAccentsAndReverseDirection() {
        stored.clear();
        // Restricción creada por el coordinador como "Bogotá - Tunja" con gálibo de 4.10 m
        RoadRestriction r = RoadRestriction.builder()
                .id(99L)
                .restrictionCode("RES-99")
                .title("Túnel Quebrada Blanca")
                .roadCorridor(RoadRestrictionService.normalizeCorridor("Bogotá - Tunja"))
                .restrictionType(RestrictionType.GALIBO_ALTURA)
                .severity(RestrictionSeverity.BLOQUEO_TOTAL)
                .maxClearanceMeters(4.10)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        stored.add(r);

        // 1. Camión incompatible (4.30m) viajando en sentido inverso: "tunja-bogota" sin tildes ni espacios
        RouteCompatibilityResponse respReverse = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("INC-01")
                .roadCorridor("tunja-bogota")
                .heightMeters(4.30)
                .grossWeightTons(20.0)
                .build());
        assertThat(respReverse.getIsPassable()).isFalse();
        assertThat(respReverse.getBlockingReasons()).singleElement().asString().contains("supera el gálibo máximo");

        // 2. Camión incompatible (4.30m) con tildes y espacios: "Bogotá - Tunja"
        RouteCompatibilityResponse respAccents = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("INC-02")
                .roadCorridor("Bogotá - Tunja")
                .heightMeters(4.30)
                .grossWeightTons(20.0)
                .build());
        assertThat(respAccents.getIsPassable()).isFalse();

        // 3. Camión compatible (3.90m) en el mismo corredor: transitable
        RouteCompatibilityResponse respValid = service.validateCompatibility(ValidateRouteCompatibilityRequest.builder()
                .vehiclePlate("VAL-01")
                .roadCorridor("BOGOTA-TUNJA")
                .heightMeters(3.90)
                .grossWeightTons(20.0)
                .build());
        assertThat(respValid.getIsPassable()).isTrue();
        assertThat(respValid.getBlockingReasons()).isEmpty();
    }
}

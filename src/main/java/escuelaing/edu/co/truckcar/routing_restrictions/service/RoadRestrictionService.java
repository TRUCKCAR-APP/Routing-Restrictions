package escuelaing.edu.co.truckcar.routing_restrictions.service;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.event.RoadRestrictionEvent;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.event.EventEnvelope;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteGeometryRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteGeometryValidationResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteRestrictionHit;
import escuelaing.edu.co.truckcar.routing_restrictions.exception.RestrictionNotFoundException;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionSeverity;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RoadRestriction;
import escuelaing.edu.co.truckcar.routing_restrictions.repository.RoadRestrictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadRestrictionService implements IRoadRestrictionService {

    /** Límites del vehículo: solo bloquean a quien los excede, sea cual sea su severidad. */
    private static final EnumSet<RestrictionType> VEHICLE_LIMITS =
            EnumSet.of(RestrictionType.GALIBO_ALTURA, RestrictionType.PESO_BRUTO, RestrictionType.PESO_POR_EJE);
    private static final double DEFAULT_CORRIDOR_METERS = 60.0;
    /** Los mensajes van en español con coma decimal, sin depender del idioma del servidor. */
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    /** Zona horaria oficial para operaciones de transporte en Colombia (UTC-5). */
    public static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final RoadRestrictionRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${truckcar.kafka.topic.restrictions:truckcar.events.road-restrictions}")
    private String restrictionsTopic;

    @Override
    @Transactional
    public RoadRestrictionResponse createRestriction(CreateRestrictionRequest request) {
        String code = (request.getRestrictionCode() != null && !request.getRestrictionCode().isBlank())
                ? request.getRestrictionCode()
                : "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String corridor = request.getRoadCorridor() != null
                ? normalizeCorridor(request.getRoadCorridor())
                : null;

        RoadRestriction restriction = RoadRestriction.builder()
                .restrictionCode(code)
                .title(request.getTitle())
                .description(request.getDescription())
                .restrictionType(request.getRestrictionType())
                .severity(request.getSeverity())
                .roadCorridor(corridor)
                .department(request.getDepartment())
                .maxClearanceMeters(request.getMaxClearanceMeters())
                .maxGrossWeightTons(request.getMaxGrossWeightTons())
                .maxAxleWeightTons(request.getMaxAxleWeightTons())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .appliesFrom(request.getAppliesFrom())
                .appliesTo(request.getAppliesTo())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        RoadRestriction saved = repository.save(restriction);
        publishKafkaEvent(saved, "CREATED");
        log.info("Restricción vial registrada [{}] en corredor {}", saved.getRestrictionCode(), saved.getRoadCorridor());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadRestrictionResponse getRestrictionById(Long id) {
        return repository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RestrictionNotFoundException("Restricción no encontrada con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadRestrictionResponse> getAllActiveRestrictions(String corridor, RestrictionType type) {
        List<RoadRestriction> list;
        if (corridor != null && !corridor.isBlank()) {
            list = repository.findByRoadCorridorIgnoreCaseAndIsActiveTrue(corridor.trim());
        } else if (type != null) {
            list = repository.findByRestrictionTypeAndIsActiveTrue(type);
        } else {
            list = repository.findByIsActiveTrue();
        }

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoadRestrictionResponse deactivateRestriction(Long id) {
        RoadRestriction restriction = repository.findById(id)
                .orElseThrow(() -> new RestrictionNotFoundException("Restricción no encontrada con ID: " + id));

        restriction.setIsActive(false);
        RoadRestriction updated = repository.save(restriction);
        publishKafkaEvent(updated, "DEACTIVATED");
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteCompatibilityResponse validateCompatibility(ValidateRouteCompatibilityRequest request) {
        String corridor = normalizeCorridor(request.getRoadCorridor());
        // El corredor puede especificarse como trayecto A-B o B-A; la restricción aplica en ambos sentidos.
        String reverseCorridor = corridor.contains("-")
                ? corridor.substring(corridor.indexOf('-') + 1) + "-" + corridor.substring(0, corridor.indexOf('-'))
                : corridor;

        List<RoadRestriction> activeRestrictions = repository.findByIsActiveTrue().stream()
                .filter(res -> {
                    String resCorridor = normalizeCorridor(res.getRoadCorridor());
                    return resCorridor.equals(corridor) || resCorridor.equals(reverseCorridor);
                })
                .toList();

        List<String> blockingReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RoadRestrictionResponse> violatedList = new ArrayList<>();

        // Zona horaria America/Bogota oficial para Colombia (UTC-5)
        LocalDateTime now = LocalDateTime.now(BOGOTA_ZONE);

        for (RoadRestriction res : activeRestrictions) {
            if (res.getAppliesFrom() != null && now.isBefore(res.getAppliesFrom())) {
                continue;
            }
            if (res.getAppliesTo() != null && now.isAfter(res.getAppliesTo())) {
                continue;
            }

            boolean isViolated = false;

            if (res.getRestrictionType() == RestrictionType.GALIBO_ALTURA
                    && res.getMaxClearanceMeters() != null
                    && request.getHeightMeters() > res.getMaxClearanceMeters()) {
                isViolated = true;
                blockingReasons.add(String.format(ES_CO, "Altura del camión (%.2fm) supera el gálibo máximo permitido (%.2fm) en %s",
                        request.getHeightMeters(), res.getMaxClearanceMeters(), res.getTitle()));
            }

            if (res.getRestrictionType() == RestrictionType.PESO_BRUTO
                    && res.getMaxGrossWeightTons() != null
                    && request.getGrossWeightTons() > res.getMaxGrossWeightTons()) {
                isViolated = true;
                blockingReasons.add(String.format(ES_CO, "Peso bruto (%.2f Tn) supera el límite autorizado (%.2f Tn) en %s",
                        request.getGrossWeightTons(), res.getMaxGrossWeightTons(), res.getTitle()));
            }

            if (res.getRestrictionType() == RestrictionType.PESO_POR_EJE
                    && res.getMaxAxleWeightTons() != null) {
                if (request.getAxleWeightTons() != null) {
                    if (request.getAxleWeightTons() > res.getMaxAxleWeightTons()) {
                        isViolated = true;
                        blockingReasons.add(String.format(ES_CO, "Peso por eje (%.2f Tn) excede el máximo permitido (%.2f Tn) en %s",
                                request.getAxleWeightTons(), res.getMaxAxleWeightTons(), res.getTitle()));
                    }
                } else {
                    // Sin el peso por eje del vehículo no se bloquea por adivinanza; se alerta como precaución.
                    warnings.add(String.format(ES_CO, "Límite de %.2f Tn por eje en %s: verifique la carga por eje",
                            res.getMaxAxleWeightTons(), res.getTitle()));
                }
            }

            // Reglas para CIERRE_PROGRAMADO, HORARIO_FESTIVO y PASO_RESTRINGIDO:
            // Si la restricción es de vehículo (gálibo o peso) ya se evaluó numéricamente contra las cotas del camión.
            // Para el resto (o cierres directos por obras/fiestas/eventos):
            // - BLOQUEO_TOTAL: bloquea completamente el tránsito.
            // - PRECAUCION: no impide el paso pero advierte al conductor.
            // - INFORMATIVA: aviso preventivo sin impedir el paso.
            if (VEHICLE_LIMITS.contains(res.getRestrictionType())) {
                // Ya se evaluó contra la altura o el peso del vehículo: la severidad no bloquea a quien cumple.
            } else if (res.getSeverity() == RestrictionSeverity.BLOQUEO_TOTAL) {
                isViolated = true;
                blockingReasons.add("Paso bloqueado en el tramo: " + res.getTitle());
            } else if (res.getSeverity() == RestrictionSeverity.PRECAUCION) {
                warnings.add("Precaución en la ruta: " + res.getTitle());
            } else if (res.getSeverity() == RestrictionSeverity.INFORMATIVA) {
                warnings.add("Aviso en la ruta: " + res.getTitle());
            }

            if (isViolated) {
                violatedList.add(mapToResponse(res));
            }
        }

        boolean isPassable = blockingReasons.isEmpty();

        return RouteCompatibilityResponse.builder()
                .vehiclePlate(request.getVehiclePlate())
                .roadCorridor(request.getRoadCorridor())
                .isPassable(isPassable)
                .blockingReasons(blockingReasons)
                .warnings(warnings)
                .violatedRestrictions(violatedList)
                .build();
    }

    /**
     * Restricciones activas a lo largo de la geometría real de la ruta. Cada restricción con posición aplica si está a
     * corridorMeters o menos de la polilínea; las de altura y peso bloquean solo si el vehículo excede el límite.
     * Limitación: una restricción es un punto, sin calzada ni sentido; un puente que cruza por encima de la ruta y la
     * vía que pasa bajo él se ven igual de cerca.
     */
    @Override
    @Transactional(readOnly = true)
    public RouteGeometryValidationResponse validateRoute(ValidateRouteGeometryRequest request) {
        RouteGeometry route = RouteGeometry.of(request.getPath());
        double corridor = request.getCorridorMeters() != null ? request.getCorridorMeters() : DEFAULT_CORRIDOR_METERS;
        double[] box = route.boundingBox(corridor);
        LocalDateTime now = LocalDateTime.now();

        List<RoadRestriction> nearby = repository.findActiveWithin(box[0], box[1], box[2], box[3]).stream()
                .filter(r -> r.getAppliesFrom() == null || !now.isBefore(r.getAppliesFrom()))
                .filter(r -> r.getAppliesTo() == null || !now.isAfter(r.getAppliesTo()))
                .toList();

        List<RouteRestrictionHit> blocking = new ArrayList<>();
        List<RouteRestrictionHit> warnings = new ArrayList<>();
        int evaluated = 0;
        for (RoadRestriction r : nearby) {
            RouteGeometry.Projection at = route.project(r.getLatitude(), r.getLongitude());
            if (at.distanceMeters() > corridor) {
                continue;
            }
            evaluated++;
            classify(r, request).ifPresent(hit -> {
                hit.setAlongRouteMeters(Math.round(at.alongMeters() * 10) / 10.0);
                hit.setDistanceFromRouteMeters(Math.round(at.distanceMeters() * 10) / 10.0);
                hit.setRestriction(mapToResponse(r));
                (isBlocking(hit.getKind()) ? blocking : warnings).add(hit);
            });
        }
        Comparator<RouteRestrictionHit> byPosition = Comparator.comparing(RouteRestrictionHit::getAlongRouteMeters);
        blocking.sort(byPosition);
        warnings.sort(byPosition);

        return RouteGeometryValidationResponse.builder()
                .vehiclePlate(request.getVehiclePlate())
                .passable(blocking.isEmpty())
                .routeLengthMeters(Math.round(route.lengthMeters() * 10) / 10.0)
                .restrictionsEvaluated(evaluated)
                .blocking(blocking)
                .warnings(warnings)
                .build();
    }

    /** Por qué aplica la restricción a este vehículo; vacío si no le aplica. */
    private java.util.Optional<RouteRestrictionHit> classify(RoadRestriction r, ValidateRouteGeometryRequest v) {
        String title = r.getTitle();
        switch (r.getRestrictionType()) {
            case GALIBO_ALTURA -> {
                if (r.getMaxClearanceMeters() != null && v.getHeightMeters() > r.getMaxClearanceMeters()) {
                    return hit("GALIBO_ALTURA", String.format(ES_CO, "Altura del camión (%.2f m) supera el gálibo de %.2f m en %s",
                            v.getHeightMeters(), r.getMaxClearanceMeters(), title));
                }
                return java.util.Optional.empty();
            }
            case PESO_BRUTO -> {
                if (r.getMaxGrossWeightTons() != null && v.getGrossWeightTons() > r.getMaxGrossWeightTons()) {
                    return hit("PESO_BRUTO", String.format(ES_CO, "Peso bruto (%.1f t) supera el límite de %.1f t en %s",
                            v.getGrossWeightTons(), r.getMaxGrossWeightTons(), title));
                }
                return java.util.Optional.empty();
            }
            case PESO_POR_EJE -> {
                if (r.getMaxAxleWeightTons() == null) {
                    return java.util.Optional.empty();
                }
                if (v.getAxleWeightTons() == null) {
                    // Sin el peso por eje real no se adivina: se avisa del límite para que el conductor lo verifique.
                    return hit("PESO_POR_EJE_SIN_DATO", String.format(ES_CO, "Límite de %.1f t por eje en %s: verifique la carga por eje",
                            r.getMaxAxleWeightTons(), title));
                }
                if (v.getAxleWeightTons() > r.getMaxAxleWeightTons()) {
                    return hit("PESO_POR_EJE", String.format(ES_CO, "Peso por eje (%.1f t) supera el límite de %.1f t en %s",
                            v.getAxleWeightTons(), r.getMaxAxleWeightTons(), title));
                }
                return java.util.Optional.empty();
            }
            default -> {
                if (r.getSeverity() == RestrictionSeverity.BLOQUEO_TOTAL) {
                    return hit("BLOQUEO_TOTAL", "Paso cerrado: " + title);
                }
                if (r.getSeverity() == RestrictionSeverity.PRECAUCION) {
                    return hit("PRECAUCION", "Precaución: " + title);
                }
                return java.util.Optional.empty();
            }
        }
    }

    private static boolean isBlocking(String kind) {
        return !"PRECAUCION".equals(kind) && !"PESO_POR_EJE_SIN_DATO".equals(kind);
    }

    private static java.util.Optional<RouteRestrictionHit> hit(String kind, String reason) {
        return java.util.Optional.of(RouteRestrictionHit.builder().kind(kind).reason(reason).build());
    }

    private void publishKafkaEvent(RoadRestriction restriction, String action) {
        RoadRestrictionEvent payload = RoadRestrictionEvent.builder()
                .restrictionId(restriction.getId())
                .restrictionCode(restriction.getRestrictionCode())
                .title(restriction.getTitle())
                .restrictionType(restriction.getRestrictionType().name())
                .severity(restriction.getSeverity().name())
                .roadCorridor(restriction.getRoadCorridor())
                .latitude(restriction.getLatitude())
                .longitude(restriction.getLongitude())
                .eventAction(action)
                .timestamp(LocalDateTime.now())
                .build();

        EventEnvelope<RoadRestrictionEvent> event = EventEnvelope.<RoadRestrictionEvent>builder()
                .eventId(UUID.randomUUID())
                .eventType("CREATED".equals(action) ? "restriction.created" : "route.updated")
                .occurredAt(Instant.now())
                .correlationId(UUID.nameUUIDFromBytes(
                        restriction.getRestrictionCode().getBytes(StandardCharsets.UTF_8)))
                .version(1)
                .producer("routing-restrictions")
                .payload(payload)
                .build();

        kafkaTemplate.send(restrictionsTopic, restriction.getRoadCorridor(), event);
    }

    private RoadRestrictionResponse mapToResponse(RoadRestriction entity) {
        return RoadRestrictionResponse.builder()
                .id(entity.getId())
                .restrictionCode(entity.getRestrictionCode())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .restrictionType(entity.getRestrictionType())
                .severity(entity.getSeverity())
                .roadCorridor(entity.getRoadCorridor())
                .department(entity.getDepartment())
                .maxClearanceMeters(entity.getMaxClearanceMeters())
                .maxGrossWeightTons(entity.getMaxGrossWeightTons())
                .maxAxleWeightTons(entity.getMaxAxleWeightTons())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .appliesFrom(entity.getAppliesFrom())
                .appliesTo(entity.getAppliesTo())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Normaliza canónicamente el nombre de un corredor vial:
     * - Remueve tildes y diacríticos (ej. "Bogotá" -> "Bogota")
     * - Remueve espacios alrededor de guiones (ej. "Bogotá - Tunja" -> "BOGOTA-TUNJA")
     * - Colapsa espacios redundantes
     * - Convierte a mayúsculas
     */
    public static String normalizeCorridor(String corridor) {
        if (corridor == null || corridor.isBlank()) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(corridor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.trim()
                .replaceAll("\\s*-\\s*", "-")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
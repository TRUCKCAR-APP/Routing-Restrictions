package escuelaing.edu.co.truckcar.routing_restrictions.service;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.event.RoadRestrictionEvent;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.event.EventEnvelope;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadRestrictionService implements IRoadRestrictionService {

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

        RoadRestriction restriction = RoadRestriction.builder()
                .restrictionCode(code)
                .title(request.getTitle())
                .description(request.getDescription())
                .restrictionType(request.getRestrictionType())
                .severity(request.getSeverity())
                .roadCorridor(request.getRoadCorridor())
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
            list = repository.findByRoadCorridorIgnoreCaseAndIsActiveTrue(corridor);
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
        List<RoadRestriction> activeRestrictions = repository.findByRoadCorridorIgnoreCaseAndIsActiveTrue(request.getRoadCorridor());

        List<String> blockingReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RoadRestrictionResponse> violatedList = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

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
                blockingReasons.add(String.format("Altura del camión (%.2fm) supera el gálibo máximo permitido (%.2fm) en %s",
                        request.getHeightMeters(), res.getMaxClearanceMeters(), res.getTitle()));
            }

            if (res.getRestrictionType() == RestrictionType.PESO_BRUTO
                    && res.getMaxGrossWeightTons() != null
                    && request.getGrossWeightTons() > res.getMaxGrossWeightTons()) {
                isViolated = true;
                blockingReasons.add(String.format("Peso bruto (%.2f Tn) supera el límite autorizado (%.2f Tn) en %s",
                        request.getGrossWeightTons(), res.getMaxGrossWeightTons(), res.getTitle()));
            }

            if (res.getRestrictionType() == RestrictionType.PESO_POR_EJE
                    && res.getMaxAxleWeightTons() != null
                    && request.getAxleWeightTons() != null
                    && request.getAxleWeightTons() > res.getMaxAxleWeightTons()) {
                isViolated = true;
                blockingReasons.add(String.format("Peso por eje (%.2f Tn) excede el máximo permitido (%.2f Tn) en %s",
                        request.getAxleWeightTons(), res.getMaxAxleWeightTons(), res.getTitle()));
            }

            if (res.getSeverity() == RestrictionSeverity.BLOQUEO_TOTAL) {
                isViolated = true;
                blockingReasons.add("Paso bloqueado en el tramo: " + res.getTitle());
            } else if (res.getSeverity() == RestrictionSeverity.PRECAUCION) {
                warnings.add("Precaución en la ruta: " + res.getTitle());
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
}
package escuelaing.edu.co.truckcar.routing_restrictions.controller;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteGeometryRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteGeometryValidationResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.exception.RestrictionAccessDeniedException;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.service.IRoadRestrictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/restrictions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoadRestrictionController {

    private static final String AUTH_ROLE_HEADER = "X-Auth-Role";
    private static final Set<String> CONTROL_TOWER_ROLES = Set.of("COORDINADOR", "ADMIN");

    private final IRoadRestrictionService service;

    /** Crear y desactivar restricciones (gálibo, peso, horario) cambia las rutas de toda la flota: solo la torre. */
    @PostMapping
    public ResponseEntity<RoadRestrictionResponse> createRestriction(
            @Valid @RequestBody CreateRestrictionRequest request,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireControlTower(authenticatedRole);
        return new ResponseEntity<>(service.createRestriction(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoadRestrictionResponse> getRestrictionById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRestrictionById(id));
    }

    @GetMapping
    public ResponseEntity<List<RoadRestrictionResponse>> getAllActive(
            @RequestParam(required = false) String corridor,
            @RequestParam(required = false) RestrictionType type) {
        return ResponseEntity.ok(service.getAllActiveRestrictions(corridor, type));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<RoadRestrictionResponse> deactivateRestriction(
            @PathVariable Long id,
            @RequestHeader(value = AUTH_ROLE_HEADER, required = false) String authenticatedRole) {
        requireControlTower(authenticatedRole);
        return ResponseEntity.ok(service.deactivateRestriction(id));
    }

    @PostMapping("/validate-compatibility")
    public ResponseEntity<RouteCompatibilityResponse> validateCompatibility(
            @Valid @RequestBody ValidateRouteCompatibilityRequest request) {
        return ResponseEntity.ok(service.validateCompatibility(request));
    }

    /** Gálibo, peso y cierres sobre la geometría real de la ruta (la polilínea de OSRM), no sobre el nombre del corredor. */
    @PostMapping("/validate-route")
    public ResponseEntity<RouteGeometryValidationResponse> validateRoute(@Valid @RequestBody ValidateRouteGeometryRequest request) {
        return ResponseEntity.ok(service.validateRoute(request));
    }

    /** X-Auth-Role lo fija el api-gateway desde el JWT (descarta el que envíe el cliente). */
    private static void requireControlTower(String authenticatedRole) {
        if (authenticatedRole == null || authenticatedRole.isBlank()) {
            throw new RestrictionAccessDeniedException("Se requiere un rol autenticado para modificar restricciones", true);
        }
        if (!CONTROL_TOWER_ROLES.contains(authenticatedRole)) {
            throw new RestrictionAccessDeniedException("El rol " + authenticatedRole
                    + " no puede modificar restricciones viales (solo COORDINADOR o ADMIN)", false);
        }
    }
}
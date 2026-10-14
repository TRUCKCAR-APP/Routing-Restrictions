package escuelaing.edu.co.truckcar.routing_restrictions.controller;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.service.IRoadRestrictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restrictions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoadRestrictionController {

    private final IRoadRestrictionService service;

    @PostMapping
    public ResponseEntity<RoadRestrictionResponse> createRestriction(@Valid @RequestBody CreateRestrictionRequest request) {
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
    public ResponseEntity<RoadRestrictionResponse> deactivateRestriction(@PathVariable Long id) {
        return ResponseEntity.ok(service.deactivateRestriction(id));
    }

    @PostMapping("/validate-compatibility")
    public ResponseEntity<RouteCompatibilityResponse> validateCompatibility(
            @Valid @RequestBody ValidateRouteCompatibilityRequest request) {
        return ResponseEntity.ok(service.validateCompatibility(request));
    }
}
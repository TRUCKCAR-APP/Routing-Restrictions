package escuelaing.edu.co.truckcar.routing_restrictions.service;

import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.CreateRestrictionRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.request.ValidateRouteCompatibilityRequest;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RoadRestrictionResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.dto.response.RouteCompatibilityResponse;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;

import java.util.List;

public interface IRoadRestrictionService {
    RoadRestrictionResponse createRestriction(CreateRestrictionRequest request);
    RoadRestrictionResponse getRestrictionById(Long id);
    List<RoadRestrictionResponse> getAllActiveRestrictions(String corridor, RestrictionType type);
    RoadRestrictionResponse deactivateRestriction(Long id);
    RouteCompatibilityResponse validateCompatibility(ValidateRouteCompatibilityRequest request);
}
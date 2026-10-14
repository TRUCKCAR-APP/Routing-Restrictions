package escuelaing.edu.co.truckcar.routing_restrictions.repository;

import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RoadRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadRestrictionRepository extends JpaRepository<RoadRestriction, Long> {

    Optional<RoadRestriction> findByRestrictionCode(String restrictionCode);

    List<RoadRestriction> findByIsActiveTrue();

    List<RoadRestriction> findByRoadCorridorIgnoreCaseAndIsActiveTrue(String roadCorridor);

    List<RoadRestriction> findByRestrictionTypeAndIsActiveTrue(RestrictionType restrictionType);
}
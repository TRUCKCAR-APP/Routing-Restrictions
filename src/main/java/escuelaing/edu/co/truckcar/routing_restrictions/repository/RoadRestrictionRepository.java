package escuelaing.edu.co.truckcar.routing_restrictions.repository;

import escuelaing.edu.co.truckcar.routing_restrictions.model.RestrictionType;
import escuelaing.edu.co.truckcar.routing_restrictions.model.RoadRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadRestrictionRepository extends JpaRepository<RoadRestriction, Long> {

    Optional<RoadRestriction> findByRestrictionCode(String restrictionCode);

    List<RoadRestriction> findByIsActiveTrue();

    List<RoadRestriction> findByRoadCorridorIgnoreCaseAndIsActiveTrue(String roadCorridor);

    List<RoadRestriction> findByRestrictionTypeAndIsActiveTrue(RestrictionType restrictionType);

    /** Restricciones activas con posición dentro del rectángulo que contiene la ruta (filtro previo al cálculo exacto). */
    @Query("""
            SELECT r FROM RoadRestriction r
            WHERE r.isActive = true AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
              AND r.latitude BETWEEN :latMin AND :latMax AND r.longitude BETWEEN :lonMin AND :lonMax
            """)
    List<RoadRestriction> findActiveWithin(@Param("latMin") double latMin, @Param("latMax") double latMax,
                                           @Param("lonMin") double lonMin, @Param("lonMax") double lonMax);
}
package escuelaing.edu.co.truckcar.routing_restrictions.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "road_restrictions", indexes = {
        @Index(name = "idx_restriction_type", columnList = "restriction_type"),
        @Index(name = "idx_restriction_corridor", columnList = "road_corridor"),
        @Index(name = "idx_restriction_active", columnList = "is_active")
})
public class RoadRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restriction_code", nullable = false, unique = true)
    private String restrictionCode;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_type", nullable = false)
    private RestrictionType restrictionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private RestrictionSeverity severity;

    @Column(name = "road_corridor", nullable = false)
    private String roadCorridor;

    @Column(name = "department")
    private String department;

    @Column(name = "max_clearance_meters")
    private Double maxClearanceMeters;

    @Column(name = "max_gross_weight_tons")
    private Double maxGrossWeightTons;

    @Column(name = "max_axle_weight_tons")
    private Double maxAxleWeightTons;

    private Double latitude;
    private Double longitude;

    @Column(name = "applies_from")
    private LocalDateTime appliesFrom;

    @Column(name = "applies_to")
    private LocalDateTime appliesTo;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
package escuelaing.edu.co.truckcar.routing_restrictions.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Distancias sobre una polilínea [latitud, longitud] como la de OSRM. */
class RouteGeometryTest {

    // 1,11 km al norte por la longitud -74 y luego 1,11 km al oriente por la latitud 4,61.
    private static final List<List<Double>> PATH = List.of(List.of(4.60, -74.00), List.of(4.61, -74.00), List.of(4.61, -73.99));

    @Test
    void measuresTheRouteAndProjectsAPointOntoIt() {
        RouteGeometry route = RouteGeometry.of(PATH);
        assertThat(route.lengthMeters()).isCloseTo(2221, within(5.0));

        // Un punto 30 m al oriente de la mitad del primer tramo.
        RouteGeometry.Projection beside = route.project(4.605, -73.99973);
        assertThat(beside.distanceMeters()).isCloseTo(30, within(1.5));
        assertThat(beside.alongMeters()).isCloseTo(556, within(3.0));

        // Un punto sobre el segundo tramo: la distancia a lo largo suma el primer tramo completo.
        RouteGeometry.Projection second = route.project(4.61, -73.995);
        assertThat(second.distanceMeters()).isLessThan(1);
        assertThat(second.alongMeters()).isCloseTo(1666, within(5.0));
    }

    @Test
    void boundingBoxGrowsByTheCorridor() {
        double[] box = RouteGeometry.of(PATH).boundingBox(111.32);
        assertThat(box[0]).isCloseTo(4.599, within(1e-6));
        assertThat(box[1]).isCloseTo(4.611, within(1e-6));
        assertThat(box[2]).isLessThan(-74.00).isGreaterThan(-74.0012);
        assertThat(box[3]).isGreaterThan(-73.99).isLessThan(-73.9888);
    }

    @Test
    void rejectsMalformedRoutes() {
        assertThatThrownBy(() -> RouteGeometry.of(List.of(List.of(4.6, -74.0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 2 puntos");
        assertThatThrownBy(() -> RouteGeometry.of(List.of(List.of(4.6, -74.0), List.of(4.6))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("[latitud, longitud]");
        assertThatThrownBy(() -> RouteGeometry.of(List.of(List.of(4.6, -74.0), List.of(95.0, -74.0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fuera de rango");
    }
}

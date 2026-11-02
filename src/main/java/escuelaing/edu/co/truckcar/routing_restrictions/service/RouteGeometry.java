package escuelaing.edu.co.truckcar.routing_restrictions.service;

import java.util.List;

/**
 * Geometría de una ruta como polilínea [latitud, longitud] (la que devuelve OSRM). Distancias en metros: haversine para
 * el largo de cada tramo y una proyección local equirectangular para la distancia de un punto al tramo, que a decenas
 * o cientos de metros tiene un error despreciable.
 */
public final class RouteGeometry {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private final List<double[]> points;
    private final double[] cumulative;

    private RouteGeometry(List<double[]> points) {
        this.points = points;
        this.cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + haversine(points.get(i - 1), points.get(i));
        }
    }

    /** Valida la polilínea: al menos 2 puntos, cada uno [lat, lon] dentro de rango. */
    public static RouteGeometry of(List<List<Double>> path) {
        if (path == null || path.size() < 2) {
            throw new IllegalArgumentException("La ruta necesita al menos 2 puntos [latitud, longitud]");
        }
        List<double[]> points = path.stream().map(point -> {
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null) {
                throw new IllegalArgumentException("Cada punto de la ruta debe ser [latitud, longitud]");
            }
            double lat = point.get(0);
            double lon = point.get(1);
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
                throw new IllegalArgumentException("Punto de la ruta fuera de rango: [" + lat + ", " + lon + "]");
            }
            return new double[]{lat, lon};
        }).toList();
        return new RouteGeometry(points);
    }

    public double lengthMeters() {
        return cumulative[cumulative.length - 1];
    }

    /** [latMin, latMax, lonMin, lonMax] de la ruta, ampliado en marginMeters por cada lado. */
    public double[] boundingBox(double marginMeters) {
        double latMin = 90, latMax = -90, lonMin = 180, lonMax = -180;
        for (double[] p : points) {
            latMin = Math.min(latMin, p[0]);
            latMax = Math.max(latMax, p[0]);
            lonMin = Math.min(lonMin, p[1]);
            lonMax = Math.max(lonMax, p[1]);
        }
        double dLat = marginMeters / METERS_PER_DEGREE_LAT;
        double maxAbsLat = Math.min(89.0, Math.max(Math.abs(latMin), Math.abs(latMax)));
        double dLon = marginMeters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(maxAbsLat)));
        return new double[]{latMin - dLat, latMax + dLat, lonMin - dLon, lonMax + dLon};
    }

    /** Distancia mínima del punto a la ruta y cuántos metros de ruta hay desde el inicio hasta ese punto más cercano. */
    public Projection project(double lat, double lon) {
        double best = Double.MAX_VALUE;
        double along = 0;
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            // Plano local centrado en el punto: x hacia el oriente, y hacia el norte, en metros.
            double kx = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat));
            double ax = (a[1] - lon) * kx, ay = (a[0] - lat) * METERS_PER_DEGREE_LAT;
            double bx = (b[1] - lon) * kx, by = (b[0] - lat) * METERS_PER_DEGREE_LAT;
            double dx = bx - ax, dy = by - ay;
            double lengthSq = dx * dx + dy * dy;
            double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSq));
            double px = ax + t * dx, py = ay + t * dy;
            double distance = Math.sqrt(px * px + py * py);
            if (distance < best) {
                best = distance;
                along = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1]);
            }
        }
        return new Projection(best, along);
    }

    static double haversine(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** @param distanceMeters distancia del punto a la ruta; @param alongMeters metros de ruta desde el inicio. */
    public record Projection(double distanceMeters, double alongMeters) {
    }
}

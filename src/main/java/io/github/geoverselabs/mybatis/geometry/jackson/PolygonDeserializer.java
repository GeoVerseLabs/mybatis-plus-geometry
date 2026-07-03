package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserializer for GeoJSON Polygon to JTS Polygon.
 * Validates ring closure and corrects ring orientation.
 *
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "Polygon",
 *   "coordinates": [
 *     [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring (CCW)
 *     [[lon1, lat1], ...]  // interior rings (CW)
 *   ]
 * }
 * }</pre>
 */
public class PolygonDeserializer extends JsonDeserializer<Polygon> {

    private final boolean coordinateValidationEnabled;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public PolygonDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public PolygonDeserializer(boolean coordinateValidationEnabled) {
        this.coordinateValidationEnabled = coordinateValidationEnabled;
    }

    @Override
    public Polygon deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node == null || node.isNull()) {
            return null;
        }

        // Validate GeoJSON type
        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            throw new GeoJsonParseException("Missing 'type' field", "type");
        }

        String type = typeNode.asText();
        if (!"Polygon".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("Polygon", type);
        }

        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.isEmpty()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }

        GeometryFactory factory = GeometryFactoryProvider.getFactory();

        // Parse exterior ring
        JsonNode exteriorRingNode = coordinatesNode.get(0);
        LinearRing shell = createLinearRing(exteriorRingNode, true, factory);

        // Parse interior rings (holes)
        LinearRing[] holes = null;
        if (coordinatesNode.size() > 1) {
            List<LinearRing> holesList = new ArrayList<>();
            for (int i = 1; i < coordinatesNode.size(); i++) {
                holesList.add(createLinearRing(coordinatesNode.get(i), false, factory));
            }
            holes = holesList.toArray(new LinearRing[0]);
        }

        Polygon polygon = factory.createPolygon(shell, holes);

        if (!polygon.isValid()) {
            throw new GeoJsonParseException("Invalid polygon geometry: not valid according to OGC rules",
                "coordinates");
        }

        return polygon;
    }


    private LinearRing createLinearRing(JsonNode coordinatesNode, boolean isExterior,
            GeometryFactory factory) throws IOException {
        if (coordinatesNode == null || !coordinatesNode.isArray() || coordinatesNode.size() < 4) {
            throw new GeoJsonParseException(
                "Invalid coordinate array: a polygon ring must have at least 4 points",
                "coordinates");
        }

        Coordinate[] coordinates = new Coordinate[coordinatesNode.size()];

        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode coordNode = coordinatesNode.get(i);
            if (!coordNode.isArray() || coordNode.size() < 2) {
                throw new GeoJsonParseException("Invalid coordinate pair at index " + i, "coordinates");
            }

            double lon = coordNode.get(0).asDouble();
            double lat = coordNode.get(1).asDouble();

            // Validate coordinates
            validateCoordinate(lon, lat);

            coordinates[i] = new Coordinate(lon, lat);
        }

        // Validate ring closure
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw new GeoJsonParseException(
                String.format("Invalid ring: first point (%f,%f) != last point (%f,%f)",
                    coordinates[0].x, coordinates[0].y,
                    coordinates[coordinates.length - 1].x, coordinates[coordinates.length - 1].y),
                "coordinates");
        }

        LinearRing ring = factory.createLinearRing(coordinates);

        // Correct ring orientation
        // Exterior ring should be counter-clockwise (CCW)
        // Interior rings (holes) should be clockwise (CW)
        boolean isCounterClockwise = Orientation.isCCW(coordinates);

        if (isExterior && !isCounterClockwise) {
            // Exterior ring should be CCW, reverse if CW
            coordinates = ring.reverse().getCoordinates();
            ring = factory.createLinearRing(coordinates);
        } else if (!isExterior && isCounterClockwise) {
            // Interior ring should be CW, reverse if CCW
            coordinates = ring.reverse().getCoordinates();
            ring = factory.createLinearRing(coordinates);
        }

        return ring;
    }

    private void validateCoordinate(double longitude, double latitude) throws IOException {
        if (coordinateValidationEnabled) {
            // WGS84 range validation
            if (longitude < -180 || longitude > 180) {
                throw InvalidCoordinateException.forLongitude(longitude);
            }
            if (latitude < -90 || latitude > 90) {
                throw InvalidCoordinateException.forLatitude(latitude);
            }
        } else {
            // Only validate that coordinates are finite (not NaN or Infinity)
            if (!Double.isFinite(longitude)) {
                throw new InvalidCoordinateException("longitude", longitude, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (!Double.isFinite(latitude)) {
                throw new InvalidCoordinateException("latitude", latitude, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
        }
    }
}

package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserializer for GeoJSON MultiPolygon to JTS MultiPolygon.
 *
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiPolygon",
 *   "coordinates": [
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]],  // exterior ring
 *       [[lon1, lat1], ...]  // interior rings (holes)
 *     ],
 *     [
 *       [[lon1, lat1], [lon2, lat2], ..., [lon1, lat1]]   // another polygon
 *     ]
 *   ]
 * }
 * }</pre>
 */
public class MultiPolygonDeserializer extends JsonDeserializer<MultiPolygon> {

    private final boolean coordinateValidationEnabled;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public MultiPolygonDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiPolygonDeserializer(boolean coordinateValidationEnabled) {
        this.coordinateValidationEnabled = coordinateValidationEnabled;
    }

    @Override
    public MultiPolygon deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
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
        if (!"MultiPolygon".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("MultiPolygon", type);
        }

        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }

        GeometryFactory factory = GeometryFactoryProvider.getFactory();

        // Parse each polygon in the MultiPolygon
        Polygon[] polygons = new Polygon[coordinatesNode.size()];
        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode polygonNode = coordinatesNode.get(i);
            if (!polygonNode.isArray() || polygonNode.isEmpty()) {
                throw new GeoJsonParseException(
                    "Invalid polygon coordinate array at index " + i, "coordinates");
            }

            // First element is exterior ring
            JsonNode exteriorRingNode = polygonNode.get(0);
            LinearRing shell = createLinearRing(exteriorRingNode, factory);

            // Remaining elements are interior rings (holes)
            LinearRing[] holes = null;
            if (polygonNode.size() > 1) {
                List<LinearRing> holesList = new ArrayList<>();
                for (int j = 1; j < polygonNode.size(); j++) {
                    holesList.add(createLinearRing(polygonNode.get(j), factory));
                }
                holes = holesList.toArray(new LinearRing[0]);
            }

            polygons[i] = factory.createPolygon(shell, holes);
        }

        return factory.createMultiPolygon(polygons);
    }

    private LinearRing createLinearRing(JsonNode ringNode, GeometryFactory factory) throws IOException {
        if (ringNode == null || !ringNode.isArray() || ringNode.size() < 4) {
            throw new GeoJsonParseException(
                "Invalid coordinate array: a polygon ring must have at least 4 points",
                "coordinates");
        }

        Coordinate[] coordinates = new Coordinate[ringNode.size()];

        for (int i = 0; i < ringNode.size(); i++) {
            JsonNode coordNode = ringNode.get(i);
            if (!coordNode.isArray() || coordNode.size() < 2) {
                throw new GeoJsonParseException("Invalid coordinate pair at index " + i, "coordinates");
            }

            double lon = coordNode.get(0).asDouble();
            double lat = coordNode.get(1).asDouble();

            validateCoordinate(lon, lat);

            coordinates[i] = new Coordinate(lon, lat);
        }

        // Validate ring closure: last coordinate must equal first coordinate
        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw new GeoJsonParseException(
                String.format("Invalid ring: first point (%f,%f) != last point (%f,%f)",
                    coordinates[0].x, coordinates[0].y,
                    coordinates[coordinates.length - 1].x, coordinates[coordinates.length - 1].y),
                "coordinates");
        }

        return factory.createLinearRing(coordinates);
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
                throw new InvalidCoordinateException("longitude", longitude,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            if (!Double.isFinite(latitude)) {
                throw new InvalidCoordinateException("latitude", latitude,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
        }
    }
}

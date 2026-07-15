package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserializer for GeoJSON GeometryCollection to JTS GeometryCollection.
 *
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "GeometryCollection",
 *   "geometries": [
 *     { "type": "Point", "coordinates": [100.0, 0.0] },
 *     { "type": "LineString", "coordinates": [[101.0, 0.0], [102.0, 1.0]] }
 *   ]
 * }
 * }</pre>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = GeometryCollectionSerializer.class)
 * @JsonDeserialize(using = GeometryCollectionDeserializer.class)
 * private GeometryCollection collection;
 * }</pre>
 */
public class GeometryCollectionDeserializer extends JsonDeserializer<GeometryCollection> {

    private final boolean coordinateValidationEnabled;

    private final PointDeserializer pointDeserializer;
    private final LineStringDeserializer lineStringDeserializer;
    private final PolygonDeserializer polygonDeserializer;
    private final MultiPointDeserializer multiPointDeserializer;
    private final MultiLineStringDeserializer multiLineStringDeserializer;
    private final MultiPolygonDeserializer multiPolygonDeserializer;

    /**
     * Default constructor with coordinate validation enabled (WGS84 range).
     */
    public GeometryCollectionDeserializer() {
        this(true);
    }

    /**
     * Constructor with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public GeometryCollectionDeserializer(boolean coordinateValidationEnabled) {
        this.coordinateValidationEnabled = coordinateValidationEnabled;
        this.pointDeserializer = new PointDeserializer(coordinateValidationEnabled);
        this.lineStringDeserializer = new LineStringDeserializer(coordinateValidationEnabled);
        this.polygonDeserializer = new PolygonDeserializer(coordinateValidationEnabled);
        this.multiPointDeserializer = new MultiPointDeserializer(coordinateValidationEnabled);
        this.multiLineStringDeserializer = new MultiLineStringDeserializer(coordinateValidationEnabled);
        this.multiPolygonDeserializer = new MultiPolygonDeserializer(coordinateValidationEnabled);
    }

    @Override
    public GeometryCollection deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
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
        if (!"GeometryCollection".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("GeometryCollection", type);
        }

        // Validate geometries field
        JsonNode geometriesNode = node.get("geometries");
        if (geometriesNode == null || !geometriesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'geometries' field", "geometries");
        }

        // Parse each sub-geometry
        List<Geometry> geometries = new ArrayList<>();
        for (int i = 0; i < geometriesNode.size(); i++) {
            JsonNode geomNode = geometriesNode.get(i);

            JsonNode geomTypeNode = geomNode.get("type");
            if (geomTypeNode == null) {
                throw new GeoJsonParseException(
                    "Missing 'type' field in geometry at index " + i, "type");
            }

            String geomType = geomTypeNode.asText();
            JsonParser nodeParser = geomNode.traverse(parser.getCodec());
            nodeParser.nextToken();

            Geometry geometry = switch (geomType) {
                case "Point" -> pointDeserializer.deserialize(nodeParser, ctx);
                case "LineString" -> lineStringDeserializer.deserialize(nodeParser, ctx);
                case "Polygon" -> polygonDeserializer.deserialize(nodeParser, ctx);
                case "MultiPoint" -> multiPointDeserializer.deserialize(nodeParser, ctx);
                case "MultiLineString" -> multiLineStringDeserializer.deserialize(nodeParser, ctx);
                case "MultiPolygon" -> multiPolygonDeserializer.deserialize(nodeParser, ctx);
                default -> throw new GeoJsonParseException(
                    "Unsupported geometry type in GeometryCollection: " + geomType, "type");
            };

            geometries.add(geometry);
        }

        return GeometryFactoryProvider.getFactory()
            .createGeometryCollection(geometries.toArray(new Geometry[0]));
    }
}

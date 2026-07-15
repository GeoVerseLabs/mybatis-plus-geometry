package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Jackson Module that registers GeoJSON serializers/deserializers for JTS geometry types.
 *
 * <p>Supported types:</p>
 * <ul>
 *   <li>Point</li>
 *   <li>LineString</li>
 *   <li>Polygon</li>
 *   <li>MultiPoint</li>
 *   <li>MultiLineString</li>
 *   <li>MultiPolygon</li>
 *   <li>GeometryCollection</li>
 *   <li>Geometry (generic, dispatches to specific type)</li>
 * </ul>
 *
 * <p>Output format conforms to RFC 7946 GeoJSON with coordinate order [longitude, latitude].</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new GeometryJacksonModule());
 * }</pre>
 */
public class GeometryJacksonModule extends SimpleModule {

    /**
     * Create a GeometryJacksonModule with coordinate validation enabled (default).
     */
    public GeometryJacksonModule() {
        this(true);
    }

    /**
     * Create a GeometryJacksonModule with configurable coordinate validation.
     *
     * @param coordinateValidationEnabled when true, deserializers validate WGS84 range;
     *                                    when false, only validate Double.isFinite()
     */
    public GeometryJacksonModule(boolean coordinateValidationEnabled) {
        super("GeometryJacksonModule");

        // Serializers - specific types first
        addSerializer(Point.class, new PointSerializer());
        addSerializer(LineString.class, new LineStringSerializer());
        addSerializer(Polygon.class, new PolygonSerializer());
        addSerializer(MultiPoint.class, new MultiPointSerializer());
        addSerializer(MultiLineString.class, new MultiLineStringSerializer());
        addSerializer(MultiPolygon.class, new MultiPolygonSerializer());
        addSerializer(GeometryCollection.class, new GeometryCollectionSerializer());
        // Generic Geometry serializer registered last to avoid Jackson type resolution issues
        addSerializer(Geometry.class, new GenericGeometrySerializer());

        // Deserializers - specific types first
        addDeserializer(Point.class, new PointDeserializer(coordinateValidationEnabled));
        addDeserializer(LineString.class, new LineStringDeserializer(coordinateValidationEnabled));
        addDeserializer(Polygon.class, new PolygonDeserializer(coordinateValidationEnabled));
        addDeserializer(MultiPoint.class, new MultiPointDeserializer(coordinateValidationEnabled));
        addDeserializer(MultiLineString.class, new MultiLineStringDeserializer(coordinateValidationEnabled));
        addDeserializer(MultiPolygon.class, new MultiPolygonDeserializer(coordinateValidationEnabled));
        addDeserializer(GeometryCollection.class, new GeometryCollectionDeserializer(coordinateValidationEnabled));
        // Generic Geometry deserializer registered last to avoid Jackson type resolution issues
        addDeserializer(Geometry.class, new GenericGeometryDeserializer(coordinateValidationEnabled));
    }
}

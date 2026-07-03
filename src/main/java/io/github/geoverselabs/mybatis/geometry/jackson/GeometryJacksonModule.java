package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.locationtech.jts.geom.LineString;
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
        // Serializers
        addSerializer(Point.class, new PointSerializer());
        addSerializer(LineString.class, new LineStringSerializer());
        addSerializer(Polygon.class, new PolygonSerializer());
        // Deserializers
        addDeserializer(Point.class, new PointDeserializer(coordinateValidationEnabled));
        addDeserializer(LineString.class, new LineStringDeserializer(coordinateValidationEnabled));
        addDeserializer(Polygon.class, new PolygonDeserializer(coordinateValidationEnabled));
    }
}

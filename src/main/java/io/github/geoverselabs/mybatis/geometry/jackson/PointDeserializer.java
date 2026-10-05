package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON Point to JTS Point.
 *
 * <p>Expected input format ({@code "coordinates": []} gives an empty point; an optional third
 * number is kept as the altitude):</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [longitude, latitude]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = PointDeserializer.class)}) use {@link GeoJsonOptions#getGlobal()}
 * at call time. See {@link GeometryJacksonModule} for the parsing and validation rules.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PointSerializer.class)
 * @JsonDeserialize(using = PointDeserializer.class)
 * private Point location;
 * }</pre>
 */
public class PointDeserializer extends GeoJsonGeometryDeserializer<Point> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public PointDeserializer() {
        super(Point.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public PointDeserializer(boolean coordinateValidationEnabled) {
        super(Point.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public PointDeserializer(GeoJsonOptions options) {
        super(Point.class, options, null);
    }

    /**
     * Read a GeoJSON geometry object.
     *
     * @param parser the parser, positioned at the object's start (or a field name inside it)
     * @param ctx the deserialization context (may be null)
     * @return the geometry, or null for JSON null
     * @throws IOException on invalid GeoJSON ({@code GeoJsonParseException},
     *                     {@code InvalidCoordinateException}) or parser failure
     */
    @Override
    public Point deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}

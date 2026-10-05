package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.locationtech.jts.geom.MultiPoint;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON MultiPoint to JTS MultiPoint.
 *
 * <p>Expected input format ({@code "coordinates": []} gives an empty MultiPoint):</p>
 * <pre>{@code
 * {
 *   "type": "MultiPoint",
 *   "coordinates": [[lon1, lat1], [lon2, lat2], ...]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonDeserialize(using = MultiPointDeserializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time. See {@link GeometryJacksonModule} for the
 * parsing and validation rules.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiPointSerializer.class)
 * @JsonDeserialize(using = MultiPointDeserializer.class)
 * private MultiPoint locations;
 * }</pre>
 */
public class MultiPointDeserializer extends GeoJsonGeometryDeserializer<MultiPoint> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiPointDeserializer() {
        super(MultiPoint.class, null, null);
    }

    /**
     * Create a deserializer using the global {@link GeoJsonOptions} at call time, with the
     * coordinate range validation overridden.
     *
     * @param coordinateValidationEnabled when true, validates WGS84 range;
     *                                    when false, only validates Double.isFinite()
     */
    public MultiPointDeserializer(boolean coordinateValidationEnabled) {
        super(MultiPoint.class, null, coordinateValidationEnabled);
    }

    /**
     * Create a deserializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiPointDeserializer(GeoJsonOptions options) {
        super(MultiPoint.class, options, null);
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
    public MultiPoint deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        return super.deserialize(parser, ctx);
    }
}

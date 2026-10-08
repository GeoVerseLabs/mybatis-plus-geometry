package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Jackson serializer for JTS Point to GeoJSON format.
 *
 * <p>Output format (an empty point is written with {@code "coordinates": []}):</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [longitude, latitude]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = PointSerializer.class)}) use {@link GeoJsonOptions#getGlobal()} at
 * call time; {@link #PointSerializer(GeoJsonOptions)} fixes the options. {@link GeoJsonPrecision}
 * on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PointSerializer.class)
 * @JsonDeserialize(using = PointDeserializer.class)
 * private Point location;
 * }</pre>
 */
public class PointSerializer extends GeoJsonGeometrySerializer<Point> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public PointSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public PointSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private PointSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(Point.class, options, precisionOverride);
    }

    @Override
    PointSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new PointSerializer(options, precisionOverride);
    }

    /**
     * Write {@code point} as a GeoJSON object (JSON null for null).
     *
     * @param point the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(Point point, JsonGenerator gen, SerializerProvider provider) throws IOException {
        super.serialize(point, gen, provider);
    }
}

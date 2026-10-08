package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;

/**
 * Jackson serializer for JTS LineString to GeoJSON format.
 *
 * <p>Output format (an empty line is written with {@code "coordinates": []}):</p>
 * <pre>{@code
 * {
 *   "type": "LineString",
 *   "coordinates": [[lon1, lat1], [lon2, lat2], ...]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = LineStringSerializer.class)}) use {@link GeoJsonOptions#getGlobal()}
 * at call time; {@link #LineStringSerializer(GeoJsonOptions)} fixes the options.
 * {@link GeoJsonPrecision} on a property overrides the coordinate precision for that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = LineStringSerializer.class)
 * @JsonDeserialize(using = LineStringDeserializer.class)
 * private LineString route;
 * }</pre>
 */
public class LineStringSerializer extends GeoJsonGeometrySerializer<LineString> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public LineStringSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public LineStringSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private LineStringSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(LineString.class, options, precisionOverride);
    }

    @Override
    LineStringSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new LineStringSerializer(options, precisionOverride);
    }

    /**
     * Write {@code lineString} as a GeoJSON object (JSON null for null).
     *
     * @param lineString the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(LineString lineString, JsonGenerator gen, SerializerProvider provider) throws IOException {
        super.serialize(lineString, gen, provider);
    }
}

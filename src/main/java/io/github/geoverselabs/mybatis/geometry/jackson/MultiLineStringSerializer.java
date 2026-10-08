package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.MultiLineString;

import java.io.IOException;

/**
 * Jackson serializer for JTS MultiLineString to GeoJSON format.
 *
 * <p>Empty member lines are skipped; an empty MultiLineString is written with
 * {@code "coordinates": []}.</p>
 *
 * <p>Output format:</p>
 * <pre>{@code
 * {
 *   "type": "MultiLineString",
 *   "coordinates": [[[lon1, lat1], [lon2, lat2], ...], [[lon3, lat3], [lon4, lat4], ...]]
 * }
 * }</pre>
 *
 * <p>Instances created with the no-arg constructor (for example through
 * {@code @JsonSerialize(using = MultiLineStringSerializer.class)}) use
 * {@link GeoJsonOptions#getGlobal()} at call time; {@link #MultiLineStringSerializer(GeoJsonOptions)}
 * fixes the options. {@link GeoJsonPrecision} on a property overrides the coordinate precision for
 * that property.</p>
 *
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = MultiLineStringSerializer.class)
 * @JsonDeserialize(using = MultiLineStringDeserializer.class)
 * private MultiLineString routes;
 * }</pre>
 */
public class MultiLineStringSerializer extends GeoJsonGeometrySerializer<MultiLineString> {

    private static final long serialVersionUID = 1L;

    /**
     * Create a serializer using the global {@link GeoJsonOptions} at call time.
     */
    public MultiLineStringSerializer() {
        this(null, null);
    }

    /**
     * Create a serializer with fixed options.
     *
     * @param options the options; null to use {@link GeoJsonOptions#getGlobal()} at call time
     */
    public MultiLineStringSerializer(GeoJsonOptions options) {
        this(options, null);
    }

    private MultiLineStringSerializer(GeoJsonOptions options, Integer precisionOverride) {
        super(MultiLineString.class, options, precisionOverride);
    }

    @Override
    MultiLineStringSerializer withSettings(GeoJsonOptions options, Integer precisionOverride) {
        return new MultiLineStringSerializer(options, precisionOverride);
    }

    /**
     * Write {@code multiLineString} as a GeoJSON object (JSON null for null).
     *
     * @param multiLineString the geometry
     * @param gen the generator
     * @param provider the serializer provider
     * @throws IOException on write failure or non-finite ordinates
     */
    @Override
    public void serialize(MultiLineString multiLineString, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        super.serialize(multiLineString, gen, provider);
    }
}
